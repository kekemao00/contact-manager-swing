package com.txl;

import java.util.*;
import javax.swing.*;
import java.text.SimpleDateFormat;
import java.awt.*;
import java.awt.event.*;
import java.sql.Statement;

public class ContactEditWindow extends JFrame {
    private ContactEditWindow contactEditWindow;
    private ContactWindow contactWindow;
    private Map<String, Object> allComs = new HashMap<>();

    public ContactEditWindow(ContactWindow contactWindowTemp, Map<String, Object> map) {
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.contactEditWindow = this;
        this.contactWindow = contactWindowTemp;
        boolean isEdit = map != null;
        this.setTitle(isEdit ? "编辑联系人" : "新增联系人");
        this.setResizable(false);
        // 加高窗口，字段不再拥挤
        this.setSize(520, 620);
        this.getContentPane().setBackground(Theme.BG);

        // ====== 标题栏 ======
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Theme.PRIMARY);

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 14));
        headerLeft.setBackground(Theme.PRIMARY);
        headerLeft.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        JLabel headerIcon = new JLabel(isEdit ? "✏️" : "➕");
        headerIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        headerLeft.add(headerIcon);
        headerLeft.add(Box.createHorizontalStrut(8));
        JLabel headerTitle = new JLabel(isEdit ? "编辑联系人" : "新增联系人");
        headerTitle.setFont(new Font("微软雅黑", Font.BOLD, 15));
        headerTitle.setForeground(Color.WHITE);
        headerLeft.add(headerTitle);
        headerPanel.add(headerLeft, BorderLayout.WEST);

        // 标题栏右侧提示
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 18));
        headerRight.setBackground(Theme.PRIMARY);
        headerRight.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));
        JLabel tipLabel = new JLabel("* 为必填项");
        tipLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        tipLabel.setForeground(new Color(180, 210, 255));
        headerRight.add(tipLabel);
        headerPanel.add(headerRight, BorderLayout.EAST);

        // ====== 表单区域（GridBagLayout，支持自然 Tab 顺序） ======
        JPanel formPanel = Theme.createCardPanel();
        formPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 字段定义 {key, 显示标签, placeholder, isComboBox, comboOptions...}
        // 用二维数组只定义 key 和 label，特殊字段单独处理
        String[][] fields = {
                {"c_name",      "姓名",  "请输入姓名"},
                {"c_nickname",  "分类",  null},
                {"c_phone",     "电话",  "请输入电话"},
                {"c_email",     "邮箱",  "请输入邮箱"},
                {"c_address",   "地址",  "请输入地址"},
                {"c_birthday",  "生日",  "yyyy-MM-dd"},
                {"c_company",   "公司",  "请输入公司"},
                {"c_job_title", "岗位",  "请输入岗位"},
                {"c_group_name","分组",  null},
                {"notes",       "备注",  "请输入备注"},
        };

        // 隐藏 ID 字段
        JTextField c_id_textField = new JTextField();
        c_id_textField.setVisible(false);
        formPanel.add(c_id_textField, new GridBagConstraints());
        allComs.put("c_id_textField", c_id_textField);

        int row = 0;
        // 保存所有可聚焦组件，用于建立 Tab 顺序
        List<Component> focusOrder = new ArrayList<>();

        for (String[] field : fields) {
            String key = field[0];
            String label = field[1];
            String placeholder = field[2];
            boolean required = "c_name".equals(key);

            // 标签列
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            gbc.ipadx = 0;
            JLabel lbl = new JLabel(label + (required ? " *" : ""));
            lbl.setFont(Theme.FONT_BOLD);
            lbl.setForeground(required ? Theme.PRIMARY : Theme.TEXT_PRIMARY);
            lbl.setHorizontalAlignment(JLabel.RIGHT);
            lbl.setPreferredSize(new Dimension(58, 34));
            formPanel.add(lbl, gbc);

            // 输入控件列
            gbc.gridx = 1;
            gbc.weightx = 1.0;

            if ("c_nickname".equals(key)) {
                String[] options = {"伙伴", "家人", "亲戚", "朋友", "同事", "客户", "其他"};
                JComboBox<String> cb = Theme.createComboBox(options);
                cb.setSelectedItem("伙伴");
                cb.setPreferredSize(new Dimension(0, 34));
                formPanel.add(cb, gbc);
                allComs.put(key + "_comboBox", cb);
                focusOrder.add(cb);
            } else if ("c_group_name".equals(key)) {
                String[] options = {"家人", "亲戚", "朋友", "其他"};
                JComboBox<String> cb = Theme.createComboBox(options);
                cb.setPreferredSize(new Dimension(0, 34));
                formPanel.add(cb, gbc);
                allComs.put(key + "_comboBox", cb);
                focusOrder.add(cb);
            } else {
                JTextField tf = Theme.createTextField(placeholder != null ? placeholder : "");
                tf.setPreferredSize(new Dimension(0, 34));
                formPanel.add(tf, gbc);
                allComs.put(key + "_textField", tf);
                focusOrder.add(tf);

                // 焦点高亮
                tf.addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent e) {
                        tf.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(Theme.PRIMARY),
                                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
                    }
                    @Override public void focusLost(FocusEvent e) {
                        tf.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(Theme.BORDER),
                                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
                    }
                });
            }
            row++;
        }

        // 设置自然 Tab 焦点顺序
        FocusTraversalPolicy ftp = new FocusTraversalPolicy() {
            @Override public Component getComponentAfter(Container aContainer, Component aComponent) {
                int idx = focusOrder.indexOf(aComponent);
                return focusOrder.get((idx + 1) % focusOrder.size());
            }
            @Override public Component getComponentBefore(Container aContainer, Component aComponent) {
                int idx = focusOrder.indexOf(aComponent);
                return focusOrder.get((idx - 1 + focusOrder.size()) % focusOrder.size());
            }
            @Override public Component getFirstComponent(Container aContainer) { return focusOrder.get(0); }
            @Override public Component getLastComponent(Container aContainer) { return focusOrder.get(focusOrder.size() - 1); }
            @Override public Component getDefaultComponent(Container aContainer) { return focusOrder.get(0); }
        };
        formPanel.setFocusTraversalPolicyProvider(true);
        formPanel.setFocusTraversalPolicy(ftp);

        // 编辑模式回填
        if (isEdit) {
            fillField("c_id_textField",      map.get("c_id"));
            fillField("c_name_textField",    map.get("c_name"));
            fillField("c_phone_textField",   map.get("c_phone"));
            fillField("c_email_textField",   map.get("c_email"));
            fillField("c_address_textField", map.get("c_address"));
            fillField("c_birthday_textField",map.get("c_birthday"));
            fillField("c_company_textField", map.get("c_company"));
            fillField("c_job_title_textField",map.get("c_job_title"));
            fillField("notes_textField",     map.get("notes"));
            if (!Utils.isNull(map.get("c_nickname"))) {
                JComboBox<String> cb = (JComboBox<String>) allComs.get("c_nickname_comboBox");
                cb.setSelectedItem(contactWindow.getProp("c_nickname", map.get("c_nickname").toString()));
            }
            if (!Utils.isNull(map.get("c_group_name"))) {
                Object o = allComs.get("c_group_name_comboBox");
                if (o instanceof JComboBox) {
                    ((JComboBox<String>) o).setSelectedItem(
                            contactWindow.getProp("c_group_name", map.get("c_group_name").toString()));
                }
            }
        }

        // ====== 底部按钮 ======
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttonPanel.setBackground(Theme.BG);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JButton cancel_button = Theme.createFlatButton("取 消");
        cancel_button.setPreferredSize(new Dimension(100, 38));
        buttonPanel.add(cancel_button);

        JButton submit_button = Theme.createPrimaryButton(isEdit ? "保 存" : "添 加");
        submit_button.setPreferredSize(new Dimension(100, 38));
        buttonPanel.add(submit_button);

        // 取消
        cancel_button.addActionListener(e -> {
            contactEditWindow.dispose();
            contactWindow.showWindow();
        });

        // Esc 键同取消
        getRootPane().registerKeyboardAction(e -> {
            contactEditWindow.dispose();
            contactWindow.showWindow();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Enter 键触发保存（不在文本框内时）
        getRootPane().setDefaultButton(submit_button);

        // 保存逻辑
        submit_button.addActionListener(e -> {
            String c_id_value    = ((JTextField) allComs.get("c_id_textField")).getText();
            String c_name_value  = getTextField("c_name_textField");
            if (c_name_value.trim().isEmpty()) {
                Theme.showMessage(formPanel, "请输入姓名！", -1);
                focusOrder.get(0).requestFocus();
                return;
            }
            String c_nickname_value  = getComboBoxValue("c_nickname_comboBox",  "c_nickname");
            String c_phone_value     = getTextField("c_phone_textField");
            String c_email_value     = getTextField("c_email_textField");
            String c_address_value   = getTextField("c_address_textField");
            String c_birthday_value  = getTextField("c_birthday_textField");
            String c_company_value   = getTextField("c_company_textField");
            String c_job_title_value = getTextField("c_job_title_textField");
            String c_group_name_value= getComboBoxValue("c_group_name_comboBox","c_group_name");
            String notes_value       = getTextField("notes_textField");

            if (!Utils.isBlank(c_birthday_value)) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    sdf.setLenient(false);
                    sdf.parse(c_birthday_value);
                } catch (Exception et) {
                    Theme.showMessage(formPanel, "生日格式错误，请使用 yyyy-MM-dd", -1);
                    return;
                }
            }

            // 转义单引号
            c_name_value      = c_name_value.replace("'","''");
            c_nickname_value  = c_nickname_value.replace("'","''");
            c_phone_value     = c_phone_value.replace("'","''");
            c_email_value     = c_email_value.replace("'","''");
            c_address_value   = c_address_value.replace("'","''");
            c_birthday_value  = c_birthday_value.replace("'","''");
            c_company_value   = c_company_value.replace("'","''");
            c_job_title_value = c_job_title_value.replace("'","''");
            c_group_name_value= c_group_name_value.replace("'","''");
            notes_value       = notes_value.replace("'","''");

            try {
                Statement statement = Utils.getStatement();
                int affected;
                if (!isEdit) {
                    String sql = "insert into contact(c_name,c_nickname,c_phone,c_email,c_address,c_birthday,c_company,c_job_title,c_group_name,notes) "
                            + "values('" + c_name_value + "','" + c_nickname_value + "','" + c_phone_value + "','"
                            + c_email_value + "','" + c_address_value + "','" + c_birthday_value + "','"
                            + c_company_value + "','" + c_job_title_value + "','" + c_group_name_value + "','" + notes_value + "')";
                    affected = statement.executeUpdate(sql);
                } else {
                    String sql = "update contact set c_name='" + c_name_value + "',c_nickname='" + c_nickname_value
                            + "',c_phone='" + c_phone_value + "',c_email='" + c_email_value + "',c_address='"
                            + c_address_value + "',c_birthday='" + c_birthday_value + "',c_company='"
                            + c_company_value + "',c_job_title='" + c_job_title_value + "',c_group_name='"
                            + c_group_name_value + "',notes='" + notes_value + "' where c_id='" + c_id_value + "'";
                    affected = statement.executeUpdate(sql);
                }
                // 保存成功：直接关闭，由列表刷新结果反馈，不弹额外弹窗
                if (affected > 0) {
                    contactEditWindow.dispose();
                    contactWindow.showWindow();
                } else {
                    Theme.showMessage(formPanel, isEdit ? "修改失败" : "添加失败", -1);
                }
            } catch (Exception ec) {
                ec.printStackTrace();
                Theme.showMessage(formPanel, "操作失败：" + ec.getMessage(), -1);
            }
        });

        // ====== 组装 ======
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Theme.BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        this.setLocationRelativeTo(null);
        this.setVisible(true);
        // 自动聚焦到姓名字段
        SwingUtilities.invokeLater(() -> focusOrder.get(0).requestFocusInWindow());
    }

    private void fillField(String key, Object value) {
        if (!Utils.isNull(value)) {
            Object comp = allComs.get(key);
            if (comp instanceof JTextField) ((JTextField) comp).setText(value.toString());
        }
    }

    private String getTextField(String key) {
        Object o = allComs.get(key);
        if (o instanceof JTextField) return ((JTextField) o).getText();
        return "";
    }

    private String getComboBoxValue(String key, String prop) {
        Object o = allComs.get(key);
        if (o instanceof JComboBox) {
            String itemName = ((JComboBox<?>) o).getSelectedItem().toString();
            return contactWindow.getPropValue(prop, itemName);
        }
        return "";
    }
}
