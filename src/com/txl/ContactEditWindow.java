package com.txl;

import java.util.*;
import javax.swing.*;
import java.text.SimpleDateFormat;
import java.sql.ResultSet;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.sql.Statement;

public class ContactEditWindow extends JFrame {
    private ContactEditWindow contactEditWindow;
    private ContactWindow contactWindow;
    private Map<String, Object> allComs = new HashMap<>();
    private static JLabel keyLabel;
    private static JTextField keyTextField;

    public ContactEditWindow(ContactWindow contactWindowTemp, Map<String, Object> map) {
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.contactEditWindow = this;
        this.contactWindow = contactWindowTemp;
        this.setTitle(map == null ? "新增联系人" : "编辑联系人");
        this.setResizable(false);
        this.setSize(480, 580);
        this.getContentPane().setBackground(Theme.BG);

        // ====== 标题栏 ======
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Theme.PRIMARY);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 14));
        headerLeft.setBackground(Theme.PRIMARY);
        headerLeft.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        JLabel headerIcon = new JLabel(map == null ? "➕" : "✏️");
        headerIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        headerLeft.add(headerIcon);
        headerLeft.add(Box.createHorizontalStrut(8));
        JLabel headerTitle = new JLabel(map == null ? "新增联系人" : "编辑联系人");
        headerTitle.setFont(new Font("微软雅黑", Font.BOLD, 15));
        headerTitle.setForeground(Color.WHITE);
        headerLeft.add(headerTitle);
        headerPanel.add(headerLeft, BorderLayout.WEST);

        // ====== 表单区域 ======
        JPanel formPanel = Theme.createCardPanel();
        formPanel.setLayout(null);
        formPanel.setBounds(20, 60, 440, 430);
        formPanel.setPreferredSize(new Dimension(440, 430));

        int startY = 16;
        int labelW = 70;
        int fieldW = 340;
        int rowH = 36;
        int gap = 10;

        // 编号（隐藏）
        JTextField c_id_textField = new JTextField();
        Theme.setLocation(c_id_textField, 0, 0, 0, 0);
        c_id_textField.setVisible(false);
        formPanel.add(c_id_textField);
        allComs.put("c_id_textField", c_id_textField);
        keyLabel = new JLabel();
        keyTextField = c_id_textField;

        // 字段定义
        String[][] fields = {
                {"c_name",       "姓名",    "请输入姓名 *"},
                {"c_nickname",   "昵称",    "请输入昵称"},
                {"c_phone",      "电话",    "请输入电话"},
                {"c_email",      "邮箱",    "请输入邮箱"},
                {"c_address",    "地址",    "请输入地址"},
                {"c_birthday",   "生日",    "yyyy-MM-dd"},
                {"c_company",    "公司",    "请输入公司"},
                {"c_job_title",  "岗位",    "请输入岗位"},
                {"c_group_name", "分组",    null},
                {"notes",        "备注",    "请输入备注"},
        };

        for (int i = 0; i < fields.length; i++) {
            String key = fields[i][0];
            String label = fields[i][1];
            String placeholder = fields[i][2];
            int y = startY + i * (rowH + gap);

            // 标签（必填字段加星号）
            boolean required = key.equals("c_name");
            JLabel lbl = new JLabel(label + (required ? " *" : ""));
            lbl.setFont(Theme.FONT_BOLD);
            lbl.setForeground(Theme.TEXT_PRIMARY);
            lbl.setBounds(20, y + 8, labelW, 20);
            formPanel.add(lbl);

            // 输入控件
            if (key.equals("c_group_name")) {
                String[] options = {"家人", "亲戚", "朋友", "其他"};
                JComboBox<String> cb = Theme.createComboBox(options);
                Theme.setLocation(cb, 100, y, fieldW, rowH);
                formPanel.add(cb);
                allComs.put(key + "_comboBox", cb);
            } else {
                JTextField tf = Theme.createTextField(placeholder);
                Theme.setLocation(tf, 100, y, fieldW, rowH);
                formPanel.add(tf);
                allComs.put(key + "_textField", tf);

                // 焦点高亮
                tf.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        tf.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(Theme.PRIMARY),
                                BorderFactory.createEmptyBorder(7, 10, 7, 10)
                        ));
                    }
                    @Override
                    public void focusLost(FocusEvent e) {
                        tf.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(Theme.BORDER),
                                BorderFactory.createEmptyBorder(7, 10, 7, 10)
                        ));
                    }
                });
            }
        }

        // 编辑模式回填数据
        if (map != null) {
            fillField("c_id_textField", map.get("c_id"));
            fillField("c_name_textField", map.get("c_name"));
            fillField("c_nickname_textField", map.get("c_nickname"));
            fillField("c_phone_textField", map.get("c_phone"));
            fillField("c_email_textField", map.get("c_email"));
            fillField("c_address_textField", map.get("c_address"));
            fillField("c_birthday_textField", map.get("c_birthday"));
            fillField("c_company_textField", map.get("c_company"));
            fillField("c_job_title_textField", map.get("c_job_title"));
            fillField("notes_textField", map.get("notes"));
            if (!Utils.isNull(map.get("c_group_name"))) {
                JComboBox<String> cb = (JComboBox<String>) allComs.get("c_group_name_comboBox");
                cb.setSelectedItem(contactWindow.getProp("c_group_name", map.get("c_group_name").toString()));
            }
        }

        // ====== 底部按钮栏 ======
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttonPanel.setBackground(Theme.BG);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JButton cancer_button = Theme.createFlatButton("取 消");
        cancer_button.setPreferredSize(new Dimension(100, 38));
        buttonPanel.add(cancer_button);

        JButton submit_button = Theme.createPrimaryButton("保 存");
        submit_button.setPreferredSize(new Dimension(100, 38));
        buttonPanel.add(submit_button);

        // 取消
        cancer_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contactEditWindow.dispose();
                contactWindow.showWindow();
            }
        });

        // 保存
        submit_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String c_id_value = ((JTextField) allComs.get("c_id_textField")).getText();
                String c_name_value = getTextField("c_name_textField");
                if ("".equals(c_name_value.trim())) {
                    Theme.showMessage(formPanel, "请输入姓名！", -1);
                    return;
                }
                String c_nickname_value = getTextField("c_nickname_textField");
                String c_phone_value = getTextField("c_phone_textField");
                String c_email_value = getTextField("c_email_textField");
                String c_address_value = getTextField("c_address_textField");
                String c_birthday_value = getTextField("c_birthday_textField");
                String c_company_value = getTextField("c_company_textField");
                String c_job_title_value = getTextField("c_job_title_textField");
                String c_group_name_value = getComboBoxValue("c_group_name_comboBox", "c_group_name");
                String notes_value = getTextField("notes_textField");

                if (!Utils.isBlank(c_id_value)) {
                    try {
                        Integer.parseInt(c_id_value);
                    } catch (Exception et) {
                        Theme.showMessage(formPanel, "编号校验失败", -1);
                        return;
                    }
                }
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

                Statement statement = Utils.getStatement();
                try {
                    int row;
                    if (map == null) {
                        String sql = "insert into contact(c_name,c_nickname,c_phone,c_email,c_address,c_birthday,c_company,c_job_title,c_group_name,notes) "
                                + "values('" + c_name_value + "','" + c_nickname_value + "','" + c_phone_value + "','"
                                + c_email_value + "','" + c_address_value + "','" + c_birthday_value + "','"
                                + c_company_value + "','" + c_job_title_value + "','" + c_group_name_value + "','" + notes_value + "')";
                        row = statement.executeUpdate(sql);
                        Theme.showMessage(formPanel, row > 0 ? "添加成功" : "添加失败", row > 0 ? 0 : -1);
                    } else {
                        String sql = "update contact set c_name='" + c_name_value + "', c_nickname='" + c_nickname_value
                                + "', c_phone='" + c_phone_value + "', c_email='" + c_email_value + "', c_address='"
                                + c_address_value + "', c_birthday='" + c_birthday_value + "', c_company='"
                                + c_company_value + "', c_job_title='" + c_job_title_value + "', c_group_name='"
                                + c_group_name_value + "', notes='" + notes_value + "' where c_id='" + c_id_value + "'";
                        row = statement.executeUpdate(sql);
                        Theme.showMessage(formPanel, row > 0 ? "修改成功" : "修改失败", row > 0 ? 0 : -1);
                    }
                } catch (Exception ec) {
                    ec.printStackTrace();
                    Theme.showMessage(formPanel, "操作失败：" + ec.getMessage(), -1);
                }
                contactEditWindow.dispose();
                contactWindow.showWindow();
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
    }

    private void fillField(String key, Object value) {
        if (!Utils.isNull(value)) {
            Object comp = allComs.get(key);
            if (comp instanceof JTextField) {
                ((JTextField) comp).setText(value.toString());
            }
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
