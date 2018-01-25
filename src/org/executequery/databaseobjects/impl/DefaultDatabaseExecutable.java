/*
 * DefaultDatabaseExecutable.java
 *
 * Copyright (C) 2002-2017 Takis Diakoumis
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.executequery.databaseobjects.impl;

import biz.redsoft.IFBDatabaseMetadata;
import org.executequery.databaseobjects.*;
import org.executequery.datasource.PooledDatabaseMetaData;
import org.executequery.gui.browser.ColumnData;
import org.underworldlabs.jdbc.DataSourceException;
import org.underworldlabs.util.MiscUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author takisd
 */
public class DefaultDatabaseExecutable extends AbstractDatabaseObject
        implements DatabaseExecutable {

    /**
     * the meta tag parent object
     */
    private DatabaseMetaTag metaTagParent;

    /**
     * proc parameters
     */
    private ArrayList<ProcedureParameter> parameters;

    /**
     * the proc type
     */
    private short executableType;

    private String procedureSourceCode;

    public DefaultDatabaseExecutable() {
    }

    public DefaultDatabaseExecutable(DatabaseMetaTag metaTagParent, String name) {
        this.metaTagParent = metaTagParent;
        setName(name);

        if (metaTagParent.getCatalog() != null) {
            setCatalogName(metaTagParent.getCatalog().getName());
        }

        if (metaTagParent.getSchema() != null) {
            setSchemaName(metaTagParent.getSchema().getName());
        }

    }

    /**
     * Indicates whether this executable object has any parameters.
     *
     * @return true | false
     */
    public boolean hasParameters() {
        List<ProcedureParameter> _parameters = getParameters();
        return _parameters != null && !_parameters.isEmpty();
    }

    /**
     * Adds the specified values as a single parameter to this object.
     */
    public ProcedureParameter addParameter(String name, int type, int dataType,
                                           String sqlType, int size, int nullable) {
        if (parameters == null) {

            parameters = new ArrayList<ProcedureParameter>();
        }

        ProcedureParameter parameter = new ProcedureParameter(name, type, dataType, sqlType, size, nullable);
        parameters.add(parameter);

        return parameter;
    }

    /**
     * Returns this object's parameters as an array.
     */
    public ProcedureParameter[] getParametersArray() throws DataSourceException {
        if (parameters == null) {
            getParameters();
        }
        return (ProcedureParameter[]) parameters.toArray(new
                ProcedureParameter[parameters.size()]);
    }

    public boolean supportCatalogOrSchemaInFunctionOrProcedureCalls() throws DataSourceException {

        try {

            DatabaseMetaData dmd = getMetaTagParent().getHost().getDatabaseMetaData();
            return dmd.supportsCatalogsInProcedureCalls() || dmd.supportsSchemasInProcedureCalls();

        } catch (SQLException e) {

            throw new DataSourceException(e);
        }
    }

    public boolean supportCatalogInFunctionOrProcedureCalls() throws DataSourceException {

        try {

            DatabaseMetaData dmd = getMetaTagParent().getHost().getDatabaseMetaData();
            return dmd.supportsCatalogsInProcedureCalls();

        } catch (SQLException e) {

            throw new DataSourceException(e);
        }
    }

    public boolean supportSchemaInFunctionOrProcedureCalls() throws DataSourceException {

        try {

            DatabaseMetaData dmd = getMetaTagParent().getHost().getDatabaseMetaData();
            return dmd.supportsSchemasInProcedureCalls();

        } catch (SQLException e) {

            throw new DataSourceException(e);
        }
    }

    /**
     * Returns this object's parameters.
     */
    public List<ProcedureParameter> getParameters() throws DataSourceException {

        if (!isMarkedForReload() && parameters != null) {

            return parameters;
        }

        ResultSet rs = null;
        try {

            DatabaseMetaData dmd = getMetaTagParent().getHost().getDatabaseMetaData();
            parameters = new ArrayList<ProcedureParameter>();

            String _catalog = getCatalogName();
            String _schema = getSchemaName();

            int type = getType();
            if (type == SYSTEM_FUNCTION ||
                    type == SYSTEM_STRING_FUNCTIONS ||
                    type == SYSTEM_NUMERIC_FUNCTIONS ||
                    type == SYSTEM_DATE_TIME_FUNCTIONS) {

                _catalog = null;
                _schema = null;

            } else {

                // check that the db supports catalog and 
                // schema names for this call
                if (!dmd.supportsCatalogsInProcedureCalls()) {
                    _catalog = null;
                }

                if (!dmd.supportsSchemasInProcedureCalls()) {
                    _schema = null;
                }

            }

            rs = getProcedureParameters(getName());

            while (rs.next()) {

                ProcedureParameter pp = new ProcedureParameter(rs.getString(4).trim(),
                        rs.getInt(5) == 0 ? DatabaseMetaData.procedureColumnIn : DatabaseMetaData.procedureColumnOut,
                        DatabaseTypeConverter.getSqlTypeFromRDBType(rs.getInt(7), rs.getInt(10)),
                        DatabaseTypeConverter.getDataTypeName(rs.getInt(7), rs.getInt(10), rs.getInt(9)),
                        rs.getInt(8),
                        rs.getInt("null_flag"));

                if (pp.getDataType() == Types.LONGVARBINARY ||
                        pp.getDataType() == Types.LONGVARCHAR ||
                        pp.getDataType() == Types.BLOB) {
                    pp.setSubtype(rs.getInt(10));
                    pp.setSize(rs.getInt("segment_length"));
                }

                String domain = rs.getString(6);
                if (domain != null && !domain.startsWith("RDB$"))
                    pp.setDomain(domain.trim());
                pp.setTypeOf(rs.getInt("AM") == 1);
                String relationName = rs.getString("RN");
                if (relationName != null)
                    pp.setRelationName(relationName.trim());
                String fieldName = rs.getString("FN");
                if (fieldName != null)
                    pp.setFieldName(fieldName.trim());

                if (pp.getRelationName() != null && pp.getFieldName() != null)
                    pp.setTypeOfFrom(ColumnData.TYPE_OF_FROM_COLUMN);
                String characterSet = rs.getString("character_set_name");
                if (characterSet != null && !characterSet.isEmpty() && !characterSet.contains("NONE"))
                    pp.setEncoding(characterSet.trim());

                parameters.add(pp);
            }

            return parameters;

        } catch (SQLException e) {

            throw new DataSourceException(e);

        } finally {

            releaseResources(rs);
            setMarkedForReload(false);
        }
    }

    /**
     * Returns the procedure parameters.
     *
     * @return the result set
     */
    private ResultSet getProcedureParameters(String name) throws SQLException {

        Connection connection = this.getMetaTagParent().getHost().getConnection();
        Statement statement = connection.createStatement();

        String sql = "select prc.rdb$procedure_name,\n" +
                "prc.rdb$procedure_source,\n" +
                "prc.rdb$description, \n" +
                "pp.rdb$parameter_name,\n" +
                "pp.rdb$parameter_type,\n" +
                "fs.rdb$field_name, \n" +
                "fs.rdb$field_type, \n" +
                "fs.rdb$field_length, \n" +
                "fs.rdb$field_scale, \n" +
                "fs.rdb$field_sub_type, \n" +
                "fs.rdb$segment_length as segment_length, \n" +
                "fs.rdb$dimensions, \n" +
                "cr.rdb$character_set_name as character_set_name, \n" +
                "co.rdb$collation_name, \n" +
                "pp.rdb$parameter_number,\n" +
                "fs.rdb$character_length, \n" +
                "pp.rdb$description,\n" +
                "pp.rdb$default_source,\n" +
                "fs.rdb$field_precision, \n" +
                "pp.rdb$parameter_mechanism as AM,\n" +
                "pp.rdb$field_source as FS,\n" +
                "fs.rdb$default_source, \n" +
                "pp.rdb$null_flag as null_flag,\n" +
                "pp.rdb$relation_name as RN,\n" +
                "pp.rdb$field_name as FN,\n" +
                "co2.rdb$collation_name, \n" +
                "cr.rdb$default_collate_name, \n" +
                "prc.rdb$engine_name, \n" +
                "prc.rdb$entrypoint \n" +
                "from rdb$procedures prc\n" +
                "left join rdb$procedure_parameters pp on pp.rdb$procedure_name = prc.rdb$procedure_name\n" +
                "and (pp.rdb$package_name is null)\n" +
                "left join rdb$fields fs on fs.rdb$field_name = pp.rdb$field_source\n" +
                "left join rdb$character_sets cr on fs.rdb$character_set_id = cr.rdb$character_set_id \n" +
                "left join rdb$collations co on ((fs.rdb$collation_id = co.rdb$collation_id) and (fs.rdb$character_set_id = co.rdb$character_set_id)) \n" +
                "left join rdb$collations co2 on ((pp.rdb$collation_id = co2.rdb$collation_id) and (fs.rdb$character_set_id = co2.rdb$character_set_id))\n" +
                "where prc.rdb$procedure_name = '" + name + "'\n" +
                "and (prc.rdb$package_name is null) \n" +
                "order by pp.rdb$parameter_number";

        return statement.executeQuery(sql);
    }

    /**
     * Returns the database object type.
     *
     * @return the object type
     */
    public int getType() {
        return PROCEDURE;
    }

    /**
     * Returns the meta data key name of this object.
     *
     * @return the meta data key name.
     */
    public String getMetaDataKey() {
        return META_TYPES[PROCEDURE];
    }

    /**
     * Returns the parent meta tag object.
     *
     * @return the parent meta tag
     */
    public DatabaseMetaTag getMetaTagParent() {
        return metaTagParent;
    }

    /**
     * Returns the parent named object of this object.
     *
     * @return the parent object - the meta tag
     */
    public NamedObject getParent() {
        return super.getParent();
    }

    /**
     * The executable (procedure) type:<br>
     * <ul>
     * <li> procedureResultUnknown - May return a result
     * <li> procedureNoResult - Does not return a result
     * <li> procedureReturnsResult - Returns a result
     * </ul>
     *
     * @return the proc type
     */
    public short getExecutableType() {
        return executableType;
    }

    public void setExecutableType(short executableType) {
        this.executableType = executableType;
    }

    public String getProcedureSourceCode() {
        if (!isMarkedForReload() && procedureSourceCode != null) {

            return procedureSourceCode;
        }

        procedureSourceCode = "";

        try {

            DatabaseMetaData dmd = getMetaTagParent().getHost().getDatabaseMetaData();
            PooledDatabaseMetaData poolMetaData = (PooledDatabaseMetaData)dmd;
            DatabaseMetaData dMetaData = poolMetaData.getInner();
            if (this.getHost() != null && this.getHost().getDatabaseConnection().getJDBCDriver().getClassName().contains("FBDriver")) {

                URL[] urls = new URL[0];
                Class clazzdb = null;
                Object odb = null;
                try {
                    urls = MiscUtils.loadURLs("./lib/fbplugin-impl.jar");
                    ClassLoader cl = new URLClassLoader(urls, dMetaData.getClass().getClassLoader());
                    clazzdb = cl.loadClass("biz.redsoft.FBDatabaseMetadataImpl");
                    odb = clazzdb.newInstance();
                    IFBDatabaseMetadata db = (IFBDatabaseMetadata) odb;

                    procedureSourceCode = db.getProcedureSourceCode(dMetaData, getName());

                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }

            return procedureSourceCode;

        } finally {

            setMarkedForReload(false);
        }
    }

}






