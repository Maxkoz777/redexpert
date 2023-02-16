package org.underworldlabs.util;

import org.executequery.databasemediators.DatabaseConnection;
import org.executequery.databaseobjects.FunctionArgument;
import org.executequery.databaseobjects.NamedObject;
import org.executequery.databaseobjects.Parameter;
import org.executequery.databaseobjects.ProcedureParameter;
import org.executequery.databaseobjects.impl.DefaultDatabaseJob;
import org.executequery.databaseobjects.impl.DefaultDatabaseUser;
import org.executequery.gui.browser.ColumnConstraint;
import org.executequery.gui.browser.ColumnData;
import org.executequery.gui.browser.ConnectionsTreePanel;
import org.executequery.gui.table.CreateTableSQLSyntax;
import org.executequery.gui.table.TableDefinitionPanel;
import org.underworldlabs.jdbc.DataSourceException;

import java.sql.DatabaseMetaData;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.executequery.databaseobjects.NamedObject.*;
import static org.executequery.gui.browser.ColumnConstraint.RESTRICT;
import static org.executequery.gui.browser.ColumnConstraint.RULES;
import static org.executequery.gui.table.CreateTableSQLSyntax.*;

public final class SQLUtils {

    public static String generateCreateTable (
            String name, List<ColumnData> columnDataList, List<ColumnConstraint> columnConstraintList,
            boolean existTable, boolean temporary, String typeTemporary, String externalFile,
            String adapter, String sqlSecurity, String tablespace, String comment) {

        StringBuilder sb = new StringBuilder();
        StringBuilder sqlText = new StringBuilder();

        sb.append(temporary ?
                CreateTableSQLSyntax.CREATE_GLOBAL_TEMPORARY_TABLE :
                CreateTableSQLSyntax.CREATE_TABLE);
        sb.append(format(name));

        if (externalFile != null)
            sb.append(NEW_LINE).append("EXTERNAL FILE '").append(externalFile.trim()).append("'");
        if (adapter != null)
            sb.append(SPACE).append(" ADAPTER '").append(adapter.trim()).append("'");
        sb.append(SPACE).append(B_OPEN);

        StringBuilder primary = new StringBuilder();
        primary.append(",\nCONSTRAINT ");
        primary.append(MiscUtils.getFormattedObject("PK_" + name));
        primary.append(" PRIMARY KEY (");

        boolean primary_flag = false;
        String autoincrementSQLText = "";
        StringBuilder primaryText = new StringBuilder();

        for (int i = 0, k = columnDataList.size(); i < k; i++) {

            ColumnData cd = columnDataList.get(i);
            autoincrementSQLText += cd.getAutoincrement().getSqlAutoincrement();

            if (cd.isPrimaryKey()) {
                primaryText.append(primary_flag ? ", " : " ");
                primaryText.append(cd.getFormattedColumnName());
                primary_flag = true;
            }

            sqlText.append(generateDefinitionColumn(cd, true));
            if (i != k - 1)
                sqlText.append(COMMA);
        }
        primary.append(primaryText).append(B_CLOSE);
        sb.append(sqlText.toString().replaceAll(TableDefinitionPanel.SUBSTITUTE_NAME, format(name)));

        if (primary_flag && !existTable)
            sb.append(primary);
        columnConstraintList = removeDuplicatesConstraints(columnConstraintList);

        for (ColumnConstraint columnConstraint : columnConstraintList)
            sb.append(generateDefinitionColumnConstraint(columnConstraint, existTable)
                    .replaceAll(TableDefinitionPanel.SUBSTITUTE_NAME, format(name)));

        sb.append(CreateTableSQLSyntax.B_CLOSE);

        if (tablespace != null)
            sb.append("\nTABLESPACE ").append(format(tablespace));
        if (!MiscUtils.isNull(sqlSecurity))
            sb.append("\n" + SQL_SECURITY).append(sqlSecurity);
        if (temporary)
            sb.append("\n").append(typeTemporary);

        sb.append(CreateTableSQLSyntax.SEMI_COLON);
        sb.append("\n").append(generateCommentForColumns(name, columnDataList, "COLUMN", "^"));
        sb.append(autoincrementSQLText.replace(TableDefinitionPanel.SUBSTITUTE_NAME, format(name))).append(NEW_LINE);

        if (comment != null)
            sb.append("COMMENT ON TABLE ").append(name).append(" IS '").append(comment).append("';\n");

        return sb.toString();
    }

