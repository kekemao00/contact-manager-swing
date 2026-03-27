package com.txl;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContactWindow extends JFrame {
    private static final int DEFAULT_PAGE_SIZE = 12;

    private ContactWindow contactWindow;
    private JPanel contentPanel;
    private final Map<String, Object> allComs = new HashMap<>();
    private final List<String> visibleContactIds = new ArrayList<>();
    private static final Map<String, Map<String, String>> propMap = new HashMap<>();
    public Integer current = 1;
    public Integer pages = 1;
    public Integer size = DEFAULT_PAGE_SIZE;
    public Integer total = 0;
    public Integer mode = 0;

    private TableModelListener tableEditListener;
    private boolean adjustingPageSize;

    public ContactWindow() {
        initWindow();
        initPropMap();
        initActionListeners();
        initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
        this.setVisible(true);
        setLocationRelativeTo(null);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        schedulePageSizeRefresh();
    }

    public ContactWindow(JFrame parentJFrame) {
        initWindow();
        initPropMap();
        initActionListeners();
        initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
        this.setVisible(true);
        setLocationRelativeTo(null);
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                parentJFrame.setVisible(true);
                contactWindow.dispose();
            }
        });
        schedulePageSizeRefresh();
    }

    public ContactWindow(Integer mode) {
        this.mode = mode;
        initWindow();
        initPropMap();
        initActionListeners();
        initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
        this.setVisible(true);
        this.setLocationRelativeTo(null);
        if (mode == 1) {
            ((JButton) allComs.get("del_button")).setVisible(false);
            ((JButton) allComs.get("add_button")).setVisible(false);
            ((JButton) allComs.get("edit_button")).setVisible(false);
            ((JButton) allComs.get("export_button")).setVisible(false);
            ((JButton) allComs.get("import_button")).setVisible(false);
        }
        schedulePageSizeRefresh();
    }

    private void initWindow() {
        this.contactWindow = this;
        this.setResizable(true);
        this.setTitle("通讯录管理");
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        this.setSize(960, 660);
        getContentPane().setBackground(Theme.BG);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Theme.PRIMARY);

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 12));
        headerLeft.setBackground(Theme.PRIMARY);
        headerLeft.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        JLabel iconLabel = new JLabel("📇");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        headerLeft.add(iconLabel);
        headerLeft.add(Box.createHorizontalStrut(8));
        JLabel titleLabel = new JLabel("通讯录管理");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        headerLeft.add(titleLabel);
        headerPanel.add(headerLeft, BorderLayout.WEST);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 12));
        headerRight.setBackground(Theme.PRIMARY);
        headerRight.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));
        JLabel timeLabel = new JLabel();
        timeLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        timeLabel.setForeground(new Color(200, 220, 255));
        Timer timer = new Timer(1000, e -> timeLabel.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())));
        timer.start();
        timer.setInitialDelay(0);
        headerRight.add(timeLabel);
        headerPanel.add(headerRight, BorderLayout.EAST);

        JPanel searchCardPanel = Theme.createCardPanel();
        searchCardPanel.setLayout(new BorderLayout(0, 8));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchRow.setOpaque(false);

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        searchRow.add(searchIcon);

        searchRow.add(Theme.createLabel("全局检索："));
        JTextField keywordTextField = Theme.createTextField("输入姓名/电话/邮箱/分类/公司/岗位等关键词");
        keywordTextField.setPreferredSize(new Dimension(360, 34));
        searchRow.add(keywordTextField);
        allComs.put("keyword_textField", keywordTextField);

        JButton searchButton = Theme.createSearchButton();
        searchButton.setPreferredSize(new Dimension(90, 34));
        searchRow.add(searchButton);
        allComs.put("search_button", searchButton);

        JButton resetButton = Theme.createFlatButton("↺ 重置");
        resetButton.setPreferredSize(new Dimension(80, 34));
        searchRow.add(resetButton);
        allComs.put("reset_button", resetButton);

        searchCardPanel.add(searchRow, BorderLayout.NORTH);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionRow.setOpaque(false);

        JButton addButton = Theme.createPrimaryButton("➕ 新增");
        addButton.setPreferredSize(new Dimension(90, 34));
        actionRow.add(addButton);
        allComs.put("add_button", addButton);

        JButton editButton = Theme.createFlatButton("✏️ 修改");
        editButton.setPreferredSize(new Dimension(90, 34));
        actionRow.add(editButton);
        allComs.put("edit_button", editButton);

        JButton delButton = Theme.createDangerButton("🗑️ 删除");
        delButton.setPreferredSize(new Dimension(90, 34));
        actionRow.add(delButton);
        allComs.put("del_button", delButton);

        JButton exportButton = Theme.createFlatButton("⬇ 导出");
        exportButton.setPreferredSize(new Dimension(96, 34));
        actionRow.add(exportButton);
        allComs.put("export_button", exportButton);

        JButton importButton = Theme.createFlatButton("⬆ 导入");
        importButton.setPreferredSize(new Dimension(96, 34));
        actionRow.add(importButton);
        allComs.put("import_button", importButton);

        JButton selectButton = Theme.createPrimaryButton("确认");
        selectButton.setPreferredSize(new Dimension(90, 34));
        selectButton.setVisible(false);
        actionRow.add(selectButton);
        allComs.put("select_button", selectButton);

        searchCardPanel.add(actionRow, BorderLayout.SOUTH);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(Theme.BG);
        tablePanel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        DefaultTableModel model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 0 && column != 2;
            }
        };
        JTable table = Theme.createTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        allComs.put("model", model);
        allComs.put("table", table);
        model.setColumnIdentifiers(new Object[]{
                "编号", "姓名", "分类", "电话", "邮箱", "地址", "公司", "岗位", "备注"
        });

        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(180);
        table.getColumnModel().getColumn(5).setPreferredWidth(360);
        table.getColumnModel().getColumn(6).setPreferredWidth(180);
        table.getColumnModel().getColumn(7).setPreferredWidth(120);
        table.getColumnModel().getColumn(8).setPreferredWidth(260);

        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col >= 0) {
                    Object value = table.getValueAt(row, col);
                    table.setToolTipText(value == null ? null : value.toString());
                } else {
                    table.setToolTipText(null);
                }
            }
        });

        JPopupMenu tableMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("复制单元格");
        copyItem.setFont(Theme.FONT_DEFAULT);
        copyItem.addActionListener(ev -> {
            int row = table.getSelectedRow();
            int col = table.getSelectedColumn();
            if (row >= 0 && col >= 0) {
                Object val = table.getValueAt(row, col);
                if (val != null) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(val.toString()), null);
                }
            }
        });
        JMenuItem editItem = new JMenuItem("修改此联系人");
        editItem.setFont(Theme.FONT_DEFAULT);
        editItem.addActionListener(ev -> openEditWindow(table));
        JMenuItem delItem = new JMenuItem("删除此联系人");
        delItem.setFont(Theme.FONT_DEFAULT);
        delItem.addActionListener(ev -> deleteSelected(table));
        tableMenu.add(copyItem);
        tableMenu.addSeparator();
        tableMenu.add(editItem);
        tableMenu.add(delItem);
        table.setComponentPopupMenu(tableMenu);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refreshForViewportSizeChange();
            }
        });
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        allComs.put("table_scrollPane", scrollPane);

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(Theme.CARD_BG);
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));

        JPanel leftFooter = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftFooter.setBackground(Theme.CARD_BG);
        JLabel sumLabel = new JLabel("共 0 条记录");
        sumLabel.setFont(Theme.FONT_DEFAULT);
        sumLabel.setForeground(Theme.TEXT_SECONDARY);
        leftFooter.add(sumLabel);
        allComs.put("sum_label", sumLabel);
        JLabel selectHintLabel = new JLabel("");
        selectHintLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        selectHintLabel.setForeground(Theme.TEXT_HINT);
        leftFooter.add(selectHintLabel);
        allComs.put("select_hint_label", selectHintLabel);
        footerPanel.add(leftFooter, BorderLayout.WEST);

        JPanel pagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pagePanel.setBackground(Theme.CARD_BG);

        JLabel pageInfoLabel = new JLabel("第 1 页 / 共 1 页");
        pageInfoLabel.setFont(Theme.FONT_DEFAULT);
        pageInfoLabel.setForeground(Theme.TEXT_SECONDARY);
        pagePanel.add(pageInfoLabel);
        allComs.put("page_num_label", pageInfoLabel);

        pagePanel.add(Box.createHorizontalStrut(8));

        JLabel pageSizeLabel = new JLabel("每页 " + size + " 条");
        pageSizeLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        pageSizeLabel.setForeground(Theme.TEXT_HINT);
        pagePanel.add(pageSizeLabel);
        allComs.put("page_size_label", pageSizeLabel);

        pagePanel.add(Box.createHorizontalStrut(16));

        JButton previousPageButton = Theme.createFlatButton("◀ 上一页");
        previousPageButton.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        pagePanel.add(previousPageButton);
        allComs.put("previous_page_button", previousPageButton);

        JTextField jumpField = new JTextField(3);
        jumpField.setFont(Theme.FONT_DEFAULT);
        jumpField.setHorizontalAlignment(JTextField.CENTER);
        jumpField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
        jumpField.setToolTipText("输入页码后按 Enter 跳转");
        pagePanel.add(new JLabel("跳至"));
        pagePanel.add(jumpField);
        pagePanel.add(new JLabel("页"));
        allComs.put("jumpField", jumpField);

        JButton nextPageButton = Theme.createFlatButton("下一页 ▶");
        nextPageButton.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        pagePanel.add(nextPageButton);
        allComs.put("next_page_button", nextPageButton);

        footerPanel.add(pagePanel, BorderLayout.EAST);

        contentPanel = new JPanel(new BorderLayout(0, 0));
        contentPanel.setBackground(Theme.BG);
        contentPanel.add(searchCardPanel, BorderLayout.NORTH);
        contentPanel.add(tablePanel, BorderLayout.CENTER);
        contentPanel.add(footerPanel, BorderLayout.SOUTH);

        setLayout(new BorderLayout(0, 0));
        add(headerPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
            }
        });
    }

    private void initActionListeners() {
        JTable table = (JTable) allComs.get("table");

        ((JButton) allComs.get("search_button")).addActionListener(e -> {
            current = 1;
            initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
        });

        ((JButton) allComs.get("reset_button")).addActionListener(e -> {
            ((JTextField) allComs.get("keyword_textField")).setText("");
            current = 1;
            initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
        });

        ActionListener enterSearch = e -> {
            current = 1;
            initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
        };
        ((JTextField) allComs.get("keyword_textField")).addActionListener(enterSearch);

        ((JButton) allComs.get("del_button")).addActionListener(e -> deleteSelected(table));

        ((JButton) allComs.get("add_button")).addActionListener(e -> new ContactEditWindow(contactWindow, null));

        ((JButton) allComs.get("edit_button")).addActionListener(e -> openEditWindow(table));
        ((JButton) allComs.get("export_button")).addActionListener(e -> exportContacts());
        ((JButton) allComs.get("import_button")).addActionListener(e -> importContacts());

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openEditWindow(table);
                }
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                JLabel hint = (JLabel) allComs.get("select_hint_label");
                if (row >= 0) {
                    Object name = table.getModel().getValueAt(row, 1);
                    hint.setText("已选中：" + (name != null ? name : ""));
                } else {
                    hint.setText("");
                }
            }
        });

        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteRow");
        table.getActionMap().put("deleteRow", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!table.isEditing()) {
                    deleteSelected(table);
                }
            }
        });

        ((JButton) allComs.get("previous_page_button")).addActionListener(e -> {
            if (current > 1) {
                current--;
                initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
            } else {
                Theme.showMessage(contentPanel, "已经是第一页了", 1);
            }
        });
        ((JButton) allComs.get("next_page_button")).addActionListener(e -> {
            if (current < pages) {
                current++;
                initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
            } else {
                Theme.showMessage(contentPanel, "已经是最后一页了", 1);
            }
        });

        ((JTextField) allComs.get("jumpField")).addActionListener(e -> {
            JTextField jf = (JTextField) allComs.get("jumpField");
            try {
                int target = Integer.parseInt(jf.getText().trim());
                if (target >= 1 && target <= pages) {
                    current = target;
                    initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
                } else {
                    Theme.showMessage(contentPanel, "页码超出范围（1 ~ " + pages + "）", 1);
                }
            } catch (NumberFormatException ex) {
                Theme.showMessage(contentPanel, "请输入有效页码", -1);
            } finally {
                jf.setText("");
            }
        });
    }

    private JComboBox<String> createCategoryComboBox() {
        String[] defaultCategories = Utils.getDefaultContactCategories();
        String[] searchOptions = new String[defaultCategories.length + 1];
        searchOptions[0] = "";
        System.arraycopy(defaultCategories, 0, searchOptions, 1, defaultCategories.length);

        JComboBox<String> cb = Theme.createComboBox(searchOptions);
        cb.setEditable(true);
        cb.setSelectedIndex(0);
        Component editor = cb.getEditor().getEditorComponent();
        if (editor instanceof JTextField) {
            JTextField textField = (JTextField) editor;
            textField.setFont(Theme.FONT_DEFAULT);
            textField.setBorder(BorderFactory.createEmptyBorder());
        }
        return cb;
    }

    private JFileChooser createCsvFileChooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 文件 (*.csv)", "csv"));
        return chooser;
    }

    private File ensureCsvExtension(File file) {
        if (file == null) {
            return null;
        }
        if (file.getName().toLowerCase().endsWith(".csv")) {
            return file;
        }
        File parent = file.getParentFile();
        return parent == null ? new File(file.getName() + ".csv") : new File(parent, file.getName() + ".csv");
    }

    private void exportContacts() {
        JFileChooser chooser = createCsvFileChooser("导出联系人");
        chooser.setSelectedFile(new File("通讯录导出_" + Utils.formTime(new Date(), "yyyyMMdd_HHmmss") + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = ensureCsvExtension(chooser.getSelectedFile());
        if (file.exists() && Theme.showConfirm(contentPanel, "目标文件已存在，是否覆盖？") != 0) {
            return;
        }

        try {
            int count = Utils.exportContactsToCsv(file, getSearchMap());
            Theme.showMessage(contentPanel, "导出成功：" + file.getName() + "，共 " + count + " 条记录", 0);
        } catch (Exception ex) {
            ex.printStackTrace();
            Theme.showMessage(contentPanel, "导出失败：" + ex.getMessage(), -1);
        }
    }

    private void importContacts() {
        if (Theme.showConfirm(contentPanel, "导入会按编号更新已有记录，未匹配编号的记录将新增，是否继续？") != 0) {
            return;
        }

        JFileChooser chooser = createCsvFileChooser("导入联系人");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            Utils.ImportResult result = Utils.importContactsFromCsv(chooser.getSelectedFile());
            current = 1;
            initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
            String message = "导入完成：新增 " + result.inserted + " 条，更新 " + result.updated + " 条";
            if (result.skipped > 0) {
                message += "，跳过 " + result.skipped + " 条";
            }
            Theme.showMessage(contentPanel, message, result.skipped > 0 ? 1 : 0);
        } catch (Exception ex) {
            ex.printStackTrace();
            Theme.showMessage(contentPanel, "导入失败：" + ex.getMessage(), -1);
        }
    }

    private void schedulePageSizeRefresh() {
        SwingUtilities.invokeLater(this::refreshForViewportSizeChange);
    }

    private void refreshForViewportSizeChange() {
        if (adjustingPageSize || !(allComs.get("model") instanceof DefaultTableModel)) {
            return;
        }
        int newSize = resolvePageSize();
        if (newSize <= 0 || newSize == size) {
            return;
        }
        adjustingPageSize = true;
        try {
            int oldSize = Math.max(1, size);
            int firstRecordIndex = Math.max(0, (current - 1) * oldSize);
            size = newSize;
            current = firstRecordIndex / size + 1;
            initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
        } finally {
            adjustingPageSize = false;
        }
    }

    private int resolvePageSize() {
        Object scrollPaneObj = allComs.get("table_scrollPane");
        Object tableObj = allComs.get("table");
        if (!(scrollPaneObj instanceof JScrollPane) || !(tableObj instanceof JTable)) {
            return Math.max(1, size);
        }
        JScrollPane scrollPane = (JScrollPane) scrollPaneObj;
        JTable table = (JTable) tableObj;
        int viewportHeight = scrollPane.getViewport().getExtentSize().height;
        if (viewportHeight <= 0) {
            viewportHeight = table.getVisibleRect().height;
        }
        if (viewportHeight <= 0) {
            return Math.max(1, size);
        }
        int rowHeight = Math.max(1, table.getRowHeight());
        return Math.max(1, viewportHeight / rowHeight);
    }

    private void openEditWindow(JTable table) {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        int row = table.getSelectedRow();
        if (row >= 0) {
            Map<String, Object> map = new HashMap<>();
            map.put("c_id", getContactIdAtRow(row));
            map.put("c_name", table.getModel().getValueAt(row, 1));
            map.put("c_nickname", safeStr(table.getModel().getValueAt(row, 2)));
            map.put("c_phone", table.getModel().getValueAt(row, 3));
            map.put("c_email", table.getModel().getValueAt(row, 4));
            map.put("c_address", table.getModel().getValueAt(row, 5));
            map.put("c_company", table.getModel().getValueAt(row, 6));
            map.put("c_job_title", table.getModel().getValueAt(row, 7));
            map.put("notes", table.getModel().getValueAt(row, 8));
            new ContactEditWindow(contactWindow, map);
        } else {
            Theme.showMessage(contentPanel, "请先选择一条记录", -1);
        }
    }

    private void deleteSelected(JTable table) {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        int row = table.getSelectedRow();
        if (row >= 0) {
            int confirm = Theme.showConfirm(contentPanel, "确定要删除这条联系人记录吗？");
            if (confirm != 0) return;
            String id = getContactIdAtRow(row);
            if (Utils.isBlank(id)) {
                Theme.showMessage(contentPanel, "未找到联系人编号", -1);
                return;
            }
            try {
                Utils.getStatement().executeUpdate("delete from contact where c_id = '" + id + "'");
                initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
            } catch (Exception xe) {
                xe.printStackTrace();
                Theme.showMessage(contentPanel, "删除失败：" + xe.getMessage(), -1);
            }
        } else {
            Theme.showMessage(contentPanel, "请先选择一条记录", -1);
        }
    }

    private void updatePagingData() {
        ((JLabel) allComs.get("page_num_label")).setText("第 " + current + " 页 / 共 " + pages + " 页");
        ((JLabel) allComs.get("page_size_label")).setText("每页 " + size + " 条");
        ((JLabel) allComs.get("sum_label")).setText("共 " + total + " 条记录");
    }

    private void initPropMap() {
        propMap.clear();
        Map<String, String> cNicknameMap = new LinkedHashMap<>();
        cNicknameMap.put("1", "伙伴");
        cNicknameMap.put("2", "家人");
        cNicknameMap.put("3", "亲戚");
        cNicknameMap.put("4", "朋友");
        cNicknameMap.put("5", "同事");
        cNicknameMap.put("6", "客户");
        cNicknameMap.put("7", "其他");
        propMap.put("c_nickname", cNicknameMap);
    }

    public String getProp(String prop, String index) {
        if (Utils.isBlank(index)) {
            return "";
        }
        if (propMap.containsKey(prop)) {
            Map<String, String> map = propMap.get(prop);
            if (map != null && map.get(index) != null) {
                return map.get(index);
            }
        }
        return Utils.normalizeCategoryValue(index);
    }

    public String getPropValue(String prop, String name) {
        String normalized = "c_nickname".equals(prop) ? Utils.normalizeCategoryValue(name) : (name == null ? "" : name.trim());
        if (Utils.isBlank(normalized)) {
            return "";
        }
        if (propMap.containsKey(prop)) {
            Map<String, String> map = propMap.get(prop);
            if (map != null) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    if (normalized.equals(entry.getValue())) {
                        return entry.getKey();
                    }
                }
            }
        }
        return normalized;
    }

    public void showWindow() {
        this.setVisible(true);
        int resolvedSize = resolvePageSize();
        if (resolvedSize > 0) {
            size = resolvedSize;
        }
        initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
    }

    private void initTableData(DefaultTableModel model, Map<String, Object> map) {
        if (!adjustingPageSize) {
            int resolvedSize = resolvePageSize();
            if (resolvedSize > 0 && resolvedSize != size) {
                int firstRecordIndex = Math.max(0, (current - 1) * Math.max(1, size));
                size = resolvedSize;
                current = firstRecordIndex / size + 1;
            }
        }

        if (tableEditListener != null) {
            model.removeTableModelListener(tableEditListener);
            tableEditListener = null;
        }
        visibleContactIds.clear();
        while (model.getRowCount() > 0) {
            model.removeRow(0);
        }

        try (PreparedStatement countStatement = Utils.prepareContactCountStatement(map);
             ResultSet rsSum = countStatement.executeQuery()) {
            if (rsSum.next()) {
                total = rsSum.getInt("total");
            } else {
                total = 0;
            }

            pages = (int) Math.ceil(total * 1.0 / Math.max(1, size));
            if (pages == 0) {
                pages = 1;
            }
            if (current > pages) {
                current = pages;
            }
            updatePagingData();

            int start = Math.max(0, (current - 1) * size);
            try (PreparedStatement listStatement = Utils.prepareContactListStatement(map, start, size);
                 ResultSet rs = listStatement.executeQuery()) {
                while (rs.next()) {
                    String contactId = rs.getString("c_id");
                    visibleContactIds.add(contactId);
                    model.addRow(new Object[]{
                            start + model.getRowCount() + 1,
                            rs.getString("c_name"),
                            getProp("c_nickname", rs.getString("c_nickname")),
                            rs.getString("c_phone"),
                            rs.getString("c_email"),
                            rs.getString("c_address"),
                            rs.getString("c_company"),
                            rs.getString("c_job_title"),
                            rs.getString("notes")
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        tableEditListener = new TableModelListener() {
            private final String[] colFields = {
                    null,
                    "c_name",
                    null,
                    "c_phone",
                    "c_email",
                    "c_address",
                    "c_company",
                    "c_job_title",
                    "notes"
            };

            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() != TableModelEvent.UPDATE) return;
                int row = e.getFirstRow();
                int col = e.getColumn();
                if (row < 0 || col <= 0 || col >= colFields.length) return;
                String dbField = colFields[col];
                if (dbField == null) return;

                String id = getContactIdAtRow(row);
                if (Utils.isBlank(id)) return;
                Object valObj = model.getValueAt(row, col);
                String newVal = (valObj == null ? "" : valObj.toString()).replace("'", "''");

                try {
                    Utils.getStatement().executeUpdate(
                            "update contact set " + dbField + "='" + newVal + "' where c_id='" + id + "'"
                    );
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Theme.showMessage(contentPanel, "保存失败：" + ex.getMessage(), -1);
                }
            }
        };
        model.addTableModelListener(tableEditListener);
    }

    private Map<String, Object> getSearchMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("keyword", getText("keyword_textField", null));
        return map;
    }

    private String getText(String name, String prop) {
        Object o = allComs.get(name);
        if (o instanceof JTextField) {
            return ((JTextField) o).getText();
        }
        if (o instanceof JComboBox) {
            JComboBox<?> comboBox = (JComboBox<?>) o;
            Object item = comboBox.isEditable() ? comboBox.getEditor().getItem() : comboBox.getSelectedItem();
            String value = item == null ? "" : item.toString().trim();
            return prop == null ? value : getPropValue(prop, value);
        }
        return "";
    }

    private String getContactIdAtRow(int row) {
        if (row < 0 || row >= visibleContactIds.size()) {
            return "";
        }
        return visibleContactIds.get(row);
    }

    private String safeStr(Object o) {
        return o == null ? "" : o.toString();
    }
}
