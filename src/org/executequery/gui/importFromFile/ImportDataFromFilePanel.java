package org.executequery.gui.importFromFile;

import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.commons.io.FilenameUtils;
import org.executequery.GUIUtilities;
import org.executequery.base.DefaultTabViewActionPanel;
import org.executequery.components.FileChooserDialog;
import org.executequery.databasemediators.DatabaseConnection;
import org.executequery.databasemediators.QueryTypes;
import org.executequery.databasemediators.spi.DefaultStatementExecutor;
import org.executequery.databaseobjects.DatabaseColumn;
import org.executequery.databaseobjects.impl.DefaultDatabaseHost;
import org.executequery.datasource.ConnectionManager;
import org.executequery.gui.NamedView;
import org.executequery.gui.WidgetFactory;
import org.executequery.gui.editor.ResultSetTablePopupMenu;
import org.executequery.gui.resultset.ResultSetTable;
import org.executequery.gui.resultset.ResultSetTableModel;
import org.executequery.localization.Bundles;
import org.executequery.repository.DatabaseConnectionRepository;
import org.executequery.repository.RepositoryCache;
import org.underworldlabs.swing.DefaultProgressDialog;
import org.underworldlabs.swing.FlatSplitPane;
import org.underworldlabs.swing.layouts.GridBagHelper;
import org.underworldlabs.swing.table.TableSorter;
import org.underworldlabs.swing.util.SwingWorker;
import org.underworldlabs.util.MiscUtils;
import org.underworldlabs.util.SystemProperties;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Vector;