    public static String generateDefinitionColumn(ColumnData cd, boolean startWithNewLine) {

        StringBuilder sb = new StringBuilder();

        if (startWithNewLine)
            sb.append(NEW_LINE_2);
        sb.append(cd.getColumnName() == null ? CreateTableSQLSyntax.EMPTY : cd.getFormattedColumnName()).append(SPACE);

        if (MiscUtils.isNull(cd.getComputedBy())) {

            if (MiscUtils.isNull(cd.getDomain()) || cd.getDomain().startsWith("RDB$"))
                sb.append(cd.getFormattedDataType());
            else
                sb.append(cd.getFormattedDomain());

            if (cd.isAutoincrement() && cd.getAutoincrement().isIdentity()) {
                sb.append(" GENERATED BY DEFAULT AS IDENTITY");
                if (cd.getAutoincrement().getStartValue() != 0)
                    sb.append(" (START WITH ").append(cd.getAutoincrement().getStartValue()).append(")");
            }

            if (!MiscUtils.isNull(cd.getDefaultValue().getValue()))
                sb.append(MiscUtils.formattedDefaultValue(cd.getDefaultValue(), cd.getSQLType()));

            sb.append(cd.isRequired() ? NOT_NULL : CreateTableSQLSyntax.EMPTY);
            if (!MiscUtils.isNull(cd.getCheck()))
                sb.append(" CHECK ( ").append(cd.getCheck()).append(")");

            if (!MiscUtils.isNull(cd.getCollate()) && !cd.getCollate().equals(CreateTableSQLSyntax.NONE))
                sb.append(" COLLATE ").append(cd.getCollate());

        } else
            sb.append("COMPUTED BY ( ").append(cd.getComputedBy()).append(")");

        return sb.toString();
    }

    public static String generateDefinitionColumnConstraint(ColumnConstraint cc, boolean editing) {

        StringBuilder sb = new StringBuilder();
        String nameConstraint = null;
        boolean hasName = false;

        if (!MiscUtils.isNull(cc.getName())) {
            nameConstraint = cc.getName();
            hasName = true;
        }

        if (hasName) {
            sb.append(COMMA).append(NEW_LINE_2).append(CreateTableSQLSyntax.CONSTRAINT);
            sb.append(format(nameConstraint)).append(SPACE);

            if (cc.getType() != -1) {

                if (cc.getType() == CHECK_KEY) {
                    sb.append(cc.getCheck());

                } else {

                    String formatted = (cc.getCountCols() > 1) ? cc.getColumn() : format(cc.getColumn());
                    if (editing) {
                        List<String> columnList = Arrays.asList(cc.getColumnDisplayList().split(", "));
                        columnList.replaceAll(SQLUtils::format);
                        formatted = String.join(", ", columnList);
                    }

                    if (cc.getType() == UNIQUE_KEY) {
                        sb.append(ColumnConstraint.UNIQUE).append(SPACE).append(B_OPEN);
                        sb.append(formatted).append(B_CLOSE);

                    } else {
                        sb.append(cc.getTypeName()).append(KEY).append(B_OPEN);
                        sb.append(formatted).append(B_CLOSE);

                        if (cc.getType() == FOREIGN_KEY) {
                            sb.append(REFERENCES).append(format(cc.getRefTable()));
                            sb.append(SPACE).append(B_OPEN);

                            formatted = (cc.getCountCols() > 1) ? cc.getRefColumn() : format(cc.getRefColumn());
                            if (editing) {
                                List<String> columnList = Arrays.asList(cc.getRefColumnDisplayList().split(", "));
                                columnList.replaceAll(SQLUtils::format);
                                formatted = String.join(", ", columnList);
                            }

                            sb.append(formatted).append(B_CLOSE);

                            if (cc.getUpdateRule() != null && !Objects.equals(cc.getUpdateRule(), RULES[RESTRICT]))
                                sb.append(" ON UPDATE ").append(cc.getUpdateRule());
                            if (cc.getDeleteRule() != null && !Objects.equals(cc.getDeleteRule(), RULES[RESTRICT]))
                                sb.append(" ON DELETE ").append(cc.getDeleteRule());
                        }
                    }
                    if (!MiscUtils.isNull(cc.getTablespace()))
                        sb.append(" TABLESPACE ").append(format(cc.getTablespace()));
                }
            }
        }

        return sb.toString();
    }

    public static String generateCreateProcedure(String name, String entryPoint, String engine, Vector<ColumnData> inputParameters, Vector<ColumnData> outputParameters, Vector<ColumnData> variables, String sqlSecurity, String authid, String procedureBody, String comment) {
        StringBuilder sb = new StringBuilder();
        sb.append(formattedParameters(variables, true));
        sb.append(procedureBody);
        return generateCreateProcedure(name, entryPoint, engine, inputParameters, outputParameters, sqlSecurity, authid, sb.toString(), comment);
    }

    public static String generateCreateProcedure(String name, String entryPoint, String engine, List<ProcedureParameter> parameters, String sqlSecurity, String authid, String fullProcedureBody, String comment, DatabaseConnection dc) {
        Vector<ColumnData> inputs = new Vector<>();
        Vector<ColumnData> outputs = new Vector<>();
        for (ProcedureParameter parameter : parameters) {
            ColumnData cd = columnDataFromProcedureParameter(parameter, dc, false);
            if (parameter.getType() == DatabaseMetaData.procedureColumnIn)
                inputs.add(cd);
            else
                outputs.add(cd);
        }
        return generateCreateProcedure(name, entryPoint, engine, inputs, outputs, sqlSecurity, authid, fullProcedureBody, comment);
    }

