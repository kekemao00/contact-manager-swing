package com.txl;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import javax.swing.*;
import java.sql.ResultSet;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.TableModelListener;
import javax.swing.event.TableModelEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Timer;

public class ContactWindow extends JFrame {
    private ContactWindow contactWindow;
    // panel 指向内容区，用于消息框定位
    private JPanel contentPanel;
    private Map<String, Object> allComs = new HashMap<>();
    private static Map<String, Map<String, String>> propMap = new HashMap<>();
    public Integer current = 1;
    public Integer pages = 1;
    public Integer size = 50;
    public Integer total = 0;
    public Integer mode = 0;

    // 直接编辑监听器引用，每次数据刷新前先移除旧的
    private TableModelListener tableEditListener;

    public ContactWindow() {
        initWindow();
        initPropMap();
        initActionListeners();
        DefaultTableModel model = (DefaultTableModel) allComs.get("model");
        initTableData(model, getSearchMap());
        this.setVisible(true);
        setLocationRelativeTo(null);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public ContactWindow(JFrame parentJFrame) {
        initWindow();
        initPropMap();
        initActionListeners();
        DefaultTableModel model = (DefaultTableModel) allComs.get("model");
        initTableData(model, getSearchMap());
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
    }

    public ContactWindow(Integer mode) {
        this.mode = mode;
        initWindow();
        initPropMap();
        initActionListeners();
        DefaultTableModel model = (DefaultTableModel) allComs.get("model");
        initTableData(model, getSearchMap());
        this.setVisible(true);
        this.setLocationRelativeTo(null);
        switch (mode) {
            case 1:
                ((JButton) allComs.get("del_button")).setVisible(false);
                ((JButton) allComs.get("add_button")).setVisible(false);
                ((JButton) allComs.get("edit_button")).setVisible(false);
                break;
            default:
                break;
        }
    }

    private void initWindow() {
        this.contactWindow = this;
        this.setResizable(true);
        this.setTitle("通讯录管理");
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        this.setSize(960, 660);
        getContentPane().setBackground(Theme.BG);

        // ====== 顶部标题栏 ======
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
        Timer timer = new Timer(1000, e -> {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            timeLabel.setText(sdf.format(new Date()));
        });
        timer.start();
        timer.setInitialDelay(0);
        headerRight.add(timeLabel);
        headerPanel.add(headerRight, BorderLayout.EAST);

        // ====== 搜索栏 ======
        JPanel searchCardPanel = Theme.createCardPanel();
        searchCardPanel.setLayout(new BorderLayout(0, 8));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchRow.setOpaque(false);

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        searchRow.add(searchIcon);

        searchRow.add(Theme.createLabel("姓名："));
        JTextField c_name_textField = Theme.createTextField("姓名");
        c_name_textField.setPreferredSize(new Dimension(120, 34));
        searchRow.add(c_name_textField);
        allComs.put("c_name_textField", c_name_textField);

        searchRow.add(Theme.createLabel("分类："));
        String[] c_nickname_options = {"全部", "伙伴", "家人", "亲戚", "朋友", "同事", "客户", "其他"};
        JComboBox<String> c_nickname_comboBox = Theme.createComboBox(c_nickname_options);
        c_nickname_comboBox.setPreferredSize(new Dimension(100, 34));
        searchRow.add(c_nickname_comboBox);
        allComs.put("c_nickname_comboBox", c_nickname_comboBox);

        searchRow.add(Theme.createLabel("公司："));
        JTextField c_company_textField = Theme.createTextField("公司名称");
        c_company_textField.setPreferredSize(new Dimension(150, 34));
        searchRow.add(c_company_textField);
        allComs.put("c_company_textField", c_company_textField);

        searchRow.add(Theme.createLabel("分组："));
        String[] c_group_name_options = {"全部", "家人", "亲戚", "朋友", "其他"};
        JComboBox<String> c_group_name_comboBox = Theme.createComboBox(c_group_name_options);
        c_group_name_comboBox.setPreferredSize(new Dimension(100, 34));
        searchRow.add(c_group_name_comboBox);
        allComs.put("c_group_name_comboBox", c_group_name_comboBox);

        JButton search_button = Theme.createSearchButton();
        search_button.setPreferredSize(new Dimension(90, 34));
        searchRow.add(search_button);
        allComs.put("search_button", search_button);

        // 重置按钮
        JButton reset_button = Theme.createFlatButton("↺ 重置");
        reset_button.setPreferredSize(new Dimension(80, 34));
        searchRow.add(reset_button);
        allComs.put("reset_button", reset_button);

        searchCardPanel.add(searchRow, BorderLayout.NORTH);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionRow.setOpaque(false);

        JButton add_button = Theme.createPrimaryButton("➕ 新增");
        add_button.setPreferredSize(new Dimension(90, 34));
        actionRow.add(add_button);
        allComs.put("add_button", add_button);

        JButton edit_button = Theme.createFlatButton("✏️ 修改");
        edit_button.setPreferredSize(new Dimension(90, 34));
        actionRow.add(edit_button);
        allComs.put("edit_button", edit_button);

        JButton del_button = Theme.createDangerButton("🗑️ 删除");
        del_button.setPreferredSize(new Dimension(90, 34));
        actionRow.add(del_button);
        allComs.put("del_button", del_button);

        JButton select_button = Theme.createPrimaryButton("确认");
        select_button.setPreferredSize(new Dimension(90, 34));
        select_button.setVisible(false);
        actionRow.add(select_button);
        allComs.put("select_button", select_button);

        searchCardPanel.add(actionRow, BorderLayout.SOUTH);

        // ====== 表格区域 ======
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(Theme.BG);
        tablePanel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        // 枚举列（编号0、分类2、分组9）禁止直接编辑，文本列允许就地修改
        DefaultTableModel model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 0 && column != 2 && column != 9;
            }
        };
        JTable table = Theme.createTable(model);
        allComs.put("model", model);
        allComs.put("table", table);
        model.setColumnIdentifiers(new Object[]{
                "编号", "姓名", "分类", "电话", "邮箱", "地址", "生日", "公司", "岗位", "分组", "备注"
        });

