package com.txl;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.*;
import javax.swing.*;
import java.sql.ResultSet;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Timer;

public class ContactWindow extends JFrame {
    private ContactWindow contactWindow;
    private JPanel panel;
    private Map<String, Object> allComs = new HashMap<>();
    private static Map<String, Map<String, String>> propMap = new HashMap<>();
    public Integer current = 1;
    public Integer pages = 10;
    public Integer size = 10;
    public Integer total = 0;
    public Integer mode = 0;

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
        this.setResizable(false);
        this.setTitle("通讯录管理");
        this.setSize(960, 660);
        getContentPane().setBackground(Theme.BG);

        // ====== 顶部标题栏 ======
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BorderLayout());
        headerPanel.setBackground(Theme.PRIMARY);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

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

        // 当前时间显示
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

        // 第一行：搜索条件
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchRow.setOpaque(false);

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));
        searchRow.add(searchIcon);

        searchRow.add(Theme.createLabel("姓名："));
        JTextField c_name_textField = Theme.createTextField("请输入姓名");
        c_name_textField.setPreferredSize(new Dimension(120, 34));
        searchRow.add(c_name_textField);
        allComs.put("c_name_textField", c_name_textField);

        searchRow.add(Theme.createLabel("昵称："));
        JTextField c_nickname_textField = Theme.createTextField("请输入昵称");
        c_nickname_textField.setPreferredSize(new Dimension(120, 34));
        searchRow.add(c_nickname_textField);
        allComs.put("c_nickname_textField", c_nickname_textField);

        searchRow.add(Theme.createLabel("电话："));
        JTextField c_phone_textField = Theme.createTextField("请输入电话");
        c_phone_textField.setPreferredSize(new Dimension(130, 34));
        searchRow.add(c_phone_textField);
        allComs.put("c_phone_textField", c_phone_textField);

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

        searchCardPanel.add(searchRow, BorderLayout.NORTH);

        // 第二行：操作按钮（右对齐）
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

        // 确认按钮（隐藏）
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

        DefaultTableModel model = new DefaultTableModel();
        JTable table = Theme.createTable(model);
        allComs.put("model", model);
        allComs.put("table", table);
        model.setColumnIdentifiers(new Object[]{
                "编号", "姓名", "昵称", "电话", "邮箱", "地址", "生日", "公司", "岗位", "分组", "备注"
        });
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        // ====== 底部分页栏 ======
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(Theme.CARD_BG);
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));

        // 左侧统计
        JLabel sum_label = new JLabel("共 0 条记录");
        sum_label.setFont(Theme.FONT_DEFAULT);
        sum_label.setForeground(Theme.TEXT_SECONDARY);
        footerPanel.add(sum_label, BorderLayout.WEST);
        allComs.put("sum_label", sum_label);

        // 右侧分页
        JPanel pagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pagePanel.setBackground(Theme.CARD_BG);

        JLabel page_info_label = new JLabel("第 1 页 / 共 1 页");
        page_info_label.setFont(Theme.FONT_DEFAULT);
        page_info_label.setForeground(Theme.TEXT_SECONDARY);
        pagePanel.add(page_info_label);
        allComs.put("page_num_label", page_info_label);

        pagePanel.add(Box.createHorizontalStrut(8));

        JLabel page_size_label = new JLabel("每页 10 条");
        page_size_label.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        page_size_label.setForeground(Theme.TEXT_HINT);
        pagePanel.add(page_size_label);
        allComs.put("page_size_label", page_size_label);

        pagePanel.add(Box.createHorizontalStrut(16));

        JButton previous_page_button = Theme.createFlatButton("上一页");
        previous_page_button.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        pagePanel.add(previous_page_button);
        allComs.put("previous_page_button", previous_page_button);

        JButton next_page_button = Theme.createFlatButton("下一页");
        next_page_button.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        pagePanel.add(next_page_button);
        allComs.put("next_page_button", next_page_button);

        footerPanel.add(pagePanel, BorderLayout.EAST);

        // ====== 组装 ======
        setLayout(new BorderLayout(0, 0));
        add(headerPanel, BorderLayout.NORTH);
        add(searchCardPanel, BorderLayout.CENTER);
        add(tablePanel, BorderLayout.CENTER);
        add(footerPanel, BorderLayout.SOUTH);

        // 用 BoxLayout 垂直排列中间和底部
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout(0, 0));
        contentPanel.setBackground(Theme.BG);
        contentPanel.add(searchCardPanel, BorderLayout.NORTH);
        contentPanel.add(tablePanel, BorderLayout.CENTER);
        contentPanel.add(footerPanel, BorderLayout.SOUTH);

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
        ((JButton) allComs.get("search_button")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DefaultTableModel model = (DefaultTableModel) allComs.get("model");
                initTableData(model, getSearchMap());
            }
        });
        ((JButton) allComs.get("del_button")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JTable table = (JTable) allComs.get("table");
                int row = table.getSelectedRow();
                if (row >= 0) {
                    int selectIndex = Theme.showConfirm(panel, "确定要删除这条联系人记录吗？");
                    if (selectIndex != 0) return;
                    String id = table.getModel().getValueAt(row, 0).toString();
                    Statement statement = Utils.getStatement();
                    try {
                        String sql = "delete from contact where c_id = '" + id + "'";
                        statement.executeUpdate(sql);
                        Theme.showMessage(panel, "删除成功", 0);
                        DefaultTableModel model = (DefaultTableModel) allComs.get("model");
                        initTableData(model, getSearchMap());
                    } catch (Exception xe) {
                        xe.printStackTrace();
                    }
                } else {
                    Theme.showMessage(panel, "请先选择一条记录", -1);
                }
            }
        });
        ((JButton) allComs.get("add_button")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contactWindow.setVisible(false);
                new ContactEditWindow(contactWindow, null);
            }
        });
        ((JButton) allComs.get("edit_button")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JTable table = (JTable) allComs.get("table");
                int row = table.getSelectedRow();
                if (row >= 0) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("c_id", table.getModel().getValueAt(row, 0));
                    map.put("c_name", table.getModel().getValueAt(row, 1));
                    map.put("c_nickname", table.getModel().getValueAt(row, 2));
                    map.put("c_phone", table.getModel().getValueAt(row, 3));
                    map.put("c_email", table.getModel().getValueAt(row, 4));
                    map.put("c_address", table.getModel().getValueAt(row, 5));
                    map.put("c_birthday", table.getModel().getValueAt(row, 6));
                    map.put("c_company", table.getModel().getValueAt(row, 7));
                    map.put("c_job_title", table.getModel().getValueAt(row, 8));
                    map.put("c_group_name", getPropValue("c_group_name", (table.getModel().getValueAt(row, 9)).toString()));
                    map.put("notes", table.getModel().getValueAt(row, 10));
                    contactWindow.setVisible(false);
                    new ContactEditWindow(contactWindow, map);
                } else {
                    Theme.showMessage(panel, "请先选择一条记录", -1);
                }
            }
        });
        ((JButton) allComs.get("previous_page_button")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (current > 1) {
                    current = current - 1;
                    initTableData(((DefaultTableModel) allComs.get("model")), getSearchMap());
                } else {
                    Theme.showMessage(panel, "已经是第一页了", 1);
                }
            }
        });
        ((JButton) allComs.get("next_page_button")).addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (current < pages) {
                    current = current + 1;
                    initTableData(((DefaultTableModel) allComs.get("model")), getSearchMap());
                } else {
                    Theme.showMessage(panel, "已经是最后一页了", 1);
                }
            }
        });
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
        Map<String, String> c_group_name_map = new HashMap<>();
        c_group_name_map.put("1", "家人");
        c_group_name_map.put("2", "亲戚");
        c_group_name_map.put("3", "朋友");
        c_group_name_map.put("4", "其他");
        propMap.put("c_group_name", c_group_name_map);
    }

    public String getProp(String prop, String index) {
        if (propMap.containsKey(prop)) {
            if (propMap.get(prop) != null && propMap.get(prop).get(index) != null) {
                return propMap.get(prop).get(index);
            }
        }
        return "";
    }

    public String getPropValue(String prop, String name) {
        if (propMap.containsKey(prop)) {
            if (propMap.get(prop) != null) {
                Map<String, String> map = propMap.get(prop);
                if (map.containsValue(name)) {
                    AtomicReference<String> index = new AtomicReference<>("");
                    map.forEach((key, value) -> {
                        if (name.equals(value)) {
                            index.set(key);
                        }
                    });
                    return index.get();
                }
            }
        }
        return "";
    }

    public void showWindow() {
        this.setVisible(true);
        initTableData(((DefaultTableModel) allComs.get("model")), getSearchMap());
    }

    private void initTableData(DefaultTableModel model, Map<String, Object> map) {
        while (model.getRowCount() > 0) {
            model.removeRow(0);
        }
        Statement statement = Utils.getStatement();
        try {
            StringBuilder sql = new StringBuilder("select contact.* from contact where 1 = 1 ");
            StringBuilder sqlSum = new StringBuilder("select count(1) as total from contact where 1 = 1 ");
            map.forEach((key, value) -> {
                if (!Utils.isBlank(value.toString())) {
                    sql.append(" and " + key + " = '" + value + "'");
                    sqlSum.append(" and " + key + " = '" + value + "'");
                }
            });
            ResultSet resultSetSum = statement.executeQuery(sqlSum.toString());
            Boolean updatePage = false;
            if (resultSetSum.next()) {
                total = resultSetSum.getInt("total");
                pages = (int) Math.ceil(resultSetSum.getInt("total") * 1.0 / size);
                if (pages != 0) {
                    if (current > pages) {
                        current = pages;
                        initTableData(model, map);
                        updatePage = true;
                    }
                } else {
                    pages = 1;
                }
                updatePagingData();
            }
            if (updatePage) return;
            Integer start = (current - 1) * size;
            sql.append(" limit " + start + "," + size + "");
            ResultSet resultSet = statement.executeQuery(sql.toString());
            while (resultSet.next()) {
                model.addRow(new Object[]{
                        resultSet.getString("c_id"),
                        resultSet.getString("c_name"),
                        resultSet.getString("c_nickname"),
                        resultSet.getString("c_phone"),
                        resultSet.getString("c_email"),
                        resultSet.getString("c_address"),
                        resultSet.getString("c_birthday"),
                        resultSet.getString("c_company"),
                        resultSet.getString("c_job_title"),
                        getProp("c_group_name", resultSet.getString("c_group_name")),
                        resultSet.getString("notes"),
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, Object> getSearchMap() {
        Map<String, Object> map = new HashMap<>();
        String c_name_textField_text = getText("c_name_textField", null);
        map.put("c_name", c_name_textField_text);
        String c_nickname_textField_text = getText("c_nickname_textField", null);
        map.put("c_nickname", c_nickname_textField_text);
        String c_phone_textField_text = getText("c_phone_textField", null);
        map.put("c_phone", c_phone_textField_text);
        String c_group_name_comboBox_text = getText("c_group_name_comboBox", "c_group_name");
        map.put("c_group_name", c_group_name_comboBox_text);
        return map;
    }

    private String getText(String name, String prop) {
        if (allComs.containsKey(name)) {
            Object o = allComs.get(name);
            if (o instanceof JTextField) {
                return ((JTextField) o).getText();
            } else if (o instanceof JComboBox) {
                String itemName = ((JComboBox<?>) o).getSelectedItem().toString();
                return getPropValue(prop, itemName);
            }
        }
        return "";
    }
}