/**
 * @author Alexey Kozlov
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ImportDataFromFilePanel extends DefaultTabViewActionPanel
        implements NamedView {

    public static final String TITLE = Bundles.get(ImportDataFromFilePanel.class, "Title");
    public static final String FRAME_ICON = "ImportDelimited16.png";

    private static final int PREVIEW_ROWS_COUNT = 50;
    private static final int MIN_COLUMN_WIDTH = 100;
    private static final String NOTHING_HEADER = "NOTHING";

    // --- GUI components ---

    private JPanel importFilePanel;
    private JPanel importConnectionPanel;

    private JComboBox targetConnectionsCombo;
    private JComboBox targetTableCombo;
    private JComboBox sourceConnectionsCombo;
    private JComboBox sourceTableCombo;
    private JComboBox delimiterCombo;

    private JLabel propertyLabel;

    private JTextField fileNameField;

    private JCheckBox firstRowIsNamesCheck;
    private JCheckBox eraseTableCheck;
    private JCheckBox importFromConnectionCheck;

    private JSpinner sheetNumberSpinner;
    private JSpinner firstImportedRowSelector;
    private JSpinner lastImportedRowSelector;
    private JSpinner commitStepSelector;

    private DefaultTableModel filePreviewTableModel;
    private JTable filePreviewTable;

    private ResultSetTableModel connectionPreviewTableModel;
    private ResultSetTable connectionPreviewTable;

    private DefaultTableModel columnMappingTableModel;
    private JTable columnMappingTable;

    private JButton browseButton;
    private JButton readFileButton;
    private JButton refreshMappingTableButton;
    private JButton startImportButton;

    private DefaultProgressDialog progressDialog;

    // ---

    private DefaultDatabaseHost targetHost;
    private ImportHelper importHelper;
    private List<String> sourceHeaders;
    private String pathToFile;
    private String fileName;
    private String fileType;

    public ImportDataFromFilePanel() {
        super(new BorderLayout());
        init();
    }

    private void init() {

        // --- comboBoxes ---

        String[] delimiters = {";", "|", ",", "#"};
        List<DatabaseConnection> connections = ((DatabaseConnectionRepository) Objects.requireNonNull(RepositoryCache.load(DatabaseConnectionRepository.REPOSITORY_ID))).findAll();

        delimiterCombo = WidgetFactory.createComboBox("delimiterCombo", delimiters);
        delimiterCombo.addActionListener(e -> previewSourceFile(false));
        delimiterCombo.setEditable(true);
        delimiterCombo.setVisible(false);

        targetTableCombo = WidgetFactory.createComboBox("targetTableCombo");
        targetTableCombo.addActionListener(e -> updateMappingTable());
        targetTableCombo.setEnabled(false);

        targetConnectionsCombo = WidgetFactory.createComboBox("targetConnectionsCombo");
        targetConnectionsCombo.addActionListener(e -> targetConnectionChanged());
        targetConnectionsCombo.addItem(bundleString("SelectDB"));
        connections.forEach(dc -> targetConnectionsCombo.addItem(dc));

        sourceTableCombo = WidgetFactory.createComboBox("sourceTableCombo");
        sourceTableCombo.addActionListener(e -> previewSourceTable());
        sourceTableCombo.setEnabled(false);

        sourceConnectionsCombo = WidgetFactory.createComboBox("sourceConnectionsCombo");
        sourceConnectionsCombo.addActionListener(e -> sourceConnectionChanged());
        sourceConnectionsCombo.addItem(bundleString("SelectDB"));
        connections.forEach(dc -> sourceConnectionsCombo.addItem(dc));

        // --- checkBoxes ---

        firstRowIsNamesCheck = WidgetFactory.createCheckBox("firstRowIsNamesCheck", bundleString("IsFirstColumnNamesText"));
        firstRowIsNamesCheck.addActionListener(e -> previewSourceFile(false));
        firstRowIsNamesCheck.setVisible(false);

        importFromConnectionCheck = WidgetFactory.createCheckBox("importFromConnectionCheck", bundleString("importFromConnectionCheck"));
        importFromConnectionCheck.addActionListener(e -> updateSourcePropertiesFields());

        eraseTableCheck = WidgetFactory.createCheckBox("eraseTableCheck", bundleString("IsEraseDatabaseText"));

        // --- numeric selectors ---

        firstImportedRowSelector = WidgetFactory.createSpinner("firstImportedRowSelector", 0, 0, Integer.MAX_VALUE, 1);
        lastImportedRowSelector = WidgetFactory.createSpinner("lastImportedRowSelector", 999999999, 0, Integer.MAX_VALUE, 1);
        commitStepSelector = WidgetFactory.createSpinner("commitStepSelector", 100, 100, 1000000, 100);

        sheetNumberSpinner = WidgetFactory.createSpinner("sheetNumberSpinner", 1, 1, 1, 1);
        sheetNumberSpinner.addChangeListener(e -> previewSourceFile(false));
        sheetNumberSpinner.setVisible(false);

        // --- file preview table ---

        filePreviewTableModel = new DefaultTableModel();
        filePreviewTable = WidgetFactory.createTable("filePreviewTable", filePreviewTableModel);

        // --- connection preview table ---

        try {
            connectionPreviewTableModel = new ResultSetTableModel(PREVIEW_ROWS_COUNT, true);

            connectionPreviewTable = new ResultSetTable();
            connectionPreviewTable.setModel(new TableSorter(connectionPreviewTableModel));
            connectionPreviewTable.addMouseListener(new ResultSetTablePopupMenu(connectionPreviewTable, null));
        } catch (SQLException ignored) {
        }

        // --- column mapping table ---

        columnMappingTableModel = new DefaultTableModel();
        columnMappingTableModel.addColumn(bundleString("ColumnMappingTableTargetC"));
        columnMappingTableModel.addColumn(bundleString("ColumnMappingTableTargetT"));
        columnMappingTableModel.addColumn(bundleString("ColumnMappingTableSource"));
        columnMappingTableModel.addColumn(bundleString("ColumnMappingTableProps"));

        columnMappingTable = WidgetFactory.createTable("columnMappingTable", columnMappingTableModel);
        columnMappingTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        columnMappingTable.getColumn(bundleString("ColumnMappingTableProps")).setCellRenderer(new MappingCellRenderer());
        columnMappingTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

                int selectedRow = columnMappingTable.getSelectedRow();
                int selectedColumn = columnMappingTable.getSelectedColumn();

                if (selectedColumn == 3) {
                    if (isBlobType(columnMappingTable.getValueAt(selectedRow, 1).toString())) {
                        String oldValue = columnMappingTable.getValueAt(selectedRow, selectedColumn).toString();
                        columnMappingTable.setValueAt(!oldValue.equals("true"), selectedRow, selectedColumn);
                    }
                }

                columnMappingTable.repaint();
            }
        });

        // --- buttons ---

        browseButton = WidgetFactory.createButton("browseButton", bundleString("BrowseButtonText"));
        browseButton.addActionListener(e -> browseFile());

        readFileButton = WidgetFactory.createButton("readFileButton", bundleString("ReadFileButtonText"));
        readFileButton.addActionListener(e -> previewSourceFile(true));

        refreshMappingTableButton = WidgetFactory.createButton("refreshMappingTableButton", bundleString("RefreshButtonText"));
        refreshMappingTableButton.addActionListener(e -> updateMappingTable());

        startImportButton = WidgetFactory.createButton("startImportButton", bundleString("StartImportButtonText"));
        startImportButton.addActionListener(e -> importData());

        // --- other ---

        importFilePanel = new JPanel(new GridBagLayout());

        importConnectionPanel = new JPanel(new GridBagLayout());
        importConnectionPanel.setVisible(false);

        fileNameField = WidgetFactory.createTextField("fileNameField");

        propertyLabel = new JLabel();
        propertyLabel.setVisible(false);

        // ---

        arrange();
    }

    private void arrange() {

        GridBagHelper gridBagHelper = new GridBagHelper().setInsets(5, 5, 5, 5).anchorNorthWest();

        // --- scroll panes ---

        JScrollPane filePreviewScrollPane = new JScrollPane(filePreviewTable,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JScrollPane connectionPreviewScrollPane = new JScrollPane(connectionPreviewTable,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JScrollPane mappingTableScrollPane = new JScrollPane(columnMappingTable,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // --- connection preview panel ---

        gridBagHelper.fillHorizontally().addLabelFieldPair(importConnectionPanel,
                bundleString("sourceConnectionLabel"), sourceConnectionsCombo, null, true, false, 2);
        gridBagHelper.addLabelFieldPair(importConnectionPanel,
                bundleString("sourceTableLabel"), sourceTableCombo, null, false, false, 1);
        importConnectionPanel.add(connectionPreviewScrollPane, gridBagHelper.nextRowFirstCol().fillBoth().spanX().spanY().get());

        // --- file preview panel ---

        importFilePanel.add(fileNameField, gridBagHelper.nextRowFirstCol().fillHorizontally().setWidth(4).setMaxWeightX().get());
        importFilePanel.add(browseButton, gridBagHelper.nextCol().setLabelDefault().get());
        importFilePanel.add(readFileButton, gridBagHelper.nextCol().get());

        importFilePanel.add(filePreviewScrollPane, gridBagHelper.nextRowFirstCol().fillBoth().setMaxWeightY().spanX().get());
        importFilePanel.add(firstRowIsNamesCheck, gridBagHelper.nextRowFirstCol().setMinWeightY().setWidth(2).get());
        importFilePanel.add(propertyLabel, gridBagHelper.nextCol().get());
        importFilePanel.add(delimiterCombo, gridBagHelper.nextCol().get());
        importFilePanel.add(sheetNumberSpinner, gridBagHelper.get());

        // --- mapping panel ---

        JPanel mappingPanel = new JPanel(new GridBagLayout());
        mappingPanel.setBorder(BorderFactory.createTitledBorder(bundleString("MappingTableLabel")));

        gridBagHelper.fillHorizontally().addLabelFieldPair(mappingPanel,
                bundleString("TargetConnectionLabel"), targetConnectionsCombo, null, true, false);
        gridBagHelper.addLabelFieldPair(mappingPanel,
                bundleString("TargetTableLabel"), targetTableCombo, null, false, false);
        mappingPanel.add(refreshMappingTableButton, gridBagHelper.nextCol().spanX().get());
        mappingPanel.add(mappingTableScrollPane, gridBagHelper.nextRowFirstCol().setMaxWeightY().fillBoth().spanX().get());
        mappingPanel.add(eraseTableCheck, gridBagHelper.nextRowFirstCol().fillHorizontally().setMinWeightY().setWidth(2).get());
        mappingPanel.add(importFromConnectionCheck, gridBagHelper.nextCol().spanX().get());

        // --- preview panel ---

        JPanel previewPanel = new JPanel(new GridBagLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder(bundleString("PreviewTableLabel")));

        previewPanel.add(importFilePanel, gridBagHelper.fillBoth().spanX().spanY().get());
        previewPanel.add(importConnectionPanel, gridBagHelper.fillBoth().spanX().spanY().get());

        // --- split pane ---

        FlatSplitPane splitPane = new FlatSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewPanel, mappingPanel);
        splitPane.setResizeWeight(0.5);

        // --- bottom panel ---

        JPanel bottomPanel = new JPanel(new GridBagLayout());

        gridBagHelper.addLabelFieldPair(bottomPanel,
                bundleString("FirstNumericSelectorsLabel"), firstImportedRowSelector, null, true, false);
        gridBagHelper.addLabelFieldPair(bottomPanel,
                bundleString("LastNumericSelectorsLabel"), lastImportedRowSelector, null, false, false);
        gridBagHelper.addLabelFieldPair(bottomPanel,
                bundleString("CommitSelectorLabel"), commitStepSelector, null, false, true);
        bottomPanel.add(startImportButton, gridBagHelper.nextRow().anchorSouthEast().setLabelDefault().get());

        // --- panels settings ---

        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }

    // --- buttons handlers ---

    private void browseFile() {

        FileChooserDialog fileChooser = new FileChooserDialog();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new FileNameExtensionFilter("Data Files", "csv", "xml", "xlsx"));
        fileChooser.setMultiSelectionEnabled(false);

        fileChooser.setDialogTitle(bundleString("OpenFileDialogText"));
        fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);

        int result = fileChooser.showDialog(GUIUtilities.getInFocusDialogOrWindow(), bundleString("OpenFileDialogButton"));
        if (result == JFileChooser.CANCEL_OPTION)
            return;

        File file = fileChooser.getSelectedFile();
        if (!file.exists()) {
            GUIUtilities.displayWarningMessage(bundleString("FileDoesNotExistMessage") + "\n" + fileNameField.getText());
            return;
        }

        fileNameField.setText(file.getAbsolutePath());
        previewSourceFile(true);
    }

    private synchronized void previewSourceFile(boolean displayWarnings) {

        if (fileNameField.getText().isEmpty()) {
            if (displayWarnings)
                GUIUtilities.displayWarningMessage(bundleString("NoFileSelectedMessage"));
            return;
        }

        File sourceFile = new File(fileNameField.getText());
        String oldPathToFile = pathToFile;
        pathToFile = sourceFile.getAbsolutePath();
        fileName = FilenameUtils.getBaseName(sourceFile.getName());
        fileType = FileNameUtils.getExtension(sourceFile.getName());

        if (!fileType.equalsIgnoreCase("csv")
                && !fileType.equalsIgnoreCase("xlsx")
                && !fileType.equalsIgnoreCase("xml")
        ) {
            if (displayWarnings)
                GUIUtilities.displayWarningMessage(bundleString("FileTypeNotSupported"));
            fileNameField.setText(oldPathToFile != null ? oldPathToFile : "");
            return;
        }

        importHelper = getImportHelper(fileType);
        if (importHelper == null)
            return;

        try {
            List<String> readData = importHelper.getPreviewData();

            filePreviewTableModel.setColumnCount(0);
            filePreviewTableModel.setRowCount(0);

            sourceHeaders = new LinkedList<>(importHelper.getHeaders());
            for (String header : sourceHeaders) {
                filePreviewTableModel.addColumn(header);
                filePreviewTable.getColumn(header).setMinWidth(MIN_COLUMN_WIDTH);
            }

            filePreviewTable.setAutoResizeMode(importHelper.getColumnsCount() < 7 ?
                    JTable.AUTO_RESIZE_ALL_COLUMNS :
                    JTable.AUTO_RESIZE_OFF
            );

            for (int i = 0; i < PREVIEW_ROWS_COUNT && i < readData.size(); i++)
                if (readData.get(i) != null)
                    filePreviewTableModel.addRow(readData.get(i).split(importHelper.getDelimiter()));

        } catch (FileNotFoundException e) {
            if (displayWarnings)
                GUIUtilities.displayWarningMessage(bundleString("FileDoesNotExistMessage") + "\n" + pathToFile);

        } catch (Exception e) {
            if (displayWarnings)
                GUIUtilities.displayWarningMessage(bundleString("FileReadingErrorMessage") + "\n" + e.getMessage());
        }

        firstRowIsNamesCheck.setVisible(fileType.equalsIgnoreCase("csv") || fileType.equalsIgnoreCase("xlsx"));
        propertyLabel.setVisible(fileType.equalsIgnoreCase("csv") || fileType.equalsIgnoreCase("xlsx"));
        delimiterCombo.setVisible(fileType.equalsIgnoreCase("csv"));
        sheetNumberSpinner.setVisible(fileType.equalsIgnoreCase("xlsx"));

        propertyLabel.setText(fileType.equalsIgnoreCase("csv") ?
                bundleString("DelimiterLabel") :
                bundleString("SheetNumberLabel")
        );

        updateMappingTable();
    }

    private synchronized void previewSourceTable() {

        fileType = "db";
        fileName = (String) sourceTableCombo.getSelectedItem();
        if (fileName == null || fileName.isEmpty())
            return;

        importHelper = getImportHelper(fileType);
        if (importHelper == null)
            return;

        try {

            ResultSet resultSet = ((ImportHelperDB) importHelper).getPreviewResultSet();
            connectionPreviewTableModel.createTable(resultSet);

            connectionPreviewTable.setAutoResizeMode(importHelper.getColumnsCount() < 7 ?
                    JTable.AUTO_RESIZE_ALL_COLUMNS :
                    JTable.AUTO_RESIZE_OFF
            );

        } catch (SQLException e) {
            GUIUtilities.displayWarningMessage(bundleString("FileReadingErrorMessage") + "\n" + e.getMessage());
        }

        updateMappingTable();
    }

    private synchronized void updateMappingTable() {

        columnMappingTableModel.setRowCount(0);

        if (targetNotSelected(false) || importHelper == null)
            return;

        JComboBox sourceComboBox = new JComboBox();
        sourceComboBox.addItem(NOTHING_HEADER);
        importHelper.getHeaders().forEach(sourceComboBox::addItem);

        TableColumn thirdColumn = columnMappingTable.getColumnModel().getColumn(2);
        thirdColumn.setCellEditor(new DefaultCellEditor(sourceComboBox));

        if (targetTableCombo.getSelectedItem() != null) {
            for (DatabaseColumn column : targetHost.getColumns(targetTableCombo.getSelectedItem().toString())) {

                String fieldProperty = "";
                if (isTimeType(column.getTypeName())) {
                    switch (column.getTypeName()) {

                        case "DATE":
                            fieldProperty = SystemProperties.getProperty("user", "results.date.pattern");
                            if (fieldProperty == null || fieldProperty.isEmpty())
                                fieldProperty = SystemProperties.getProperty("system", "results.date.pattern");
                            break;

                        case "TIME":
                            fieldProperty = SystemProperties.getProperty("user", "results.time.pattern");
                            if (fieldProperty == null || fieldProperty.isEmpty())
                                fieldProperty = SystemProperties.getProperty("system", "results.time.pattern");
                            break;

                        case "TIMESTAMP":
                            fieldProperty = SystemProperties.getProperty("user", "results.timestamp.pattern");
                            if (fieldProperty == null || fieldProperty.isEmpty())
                                fieldProperty = SystemProperties.getProperty("system", "results.timestamp.pattern");
                            break;
                    }
                }

                if (isBlobType(column.getTypeName()))
                    fieldProperty = "true";

                columnMappingTableModel.addRow(new Object[]{
                        column.getName(),
                        column.getTypeName(),
                        NOTHING_HEADER,
                        fieldProperty
                });
            }
        }
    }

    // --- import data methods ---

    private void importData() {

        if (targetNotSelected(true))
            return;

        DefaultStatementExecutor executor = new DefaultStatementExecutor();
        executor.setCommitMode(false);
        executor.setKeepAlive(true);
        executor.setDatabaseConnection((DatabaseConnection) targetConnectionsCombo.getSelectedItem());

        // -- generate  mapped columns lists

        StringBuilder targetColumnList = new StringBuilder();
        StringBuilder sourceColumnList = new StringBuilder();

        int valuesCount = 0;
        Vector<Vector> dataFromTableVector = columnMappingTableModel.getDataVector();
        boolean[] valuesIndexes = new boolean[dataFromTableVector.size()];

        for (int i = 0; i < dataFromTableVector.size(); i++) {

            if (dataFromTableVector.get(i).get(2) != NOTHING_HEADER && dataFromTableVector.get(i).get(2) != null) {

                targetColumnList.append(MiscUtils.getFormattedObject(dataFromTableVector.get(i).get(0).toString(), executor.getDatabaseConnection()));
                sourceColumnList.append(dataFromTableVector.get(i).get(2).toString());

                targetColumnList.append(",");
                sourceColumnList.append(",");

                valuesCount++;
                valuesIndexes[i] = true;

            } else
                valuesIndexes[i] = false;
        }

        if (valuesCount == 0) {
            GUIUtilities.displayWarningMessage(bundleString("NoDataForImportMessage"));
            return;
        }

        targetColumnList.deleteCharAt(targetColumnList.length() - 1);
        sourceColumnList.deleteCharAt(sourceColumnList.length() - 1);

        // -- generate insert query

        StringBuilder insertQuery = new StringBuilder()
                .append("INSERT INTO ")
                .append(MiscUtils.getFormattedObject(Objects.requireNonNull(targetTableCombo.getSelectedItem()).toString(), executor.getDatabaseConnection()))
                .append(" (")
                .append(targetColumnList)
                .append(") VALUES (");

        while (valuesCount > 1) {
            insertQuery.append("?,");
            valuesCount--;
        }
        insertQuery.append("?);");

        // -- generate insert statement

        PreparedStatement insertStatement;
        try {
            insertStatement = executor.getPreparedStatement(insertQuery.toString());
            insertStatement.setEscapeProcessing(true);

        } catch (Exception e) {
            GUIUtilities.displayExceptionErrorDialog(bundleString("ImportDataErrorMessage") + "\n" + e.getMessage(), e);
            return;
        }

        // -- start import

        progressDialog = new DefaultProgressDialog(bundleString("ExecutingProgressDialog"));

        SwingWorker worker = new SwingWorker("ImportCSV") {
            @Override
            public Object construct() {

                if (eraseTableCheck.isSelected())
                    eraseTable(Objects.requireNonNull(targetTableCombo.getSelectedItem()).toString());
                getImportHelper(fileType).importData(sourceColumnList, valuesIndexes, insertStatement, executor);

                return null;
            }

            @Override
            public void finished() {
                if (progressDialog != null)
                    progressDialog.dispose();
            }
        };

        worker.start();
        progressDialog.run();
    }

    // --- helper methods ---

    private ImportHelper getImportHelper(String fileType) {

        ImportHelper importHelper = null;
        switch (fileType) {

            case ("csv"):
                importHelper = new ImportHelperCSV(
                        this,
                        pathToFile,
                        PREVIEW_ROWS_COUNT,
                        firstRowIsNamesCheck.isSelected()
                );
                break;

            case ("xlsx"):
                importHelper = new ImportHelperXLSX(
                        this,
                        pathToFile,
                        PREVIEW_ROWS_COUNT,
                        firstRowIsNamesCheck.isSelected()
                );
                break;

            case ("xml"):
                importHelper = new ImportHelperXML(
                        this,
                        pathToFile,
                        PREVIEW_ROWS_COUNT,
                        firstRowIsNamesCheck.isSelected()
                );
                break;

            case ("db"):
                importHelper = new ImportHelperDB(
                        this,
                        fileName,
                        PREVIEW_ROWS_COUNT,
                        (DatabaseConnection) sourceConnectionsCombo.getSelectedItem()
                );
                break;
        }

        return importHelper;
    }

    private void eraseTable(String tableName) {

        try {

            DefaultStatementExecutor executor = new DefaultStatementExecutor();
            executor.setCommitMode(false);
            executor.setKeepAlive(true);
            executor.setDatabaseConnection((DatabaseConnection) targetConnectionsCombo.getSelectedItem());

            String query = "DELETE FROM " + tableName + ";";
            executor.execute(QueryTypes.DELETE, query);
            executor.getConnection().commit();
            executor.releaseResources();

        } catch (Exception e) {
            GUIUtilities.displayExceptionErrorDialog(bundleString("EraseDatabaseErrorMessage") + "\n" + e.getMessage(), e);
        }
    }

    private void targetConnectionChanged() {

        targetTableCombo.removeAllItems();

        if (columnMappingTable != null)
            columnMappingTableModel.setRowCount(0);

        if (targetConnectionsCombo.getSelectedIndex() == 0) {
            targetTableCombo.setEnabled(false);
            return;
        }

        targetHost = new DefaultDatabaseHost((DatabaseConnection) targetConnectionsCombo.getSelectedItem());
        if (!targetHost.isConnected())
            ConnectionManager.createDataSource(targetHost.getDatabaseConnection());

        targetTableCombo.setEnabled(true);
        targetTableCombo.addItem(bundleString("SelectTable"));
        targetHost.getTables().stream()
                .filter(table -> !table.isSystem())
                .forEach(table -> targetTableCombo.addItem(table.getName()));

        targetHost.close();
    }

    private void sourceConnectionChanged() {

        sourceTableCombo.removeAllItems();

        if (connectionPreviewTableModel != null)
            for (int i = 0; i < connectionPreviewTableModel.getRowCount(); i++)
                connectionPreviewTableModel.deleteRow(i);

        if (sourceConnectionsCombo.getSelectedIndex() == 0) {
            sourceTableCombo.setEnabled(false);
            return;
        }

        DefaultDatabaseHost host = new DefaultDatabaseHost((DatabaseConnection) sourceConnectionsCombo.getSelectedItem());
        if (!host.isConnected())
            ConnectionManager.createDataSource(host.getDatabaseConnection());

        sourceTableCombo.setEnabled(true);
        sourceTableCombo.addItem(bundleString("SelectTable"));
        host.getTables().stream()
                .filter(table -> !table.isSystem())
                .forEach(table -> sourceTableCombo.addItem(table.getName()));

        host.close();
    }

    private void updateSourcePropertiesFields() {

        importConnectionPanel.setVisible(importFromConnectionCheck.isSelected());
        importFilePanel.setVisible(!importFromConnectionCheck.isSelected());

        if (importFromConnectionCheck.isSelected())
            previewSourceTable();
        else
            previewSourceFile(false);
    }

    private boolean targetNotSelected(boolean displayWarnings) {

        if (targetConnectionsCombo.getSelectedIndex() == 0) {
            if (displayWarnings)
                GUIUtilities.displayWarningMessage(bundleString("SelectDBMessage"));
            return true;
        }

        if (targetTableCombo.getSelectedIndex() == 0) {
            if (displayWarnings)
                GUIUtilities.displayWarningMessage(bundleString("SelectTableMessage"));
            return true;
        }

        return false;
    }

    public boolean isIntegerType(String type) {
        return type.toUpperCase().contains("INT");
    }

    public boolean isTimeType(String type) {
        return Objects.equals(type, "DATE") ||
                Objects.equals(type, "TIME") ||
                Objects.equals(type, "TIMESTAMP");
    }

    public boolean isBlobType(String type) {
        return type.toUpperCase().contains("BLOB");
    }

    // --- getters ---

    public String getDelimiter() {
        return (String) delimiterCombo.getSelectedItem();
    }

    public int getFirstRowIndex() {
        return (int) firstImportedRowSelector.getValue();
    }

    public int getLastRowIndex() {
        return (int) lastImportedRowSelector.getValue();
    }

    public int getBathStep() {
        return (int) commitStepSelector.getValue();
    }

    public int getSheetNumber() {
        return (int) sheetNumberSpinner.getValue();
    }

    public int getSourceColumnIndex(String header) {

        for (int i = 0; i < sourceHeaders.size(); i++)
            if (sourceHeaders.get(i).equals(header))
                return i;

        return -1;
    }

    public JTable getMappingTable() {
        return columnMappingTable;
    }

    public JSpinner getSheetNumberSpinner() {
        return sheetNumberSpinner;
    }

    public DefaultProgressDialog getProgressDialog() {
        return progressDialog;
    }

    public String getFileName() {
        return fileName;
    }

    // ---

    @Override
    public String getDisplayName() {
        return TITLE;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String bundleString(String key) {
        return Bundles.get(ImportDataFromFilePanel.class, key);
    }

    private class MappingCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            if (isBlobType(columnMappingTableModel.getValueAt(row, 1).toString())) {

                JCheckBox loadAsFileCheck = new JCheckBox(bundleString("InsertAsFile"));
                loadAsFileCheck.setSelected(value.toString().equals("true"));

                return loadAsFileCheck;
            }

            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }

    } // MappingCellRenderer class

}