        // 设置各列宽度（编号列窄，备注列自动扩展）
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(70);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(140);
        table.getColumnModel().getColumn(5).setPreferredWidth(120);
        table.getColumnModel().getColumn(6).setPreferredWidth(90);
        table.getColumnModel().getColumn(7).setPreferredWidth(120);
        table.getColumnModel().getColumn(8).setPreferredWidth(80);
        table.getColumnModel().getColumn(9).setPreferredWidth(60);
        table.getColumnModel().getColumn(10).setPreferredWidth(180);

        // 右键菜单：复制单元格内容
        JPopupMenu tableMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("复制单元格");
        copyItem.setFont(Theme.FONT_DEFAULT);
        copyItem.addActionListener(ev -> {
            int row = table.getSelectedRow();
            int col = table.getSelectedColumn();
            if (row >= 0 && col >= 0) {
                Object val = table.getValueAt(row, col);
                if (val != null) {
                    java.awt.datatransfer.StringSelection sel =
                            new java.awt.datatransfer.StringSelection(val.toString());
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
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
        // 滚动速度优化
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        // ====== 底部分页栏 ======
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(Theme.CARD_BG);
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));

        // 左侧：总记录数 + 选中提示
        JPanel leftFooter = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftFooter.setBackground(Theme.CARD_BG);
        JLabel sum_label = new JLabel("共 0 条记录");
        sum_label.setFont(Theme.FONT_DEFAULT);
        sum_label.setForeground(Theme.TEXT_SECONDARY);
        leftFooter.add(sum_label);
        allComs.put("sum_label", sum_label);
        JLabel select_hint_label = new JLabel("");
        select_hint_label.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        select_hint_label.setForeground(Theme.TEXT_HINT);
        leftFooter.add(select_hint_label);
        allComs.put("select_hint_label", select_hint_label);
        footerPanel.add(leftFooter, BorderLayout.WEST);

        // 右侧分页
        JPanel pagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pagePanel.setBackground(Theme.CARD_BG);

        JLabel page_info_label = new JLabel("第 1 页 / 共 1 页");
        page_info_label.setFont(Theme.FONT_DEFAULT);
        page_info_label.setForeground(Theme.TEXT_SECONDARY);
        pagePanel.add(page_info_label);
        allComs.put("page_num_label", page_info_label);

        pagePanel.add(Box.createHorizontalStrut(8));

        JLabel page_size_label = new JLabel("每页 50 条");
        page_size_label.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        page_size_label.setForeground(Theme.TEXT_HINT);
        pagePanel.add(page_size_label);
        allComs.put("page_size_label", page_size_label);

        pagePanel.add(Box.createHorizontalStrut(16));

        JButton previous_page_button = Theme.createFlatButton("◀ 上一页");
        previous_page_button.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        pagePanel.add(previous_page_button);
        allComs.put("previous_page_button", previous_page_button);

        // 跳转页输入
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

        JButton next_page_button = Theme.createFlatButton("下一页 ▶");
        next_page_button.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        pagePanel.add(next_page_button);
        allComs.put("next_page_button", next_page_button);

        footerPanel.add(pagePanel, BorderLayout.EAST);

        // ====== 组装 ======
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

        // 搜索按钮
        ((JButton) allComs.get("search_button")).addActionListener(e -> {
            current = 1;
            DefaultTableModel model = (DefaultTableModel) allComs.get("model");
            initTableData(model, getSearchMap());
        });

        // 重置按钮
        ((JButton) allComs.get("reset_button")).addActionListener(e -> {
            ((JTextField) allComs.get("c_name_textField")).setText("");
            ((JTextField) allComs.get("c_company_textField")).setText("");
            ((JComboBox<?>) allComs.get("c_nickname_comboBox")).setSelectedIndex(0);
            ((JComboBox<?>) allComs.get("c_group_name_comboBox")).setSelectedIndex(0);
            current = 1;
            initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
        });

        // 姓名/公司输入框按 Enter 触发搜索
        ActionListener enterSearch = e -> {
            current = 1;
            initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
        };
        ((JTextField) allComs.get("c_name_textField")).addActionListener(enterSearch);
        ((JTextField) allComs.get("c_company_textField")).addActionListener(enterSearch);

        // 删除按钮
        ((JButton) allComs.get("del_button")).addActionListener(e -> deleteSelected(table));

        // 新增按钮
        ((JButton) allComs.get("add_button")).addActionListener(e -> {
            contactWindow.setVisible(false);
            new ContactEditWindow(contactWindow, null);
        });

        // 修改按钮
        ((JButton) allComs.get("edit_button")).addActionListener(e -> openEditWindow(table));

        // 表格双击 → 打开编辑窗口
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openEditWindow(table);
                }
            }
        });

        // 选中行时更新状态栏提示
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

        // Delete 键删除选中行
        table.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteRow");
        table.getActionMap().put("deleteRow", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 正在编辑单元格时 Delete 不触发删除
                if (!table.isEditing()) {
                    deleteSelected(table);
                }
            }
        });

        // 分页按钮
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

        // 跳转页输入框：Enter 跳转
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

    /** 打开编辑窗口（从表格当前选中行读取数据） */
    private void openEditWindow(JTable table) {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        int row = table.getSelectedRow();
        if (row >= 0) {
            Map<String, Object> map = new HashMap<>();
            map.put("c_id",       table.getModel().getValueAt(row, 0));
            map.put("c_name",     table.getModel().getValueAt(row, 1));
            // 分类列显示中文名，需反查 propMap key 传给编辑窗口
            map.put("c_nickname", getPropValue("c_nickname", safeStr(table.getModel().getValueAt(row, 2))));
            map.put("c_phone",    table.getModel().getValueAt(row, 3));
            map.put("c_email",    table.getModel().getValueAt(row, 4));
            map.put("c_address",  table.getModel().getValueAt(row, 5));
            map.put("c_birthday", table.getModel().getValueAt(row, 6));
            map.put("c_company",  table.getModel().getValueAt(row, 7));
            map.put("c_job_title",table.getModel().getValueAt(row, 8));
            map.put("c_group_name", getPropValue("c_group_name", safeStr(table.getModel().getValueAt(row, 9))));
            map.put("notes",      table.getModel().getValueAt(row, 10));
            contactWindow.setVisible(false);
            new ContactEditWindow(contactWindow, map);
        } else {
            Theme.showMessage(contentPanel, "请先选择一条记录", -1);
        }
    }

    /** 删除选中行 */
    private void deleteSelected(JTable table) {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        int row = table.getSelectedRow();
        if (row >= 0) {
            int confirm = Theme.showConfirm(contentPanel, "确定要删除这条联系人记录吗？");
            if (confirm != 0) return;
            String id = table.getModel().getValueAt(row, 0).toString();
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
        JLabel page_num_label = (JLabel) allComs.get("page_num_label");
        page_num_label.setText("第 " + current + " 页 / 共 " + pages + " 页");
        JLabel page_size_label = (JLabel) allComs.get("page_size_label");
        page_size_label.setText("每页 " + size + " 条");
        JLabel page_total_label = (JLabel) allComs.get("sum_label");
        page_total_label.setText("共 " + total + " 条记录");
    }

    private void initPropMap() {
        // 分类映射（复用 c_nickname 字段）
        Map<String, String> c_nickname_map = new LinkedHashMap<>();
        c_nickname_map.put("1", "伙伴");
        c_nickname_map.put("2", "家人");
        c_nickname_map.put("3", "亲戚");
        c_nickname_map.put("4", "朋友");
        c_nickname_map.put("5", "同事");
        c_nickname_map.put("6", "客户");
        c_nickname_map.put("7", "其他");
        propMap.put("c_nickname", c_nickname_map);

        // 分组映射
        Map<String, String> c_group_name_map = new LinkedHashMap<>();
        c_group_name_map.put("1", "家人");
        c_group_name_map.put("2", "亲戚");
        c_group_name_map.put("3", "朋友");
        c_group_name_map.put("4", "其他");
        propMap.put("c_group_name", c_group_name_map);
    }

    public String getProp(String prop, String index) {
        if (propMap.containsKey(prop)) {
            Map<String, String> m = propMap.get(prop);
            if (m != null && m.get(index) != null) return m.get(index);
        }
        return "";
    }

    public String getPropValue(String prop, String name) {
        if (propMap.containsKey(prop)) {
            Map<String, String> map = propMap.get(prop);
            if (map != null && map.containsValue(name)) {
                AtomicReference<String> index = new AtomicReference<>("");
                map.forEach((key, value) -> {
                    if (name.equals(value)) index.set(key);
                });
                return index.get();
            }
        }
        return "";
    }

    public void showWindow() {
        this.setVisible(true);
        initTableData((DefaultTableModel) allComs.get("model"), getSearchMap());
    }

    private void initTableData(DefaultTableModel model, Map<String, Object> map) {
        // 移除旧监听器，防止数据填充时误触发 UPDATE
        if (tableEditListener != null) {
            model.removeTableModelListener(tableEditListener);
            tableEditListener = null;
        }
        while (model.getRowCount() > 0) {
            model.removeRow(0);
        }

        Set<String> likeFields = new HashSet<>(Arrays.asList("c_name", "c_company"));

        try {
            StringBuilder sql    = new StringBuilder("select contact.* from contact where 1=1");
            StringBuilder sqlSum = new StringBuilder("select count(1) as total from contact where 1=1");
            map.forEach((key, value) -> {
                if (!Utils.isBlank(value.toString())) {
                    if (likeFields.contains(key)) {
                        String safe = value.toString().replace("'", "''");
                        sql.append(" and ").append(key).append(" like '%").append(safe).append("%'");
                        sqlSum.append(" and ").append(key).append(" like '%").append(safe).append("%'");
                    } else {
                        String safe = value.toString().replace("'", "''");
                        sql.append(" and ").append(key).append("='").append(safe).append("'");
                        sqlSum.append(" and ").append(key).append("='").append(safe).append("'");
                    }
                }
            });

            Statement statement = Utils.getStatement();
            ResultSet rsSum = statement.executeQuery(sqlSum.toString());
            boolean updatePage = false;
            if (rsSum.next()) {
                total = rsSum.getInt("total");
                pages = (int) Math.ceil(total * 1.0 / size);
                if (pages == 0) pages = 1;
                if (current > pages) {
                    current = pages;
                    initTableData(model, map);
                    updatePage = true;
                }
                updatePagingData();
            }
            if (updatePage) return;

            int start = (current - 1) * size;
            sql.append(" limit ").append(start).append(",").append(size);
            ResultSet rs = statement.executeQuery(sql.toString());
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getString("c_id"),
                        rs.getString("c_name"),
                        getProp("c_nickname", rs.getString("c_nickname")),
                        rs.getString("c_phone"),
                        rs.getString("c_email"),
                        rs.getString("c_address"),
                        rs.getString("c_birthday"),
                        rs.getString("c_company"),
                        rs.getString("c_job_title"),
                        getProp("c_group_name", rs.getString("c_group_name")),
                        rs.getString("notes"),
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 数据加载完毕，注册就地编辑保存监听器
        tableEditListener = new TableModelListener() {
            private final String[] colFields = {
                null,           // 0: 编号（不可编辑）
                "c_name",       // 1: 姓名
                null,           // 2: 分类（不可编辑）
                "c_phone",      // 3: 电话
                "c_email",      // 4: 邮箱
                "c_address",    // 5: 地址
                "c_birthday",   // 6: 生日
                "c_company",    // 7: 公司
                "c_job_title",  // 8: 岗位
                null,           // 9: 分组（不可编辑）
                "notes"         // 10: 备注
            };

            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() != TableModelEvent.UPDATE) return;
                int row = e.getFirstRow();
                int col = e.getColumn();
                if (row < 0 || col <= 0 || col >= colFields.length) return;
                String dbField = colFields[col];
                if (dbField == null) return;

                Object idObj = model.getValueAt(row, 0);
                if (idObj == null) return;
                String id = idObj.toString();
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
        map.put("c_name",       getText("c_name_textField", null));
        map.put("c_nickname",   getText("c_nickname_comboBox", "c_nickname"));
        map.put("c_company",    getText("c_company_textField", null));
        map.put("c_group_name", getText("c_group_name_comboBox", "c_group_name"));
        return map;
    }

    private String getText(String name, String prop) {
        if (allComs.containsKey(name)) {
            Object o = allComs.get(name);
            if (o instanceof JTextField)  return ((JTextField) o).getText();
            if (o instanceof JComboBox)   return getPropValue(prop, ((JComboBox<?>) o).getSelectedItem().toString());
        }
        return "";
    }

    private String safeStr(Object o) {
        return o == null ? "" : o.toString();
    }
}