    public static String generateCreateProcedure(String name, String entryPoint, String engine, Vector<ColumnData> inputParameters, Vector<ColumnData> outputParameters, String sqlSecurity, String authid, String fullProcedureBody, String comment) {
        StringBuilder sb = new StringBuilder();
        sb.append(generateCreateProcedureOrFunctionHeader(name, inputParameters, NamedObject.META_TYPES[PROCEDURE], authid));
        String output = formattedParameters(outputParameters, false);
        if (!MiscUtils.isNull(output.trim())) {
            sb.append("\nRETURNS (\n");
            sb.append(output);
            sb.append(")");
        }
        if (!MiscUtils.isNull(sqlSecurity))
            sb.append("\n" + SQL_SECURITY).append(sqlSecurity);
        if (!MiscUtils.isNull(entryPoint)) {
            sb.append("\nEXTERNAL NAME '");
            sb.append(entryPoint).append("'");
            sb.append(" ENGINE ").append(engine);
        } else sb.append(generateSQLBody(fullProcedureBody));
        sb.append("\n");

        // add procedure description
        sb.append(generateComment(name, NamedObject.META_TYPES[PROCEDURE], comment, "^", false));

        sb.append(generateCommentForColumns(name, inputParameters, "PARAMETER", "^"));

        sb.append(generateCommentForColumns(name, outputParameters, "PARAMETER", "^"));

        return sb.toString();
    }

    public static String generateCommentForColumns(String relationName, List<ColumnData> cols, String metaTag, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (ColumnData cd : cols) {
            String name = format(relationName) + "." + cd.getFormattedColumnName();
            sb.append(generateComment(name, metaTag, cd.getDescription(), delimiter, true));
        }
        return sb.toString();
    }

    public static String generateComment(String name, String metaTag, String comment, String delimiter, boolean nameAlreadyFormatted) {
        StringBuilder sb = new StringBuilder();
        if (comment != null && !comment.isEmpty()) {
            sb.append("\n");
            sb.append("COMMENT ON ").append(metaTag).append(" ");
            if (nameAlreadyFormatted)
                sb.append(name);
            else
                sb.append(format(name));
            sb.append(" IS ");
            if (!comment.equals("NULL"))
                sb.append("'").append(comment).append("'");
            else
                sb.append("NULL");
            sb.append(delimiter);
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String generateCreateProcedureOrFunctionHeader(String name, Vector<ColumnData> inputParameters, String metaTag, String authid) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE OR ALTER ").append(metaTag).append(" ");
        sb.append(format(name));
        if (!MiscUtils.isNull(authid)) {
            sb.append("\nAUTHID ").append(authid).append("\n");
        }
        if (inputParameters != null && inputParameters.size() > 0 && (inputParameters.size() == 1 && !MiscUtils.isNull(inputParameters.get(0).getColumnName()) || inputParameters.size() > 1)) {
            sb.append(" (\n");
            sb.append(formattedParameters(inputParameters, false));
            sb.append(")");
        }
        return sb.toString();

    }

    public static String generateSQLBody(String sqlBody) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nAS\n").append(sqlBody).append("^");
        return sb.toString();
    }

    public static String generateCreateFunction(String name, Vector<ColumnData> argumentList, Vector<ColumnData> variables, ColumnData returnType, String functionBody, String entryPoint, String engine, String sqlSecurity, String comment, boolean deterministic) {
        StringBuilder sb = new StringBuilder();
        sb.append(formattedParameters(variables, true));
        sb.append(functionBody);
        return generateCreateFunction(name, argumentList, returnType, sb.toString(), entryPoint, engine, sqlSecurity, comment, deterministic);
    }

    public static String generateCreateFunction(String name, List<FunctionArgument> argumentList, String fullFunctionBody, String entryPoint, String engine, String sqlSecurity, String comment, boolean deterministic, DatabaseConnection dc) {
        Vector<ColumnData> inputs = new Vector<>();
        ColumnData returnType = null;
        for (FunctionArgument parameter : argumentList) {
            if (parameter.getType() == DatabaseMetaData.procedureColumnIn) {
                ColumnData cd = columnDataFromProcedureParameter(parameter, dc, false);
                inputs.add(cd);
            } else
                returnType = columnDataFromProcedureParameter(parameter, dc, false);
        }
        return generateCreateFunction(name, inputs, returnType, fullFunctionBody, entryPoint, engine, sqlSecurity, comment, deterministic);
    }

    public static String generateCreateFunction(String name, Vector<ColumnData> inputArguments, ColumnData returnType, String fullFunctionBody, String entryPoint, String engine, String sqlSecurity, String comment, boolean deterministic) {
        StringBuilder sb = new StringBuilder();
        sb.append(generateCreateProcedureOrFunctionHeader(name, inputArguments, NamedObject.META_TYPES[FUNCTION], null));
        sb.append("\nRETURNS ");
        if (returnType != null)
            sb.append(returnType.getFormattedDataType());
        if (deterministic) {
            sb.append(" DETERMINISTIC");
        }
        if (!MiscUtils.isNull(sqlSecurity))
            sb.append("\n" + SQL_SECURITY).append(sqlSecurity);

        if (!MiscUtils.isNull(entryPoint)) {
            sb.append("\nEXTERNAL NAME '");
            sb.append(entryPoint).append("'");
            sb.append(" ENGINE ").append(engine);
        } else sb.append(generateSQLBody(fullFunctionBody));
        sb.append("\n");
        sb.append(generateComment(name, NamedObject.META_TYPES[FUNCTION], comment, "^", false));

        sb.append(generateCommentForColumns(name, inputArguments, "PARAMETER", "^"));
        return sb.toString();
    }

    public static String formattedParameters(Vector<ColumnData> tableVector, boolean variable) {
        StringBuilder sqlText = new StringBuilder();
        for (int i = 0, k = tableVector.size(); i < k; i++) {
            ColumnData cd = tableVector.elementAt(i);
            if (!MiscUtils.isNull(cd.getColumnName())) {
                if (variable)
                    sqlText.append("DECLARE ");
                if (cd.isCursor()) {
                    sqlText.append(cd.getColumnName()).append(" CURSOR FOR ");
                    if (cd.isScroll())
                        sqlText.append("SCROLL ");
                    sqlText.append("(").append(cd.getSelectOperator()).append(")");
                } else {
                    if (!variable)
                        sqlText.append("\t");
                    sqlText.append(formattedParameter(cd));
                }
                if (variable) {
                    sqlText.append(";");
                    if (cd.getDescription() != null && !cd.getDescription().isEmpty()) {
                        if (cd.isDescriptionAsSingleComment()) {
                            sqlText.append(" --");
                            sqlText.append(cd.getDescription());
                        } else {
                            sqlText.append(" /*");
                            sqlText.append(cd.getDescription());
                            sqlText.append("*/");
                        }
                    }
                } else if (i != k - 1) {
                    sqlText.append(",");
                }
                sqlText.append("\n");
            }
        }
        return sqlText.toString();
    }

    public static String formattedParameter(ColumnData cd) {
        StringBuilder sb = new StringBuilder();
        sb.append(cd.getColumnName() == null ? CreateTableSQLSyntax.EMPTY : cd.getColumnName()).
                append(" ");
        if (MiscUtils.isNull(cd.getComputedBy())) {
            if (MiscUtils.isNull(cd.getDomain())) {
                if (cd.getColumnType() != null || cd.isTypeOf()) {
                    sb.append(cd.getFormattedDataType());
                }
            } else {
                if (cd.isTypeOf())
                    sb.append(cd.getFormattedDataType());
                else
                    sb.append(cd.getFormattedDomain());
            }
            sb.append(cd.isRequired() ? " NOT NULL" : CreateTableSQLSyntax.EMPTY);
            if (cd.getTypeParameter() != ColumnData.OUTPUT_PARAMETER && !MiscUtils.isNull(cd.getDefaultValue().getValue())) {
                sb.append(MiscUtils.formattedDefaultValue(cd.getDefaultValue(), cd.getSQLType()));
            }
            if (!MiscUtils.isNull(cd.getCheck())) {
                sb.append(" CHECK ( ").append(cd.getCheck()).append(")");
            }
        } else {
            sb.append("COMPUTED BY ( ").append(cd.getComputedBy()).append(")");
        }
        return sb.toString();
    }

    public static ColumnData columnDataFromProcedureParameter(Parameter parameter, DatabaseConnection dc, boolean loadDomainInfo) {
        ColumnData cd = new ColumnData(true, dc);
        cd.setColumnName(parameter.getName());
        cd.setDomain(parameter.getDomain(), loadDomainInfo);
        cd.setColumnSubtype(parameter.getSubType());
        cd.setSQLType(parameter.getDataType());
        cd.setColumnSize(parameter.getSize());
        cd.setColumnType(parameter.getSqlType());
        cd.setColumnScale(parameter.getScale());
        cd.setNotNull(parameter.getNullable() == 0);
        cd.setCharset(parameter.getEncoding());
        cd.setDescription(parameter.getDescription());
        cd.setTypeOf(parameter.isTypeOf());
        cd.setTypeOfFrom(parameter.getTypeOfFrom());
        cd.setTable(parameter.getRelationName());
        cd.setColumnTable(parameter.getFieldName());
        cd.setDefaultValue(parameter.getDefaultValue(), true);
        cd.setDescriptionAsSingleComment(parameter.isDescriptionAsSingleComment());
        String[] dataTypes = dc.getDataTypesArray();
        int[] intDataTypes = dc.getIntDataTypesArray();
        for (int i = 0; i < dataTypes.length; i++) {
            if (dataTypes[i].equalsIgnoreCase(parameter.getSqlType()))
                cd.setSQLType(intDataTypes[i]);
        }
        return cd;
    }

    public static String generateNameForDBObject(String type, DatabaseConnection databaseConnection) {
        String name = "NEW_" + type + "_";
        int int_number = 0;
        String number;
        List<NamedObject> keys = ConnectionsTreePanel.getPanelFromBrowser().getDefaultDatabaseHostFromConnection(databaseConnection).getDatabaseObjectsForMetaTag(type);
        if (keys != null)
            for (NamedObject key : keys) {
                if (!MiscUtils.isNull(key.getName()))
                    if (key.getName().contains(name)) {
                        number = key.getName().replace(name, "");
                        try {
                            if (Integer.parseInt(number) > int_number)
                                int_number = Integer.parseInt(number);
                        } catch (NumberFormatException e) {
                            Log.debug(e.getMessage());
                        }
                    }

            }
        number = "" + (int_number + 1);
        return name + number;
    }

    private static String generateNameForConstraint(String type, List<ColumnConstraint> keys) {
        String name = "_<TABLE_NAME>";
        switch (type) {
            case org.executequery.databaseobjects.impl.ColumnConstraint.PRIMARY:
                name = "PK" + name;
                break;
            case org.executequery.databaseobjects.impl.ColumnConstraint.FOREIGN:
                name = "FK" + name;
                break;
            case org.executequery.databaseobjects.impl.ColumnConstraint.CHECK:
                name = "CHECK" + name;
                break;
            case org.executequery.databaseobjects.impl.ColumnConstraint.UNIQUE:
                name = "UQ" + name;
                break;
        }
        name = name + "_";
        int int_number = 0;
        String number;
        if (keys != null)
            for (ColumnConstraint key : keys) {
                if (!MiscUtils.isNull(key.getName()))
                    if (key.getName().contains(name)) {
                        number = key.getName().replace(name, "");
                        try {
                            if (Integer.parseInt(number) > int_number)
                                int_number = Integer.parseInt(number);
                        } catch (NumberFormatException e) {
                            Log.debug(e.getMessage());
                        }
                    }

            }
        number = "" + (int_number + 1);
        return name + number;
    }

    public static List<ColumnConstraint> removeDuplicatesConstraints(List<ColumnConstraint> columnConstraintList) {
        List<String> cc_names = new ArrayList<>();
        List<ColumnConstraint> columnConstraints = new ArrayList<>();
        for (int i = 0, n = columnConstraintList.size(); i < n; i++) {
            if (MiscUtils.isNull(columnConstraintList.get(i).getName()) && !MiscUtils.isNull(columnConstraintList.get(i).getTypeName())) {
                String colType = columnConstraintList.get(i).getTypeName();
                columnConstraintList.get(i).setName(generateNameForConstraint(colType, columnConstraintList));
                columnConstraintList.get(i).setGeneratedName(true);
            }
            int ind = cc_names.indexOf(columnConstraintList.get(i).getName());
            if (ind >= 0) {
                ColumnConstraint cc_rep = columnConstraintList.get(i);
                ColumnConstraint cc_origin = columnConstraints.get(ind);
                if (cc_rep.getColumn() != null) {
                    String cols = cc_origin.getColumn();
                    if (cc_origin.getCountCols() == 1)
                        cols = format(cols);
                    cols += "," + format(cc_rep.getColumn());
                    cc_origin.setColumn(cols);
                }
                if (cc_rep.getRefColumn() != null) {
                    String cols = cc_origin.getRefColumn();
                    if (cc_origin.getCountCols() == 1)
                        cols = format(cols);
                    cols += "," + format(cc_rep.getRefColumn());
                    cc_origin.setRefColumn(cols);
                }
                cc_origin.setCountCols(cc_origin.getCountCols() + 1);

            } else {
                cc_names.add(columnConstraintList.get(i).getName());
                columnConstraints.add(columnConstraintList.get(i));
            }
        }
        return columnConstraints;
    }

    public static String generateCreateDomain(ColumnData columnData, String name, boolean useDomainType) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE DOMAIN ").append(name).append(" AS ");
        if (useDomainType)
            sb.append(columnData.getFormattedDomainDataType());
        else sb.append(columnData.getFormattedDataType());
        sb.append("\n");
        if (!MiscUtils.isNull(columnData.getDefaultValue().getValue())) {
            sb.append(MiscUtils.formattedDefaultValue(columnData.getDefaultValue(), columnData.getSQLType()));
        }
        sb.append(columnData.isRequired() ? " NOT NULL" : "");
        if (!MiscUtils.isNull(columnData.getCheck())) {
            sb.append(" CHECK (").append(columnData.getCheck()).append(")");
        }
        if (columnData.getCollate() != null && !columnData.getCollate().trim().contentEquals("NONE"))
            sb.append(" COLLATE ").append(columnData.getCollate());
        sb.append(";");
        if (!MiscUtils.isNull(columnData.getDescription())) {
            sb.append("\nCOMMENT ON DOMAIN ").append(columnData.getFormattedColumnName()).append(" IS '")
                    .append(columnData.getDescription()).append("';");
        }
        return sb.toString();
    }

    public static String generateAlterDomain(ColumnData thisDomainData, ColumnData domainData) throws DataSourceException {

        StringBuilder sb = new StringBuilder();
        sb.append("ALTER DOMAIN ").append(thisDomainData.getFormattedColumnName()).append("\n");
        String noChangesCheckString = sb.toString();

        if (!thisDomainData.getColumnName().contentEquals(domainData.getColumnName()))
            sb.append("TO ").append(domainData.getFormattedColumnName()).append("\n");

        if (thisDomainData.getDefaultValue() != domainData.getDefaultValue()) {

            if (MiscUtils.isNull(domainData.getDefaultValue().getValue()))
                sb.append("DROP DEFAULT\n");

            else {

                sb.append("SET DEFAULT ");
                if (domainData.getDefaultValue().getValue().toUpperCase().trim().equals("NULL"))
                    sb.append("NULL");
                else
                    sb.append(MiscUtils.formattedSQLValue(domainData.getDefaultValue(), domainData.getSQLType()));
                sb.append("\n");
            }
        }

        if (thisDomainData.isDomainNotNull() != domainData.isRequired()) {

            if (domainData.isRequired())
                sb.append("SET ");
            else
                sb.append("DROP ");

            sb.append("NOT NULL\n");

        }
        if (!Objects.equals(thisDomainData.getCheck(), domainData.getCheck())) {

            sb.append("DROP CONSTRAINT\n");
            if (!MiscUtils.isNull(domainData.getCheck()))
                sb.append("ADD CHECK (").append(domainData.getCheck()).append(")\n");

        }

        if (!Objects.equals(thisDomainData.getDomainTypeName(), domainData.getDomainTypeName()))
            sb.append("TYPE ").append(domainData.getDomainTypeName());

        if (noChangesCheckString.equals(sb.toString()))
            sb = new StringBuilder();
        else
            sb.append(";\n");

        if (!Objects.equals(thisDomainData.getDescription(), domainData.getDescription())) {
            sb.append("COMMENT ON DOMAIN ").append(thisDomainData.getFormattedColumnName()).append(" IS ");
            if (!Objects.equals(domainData.getDescription(), "") && domainData.getDescription() != null)
                sb.append("'").append(domainData.getDescription()).append("'");
            else
                sb.append("NULL");
        }

        return !sb.toString().equals("") ? sb.toString() : "/* there are no changes */";
    }

    public static String generateAlterDomain(ColumnData columnData, String domainName) {

        StringBuilder sb = new StringBuilder();
        sb.append("ALTER DOMAIN ").append(format(domainName)).append("\n");
        String noChangesCheckString = sb.toString();

        if (columnData.isNameChanged())
            sb.append("TO ").append(columnData.getFormattedColumnName()).append("\n");

        if (columnData.isDefaultChanged())
            if (!MiscUtils.isNull(columnData.getDefaultValue().getValue()))
                sb.append("SET DEFAULT ").append(MiscUtils.formattedSQLValue(
                        columnData.getDefaultValue(), columnData.getSQLType())).append("\n");
            else
                sb.append("DROP DEFAULT\n");

        if (columnData.isRequiredChanged())
            sb.append(columnData.isRequired() ? "SET" : "DROP").append(" NOT NULL\n");

        if (columnData.isCheckChanged()) {
            sb.append("DROP CONSTRAINT\n");
            if (!MiscUtils.isNull(columnData.getCheck()))
                sb.append("ADD CHECK (").append(columnData.getCheck()).append(")\n");
        }

        if (columnData.isTypeChanged())
            sb.append("TYPE ").append(columnData.getFormattedDataType());

        if (noChangesCheckString.equals(sb.toString()))
            sb = new StringBuilder();
        else
            sb.append(";\n");

        if (columnData.isDescriptionChanged()) {
            sb.append("COMMENT ON DOMAIN ").append(columnData.getFormattedColumnName()).append(" IS ");
            if (columnData.getDescription() != null)
                sb.append("'").append(columnData.getDescription()).append("'");
            else
                sb.append("NULL");
        }

        return !sb.toString().equals("") ? sb.toString() : "/* there are no changes */";
    }

    public static String generateCreateUser(DefaultDatabaseUser user) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE");
        sb.append(" USER ").append(format(user.getName()));
        if (!MiscUtils.isNull(user.getFirstName()))
            sb.append("\nFIRSTNAME '").append(user.getFirstName()).append("'");
        if (!MiscUtils.isNull(user.getMiddleName()))
            sb.append("\nMIDDLENAME '").append(user.getMiddleName()).append("'");
        if (!MiscUtils.isNull(user.getLastName()))
            sb.append("\nLASTNAME '").append(user.getLastName()).append("'");
        if (!MiscUtils.isNull(user.getPassword())) {
            sb.append("\nPASSWORD '").append(user.getPassword()).append("'");
        }
        if (user.getActive()) {
            sb.append("\nACTIVE");
        } else {
            sb.append("\nINACTIVE");
        }
        if (user.getAdministrator()) {
            sb.append("\nGRANT ADMIN ROLE");
        }
        if (!user.getPlugin().equals(""))
            sb.append("\nUSING PLUGIN ").append(user.getPlugin());
        Map<String, String> tags = user.getTags();
        if (tags.size() > 0) {
            sb.append("\nTAGS (");
            boolean first = true;
            for (String tag : tags.keySet()) {
                if (!first)
                    sb.append(", ");
                first = false;
                sb.append(tag).append(" = '").append(tags.get(tag)).append("'");
            }
            sb.append(" )");
        }
        sb.append(";\n");
        if (!MiscUtils.isNull(user.getComment()))
            sb.append("COMMENT ON USER ").append(format(user.getName())).append(" is '").append(user.getComment()).append("'");
        return sb.toString();
    }

    public static String generateAlterUser(DefaultDatabaseUser thisUser, DefaultDatabaseUser compareUser) {

        StringBuilder sb = new StringBuilder();
        sb.append("ALTER USER ").append(format(thisUser.getName()));
        String noChangesCheckString = sb.toString();

        if (!Objects.equals(thisUser.getFirstName(), compareUser.getFirstName()))
            sb.append("\n\tFIRSTNAME '").append(compareUser.getFirstName()).append("'");

        if (!Objects.equals(thisUser.getMiddleName(), compareUser.getMiddleName()))
            sb.append("\n\tMIDDLENAME '").append(compareUser.getMiddleName()).append("'");

        if (!Objects.equals(thisUser.getLastName(), compareUser.getLastName()))
            sb.append("\n\tLASTNAME '").append(compareUser.getLastName()).append("'");

        if (!Objects.equals(thisUser.getPassword(), compareUser.getPassword()))
            if (!Objects.equals(compareUser.getPassword(), ""))
                sb.append("\n\tPASSWORD '").append(compareUser.getPassword()).append("'");

        if (thisUser.getActive() != compareUser.getActive())
            sb.append(compareUser.getActive() ? "\n\tACTIVE" : "\n\tINACTIVE");

        if (thisUser.getAdministrator() != compareUser.getAdministrator())
            sb.append(compareUser.getAdministrator() ? "\n\tGRANT ADMIN ROLE" : "\n\tREVOKE ADMIN ROLE");

        if (!Objects.equals(thisUser.getPlugin(), compareUser.getPlugin()))
            if (!Objects.equals(compareUser.getPlugin(), "") && compareUser.getPlugin() != null)
                sb.append("\nUSING PLUGIN ").append(compareUser.getPlugin());

        Map<String, String> thisTags = thisUser.getTags();
        Map<String, String> compareTags = compareUser.getTags();

        if (!thisTags.equals(compareTags)) {
            sb.append("\n\tTAGS (");

            for (String tag : thisTags.keySet())
                if (!compareTags.containsKey(tag))
                    sb.append("DROP ").append(tag).append(", ");

            for (String tag : compareTags.keySet())
                sb.append(tag).append(" = '").append(compareTags.get(tag)).append("', ");

            sb.deleteCharAt(sb.lastIndexOf(",")).append(" )");
        }

        if (noChangesCheckString.equals(sb.toString()))
            sb = new StringBuilder();
        else
            sb.append(";\n");

        if (!Objects.equals(thisUser.getComment(), compareUser.getComment())) {
            sb.append("COMMENT ON USER ").append(format(thisUser.getName())).append(" IS ");
            if (!Objects.equals(compareUser.getComment(), "") && compareUser.getComment() != null)
                sb.append("'").append(compareUser.getComment()).append("'");
            else
                sb.append("NULL");
        }

        return !sb.toString().equals("") ? sb.toString() : "/* there are no changes */";
    }

    public static String generateCreateTablespace(String name, String file) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLESPACE ").append(format(name));
        sb.append(" FILE '").append(file).append("';\n");
        return sb.toString();
    }

    public static String generateCreateSequence(
            String name, long startValue, long increment, String description, int databaseVersion, boolean existed) {

        StringBuilder sb = new StringBuilder();

        if (databaseVersion >= 3) {

            sb.append("CREATE OR ALTER SEQUENCE ").append(format(name));
            sb.append(" START WITH ").append(startValue);
            sb.append(" INCREMENT BY ").append(increment);
            sb.append(";\n");

        } else {

            if (!existed)
                sb.append("CREATE SEQUENCE ").append(format(name)).append(";\n");
            sb.append("ALTER SEQUENCE ").append(format(name));
            sb.append(" RESTART WITH ").append(startValue + increment).append(";\n");

        }

        if (description != null && !description.trim().equals(""))
            sb.append(generateComment(name, "SEQUENCE", description.trim(), ";", false));

        return sb.toString();
    }

    public static String generateCreateView(
            String name, String fields, String selectStatement, String description, int databaseVersion, boolean existed) {

        StringBuilder sb = new StringBuilder();

        if (databaseVersion >= 3)
            sb.append("CREATE OR ALTER VIEW ").append(format(name));
        else if (!existed)
            sb.append("CREATE VIEW ").append(format(name));
        else
            sb.append("ALTER VIEW ").append(format(name));

        if (fields != null && !fields.trim().equals(""))
            sb.append(" (").append(fields.trim()).append(") ");
        sb.append("\nAS \n").append(selectStatement.trim()).append(";\n");

        if (description != null && !description.trim().equals(""))
            sb.append(generateComment(name, "VIEW", description.trim(), ";", false));

        return sb.toString();
    }

    public static String generateDefaultUpdateStatement(String name, String settings) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(format(name.trim()));
        sb.append(" SET ").append(settings.trim()).append(";\n");
        return sb.toString();
    }

    public static String generateDefaultInsertStatement(String name, String fields, String values) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(format(name.trim()));
        sb.append(" (").append(fields.trim()).append(")");
        sb.append(" VALUES (").append(values.trim()).append(");\n");
        return sb.toString();
    }

    public static String generateDefaultSelectStatement(String name, String fields) {

        StringBuilder sb = new StringBuilder();

        sb.append("SELECT ").append(fields.trim());
        sb.append(" FROM ").append(format(name.trim())).append(";\n");

        return sb.toString();
    }

    public static String generateCreateTriggerStatement(String name, String tableName, boolean active,
                                                        String triggerType, int position, String sourceCode,
                                                        String engine, String entryPoint, String sqlSecurity, String comment) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE OR ALTER TRIGGER ").append(format(name));
        if (!MiscUtils.isNull(tableName))
            sb.append(" FOR ").append(format(tableName));
        sb.append("\n");
        sb.append(active ? "ACTIVE" : "INACTIVE");
        sb.append(" ").append(triggerType);
        sb.append(" POSITION ").append(position);
        sb.append("\n");
        if (!MiscUtils.isNull(sqlSecurity)) {
            sb.append("\n" + SQL_SECURITY).append(sqlSecurity).append("\n");
        }
        if (!MiscUtils.isNull(entryPoint)) {
            sb.append("EXTERNAL NAME '").append(entryPoint).append("'");
            if (!MiscUtils.isNull(engine)) {
                sb.append("\n").append("ENGINE ").append(engine);
            }
        } else if (!MiscUtils.isNull(sourceCode)) {
            sb.append(sourceCode);
        }
        sb.append("^");
        if (!MiscUtils.isNull(comment)) {
            comment = comment.replace("'", "''");
            sb.append("COMMENT ON TRIGGER ").append(format(name)).append(" IS '").append(comment).append("'^");
        }
        return sb.toString();
    }

    public static String generateCreateCollation(String name, String charset, String baseCollation, String attributes, boolean padSpace, boolean caseSensitive, boolean accentSensitive, boolean isExternal) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE COLLATION ").append(name).append("\n");
        sb.append("FOR ").append(charset);
        if (!MiscUtils.isNull(baseCollation)) {
            sb.append("\nFROM ");
            if (isExternal)
                sb.append("EXTERNAL ('");
            sb.append(baseCollation);
            if (isExternal)
                sb.append("')");
        }
        sb.append("\n");
        if (padSpace)
            sb.append("PAD SPACE");
        else sb.append("NO PAD");
        sb.append("\n");
        if (caseSensitive)
            sb.append("CASE SENSITIVE");
        else sb.append("CASE INSENSITIVE");
        sb.append("\n");
        if (accentSensitive)
            sb.append("ACCENT SENSITIVE");
        else sb.append("ACCENT INSENSITIVE");
        if (!MiscUtils.isNull(attributes))
            sb.append("\n'").append(attributes).append("'");
        sb.append(";");
        return sb.toString();
    }

    public static String generateCreateJob(String name, String cronSchedule, boolean active,
                                           LocalDateTime startDate,LocalDateTime endDate,int jobType,String source) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE JOB ").append(format(name)).append("\n");
        sb.append("'").append(cronSchedule).append("'").append("\n");
        if(active)
            sb.append("ACTIVE");
        else sb.append("INACTIVE");
        sb.append("\n");
        sb.append("START DATE ");
        if(startDate!=null)
            sb.append("'").append(startDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("'");
        else sb.append("NULL");
        sb.append("\n");
        sb.append("END DATE ");
        if(endDate!=null)
            sb.append("'").append(endDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("'");
        else sb.append("NULL");
        sb.append("\n");
        if(jobType== DefaultDatabaseJob.BASH_TYPE)
            sb.append("COMMAND '");
        else sb.append("AS\n");
        sb.append(source);
        if(jobType== DefaultDatabaseJob.BASH_TYPE)
            sb.append("'");
        sb.append("^");
        return sb.toString();
    }

    private static String format(String object) {
        return MiscUtils.getFormattedObject(object);
    }

}

