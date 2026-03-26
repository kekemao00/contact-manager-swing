package com.txl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactEditWindow extends JFrame {
    private ContactEditWindow contactEditWindow;
    private ContactWindow contactWindow;
    private final Map<String, Object> allComs = new HashMap<>();

    public ContactEditWindow(ContactWindow contactWindowTemp, Map<String, Object> map) {
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                focusParentWindow(false);
            }
        });
        this.contactEditWindow = this;
        this.contactWindow = contactWindowTemp;
        boolean isEdit = map != null;
        this.setTitle(isEdit ? "编辑联系人" : "新增联系人");
        this.setResizable(false);
        this.setSize(560, 540);
        this.getContentPane().setBackground(Theme.BG);

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

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 18));
        headerRight.setBackground(Theme.PRIMARY);
        headerRight.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));
        JLabel tipLabel = new JLabel("* 为必填项");
        tipLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        tipLabel.setForeground(new Color(180, 210, 255));
        headerRight.add(tipLabel);
        headerPanel.add(headerRight, BorderLayout.EAST);

        JPanel formPanel = Theme.createCardPanel();
        formPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        String[][] fields = {
                {"c_name", "姓名", "请输入姓名"},
                {"c_nickname", "分类", null},
                {"c_phone", "电话", "请输入电话"},
                {"c_email", "邮箱", "请输入邮箱"},
                {"c_address", "地址", "请输入地址"},
                {"c_company", "公司", "请输入公司"},
                {"c_job_title", "岗位", "请输入岗位"},
                {"notes", "备注", "请输入备注"}
        };

        JTextField c_id_textField = new JTextField();
        c_id_textField.setVisible(false);
        formPanel.add(c_id_textField, new GridBagConstraints());
        allComs.put("c_id_textField", c_id_textField);

        int row = 0;
        List<Component> focusOrder = new ArrayList<>();

        for (String[] field : fields) {
            String key = field[0];
            String label = field[1];
            String placeholder = field[2];
            boolean required = "c_name".equals(key);

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

            gbc.gridx = 1;
            gbc.weightx = 1.0;

            if ("c_nickname".equals(key)) {
                JComboBox<String> cb = createCategoryComboBox();
                formPanel.add(cb, gbc);
                allComs.put(key + "_comboBox", cb);
                focusOrder.add(cb);
            } else {
                JTextField tf = Theme.createTextField(placeholder != null ? placeholder : "");
                tf.setPreferredSize(new Dimension(0, 34));
                formPanel.add(tf, gbc);
                allComs.put(key + "_textField", tf);
                focusOrder.add(tf);

                tf.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        tf.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(Theme.PRIMARY),
                                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
                    }

                    @Override
                    public void focusLost(FocusEvent e) {
                        tf.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(Theme.BORDER),
                                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
                    }
                });
            }
            row++;
        }

        FocusTraversalPolicy ftp = new FocusTraversalPolicy() {
            @Override
            public Component getComponentAfter(Container aContainer, Component aComponent) {
                int idx = focusOrder.indexOf(aComponent);
                return focusOrder.get((idx + 1) % focusOrder.size());
            }

            @Override
            public Component getComponentBefore(Container aContainer, Component aComponent) {
                int idx = focusOrder.indexOf(aComponent);
                return focusOrder.get((idx - 1 + focusOrder.size()) % focusOrder.size());
            }

            @Override
            public Component getFirstComponent(Container aContainer) {
                return focusOrder.get(0);
            }

            @Override
            public Component getLastComponent(Container aContainer) {
                return focusOrder.get(focusOrder.size() - 1);
            }

            @Override
            public Component getDefaultComponent(Container aContainer) {
                return focusOrder.get(0);
            }
        };
        formPanel.setFocusTraversalPolicyProvider(true);
        formPanel.setFocusTraversalPolicy(ftp);

        if (isEdit) {
            fillField("c_id_textField", map.get("c_id"));
            fillField("c_name_textField", map.get("c_name"));
            fillField("c_phone_textField", map.get("c_phone"));
            fillField("c_email_textField", map.get("c_email"));
            fillField("c_address_textField", map.get("c_address"));
            fillField("c_company_textField", map.get("c_company"));
            fillField("c_job_title_textField", map.get("c_job_title"));
            fillField("notes_textField", map.get("notes"));
            if (!Utils.isNull(map.get("c_nickname"))) {
                setComboBoxValue("c_nickname_comboBox", map.get("c_nickname").toString());
            }
        }

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttonPanel.setBackground(Theme.BG);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JButton cancelButton = Theme.createFlatButton("取 消");
        cancelButton.setPreferredSize(new Dimension(100, 38));
        buttonPanel.add(cancelButton);

        JButton submitButton = Theme.createPrimaryButton(isEdit ? "保 存" : "添 加");
        submitButton.setPreferredSize(new Dimension(100, 38));
        buttonPanel.add(submitButton);

        cancelButton.addActionListener(e -> closeWindow(false));

        getRootPane().registerKeyboardAction(e -> closeWindow(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        getRootPane().setDefaultButton(submitButton);

        submitButton.addActionListener(e -> {
            String cIdValue = getTextField("c_id_textField");
            String cNameValue = getTextField("c_name_textField");
            if (cNameValue.trim().isEmpty()) {
                Theme.showMessage(formPanel, "请输入姓名！", -1);
                focusOrder.get(0).requestFocus();
                return;
            }

            String cNicknameValue = getComboBoxValue("c_nickname_comboBox", "c_nickname");
            String cPhoneValue = getTextField("c_phone_textField");
            String cEmailValue = getTextField("c_email_textField");
            String cAddressValue = getTextField("c_address_textField");
            String cCompanyValue = getTextField("c_company_textField");
            String cJobTitleValue = getTextField("c_job_title_textField");
            String notesValue = getTextField("notes_textField");

            cNameValue = cNameValue.replace("'", "''");
            cNicknameValue = cNicknameValue.replace("'", "''");
            cPhoneValue = cPhoneValue.replace("'", "''");
            cEmailValue = cEmailValue.replace("'", "''");
            cAddressValue = cAddressValue.replace("'", "''");
            cCompanyValue = cCompanyValue.replace("'", "''");
            cJobTitleValue = cJobTitleValue.replace("'", "''");
            notesValue = notesValue.replace("'", "''");

            try {
                Statement statement = Utils.getStatement();
                int affected;
                if (!isEdit) {
                    String sql = "insert into contact(c_name,c_nickname,c_phone,c_email,c_address,c_company,c_job_title,notes) "
                            + "values('" + cNameValue + "','" + cNicknameValue + "','" + cPhoneValue + "','"
                            + cEmailValue + "','" + cAddressValue + "','" + cCompanyValue + "','"
                            + cJobTitleValue + "','" + notesValue + "')";
                    affected = statement.executeUpdate(sql);
                } else {
                    String sql = "update contact set c_name='" + cNameValue + "',c_nickname='" + cNicknameValue
                            + "',c_phone='" + cPhoneValue + "',c_email='" + cEmailValue + "',c_address='"
                            + cAddressValue + "',c_company='" + cCompanyValue + "',c_job_title='"
                            + cJobTitleValue + "',notes='" + notesValue + "' where c_id='" + cIdValue + "'";
                    affected = statement.executeUpdate(sql);
                }
                if (affected > 0) {
                    closeWindow(true);
                } else {
                    Theme.showMessage(formPanel, isEdit ? "修改失败" : "添加失败", -1);
                }
            } catch (Exception ec) {
                ec.printStackTrace();
                Theme.showMessage(formPanel, "操作失败：" + ec.getMessage(), -1);
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Theme.BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        this.setLocationRelativeTo(contactWindow);
        this.setVisible(true);
        this.toFront();
        SwingUtilities.invokeLater(() -> focusOrder.get(0).requestFocusInWindow());
    }

    private void closeWindow(boolean refreshParent) {
        contactEditWindow.dispose();
        if (refreshParent) {
            contactWindow.showWindow();
        }
    }

    private void focusParentWindow(boolean refreshParent) {
        if (refreshParent) {
            contactWindow.showWindow();
        }
        contactWindow.toFront();
        contactWindow.requestFocus();
    }

    private JComboBox<String> createCategoryComboBox() {
        JComboBox<String> cb = Theme.createComboBox(Utils.getDefaultContactCategories());
        cb.setEditable(true);
        cb.setSelectedIndex(0);
        cb.setPreferredSize(new Dimension(0, 34));
        Component editor = cb.getEditor().getEditorComponent();
        if (editor instanceof JTextField) {
            JTextField tf = (JTextField) editor;
            tf.setFont(Theme.FONT_DEFAULT);
            tf.setBorder(BorderFactory.createEmptyBorder());
        }
        return cb;
    }

    private void fillField(String key, Object value) {
        if (!Utils.isNull(value)) {
            Object comp = allComs.get(key);
            if (comp instanceof JTextField) {
                ((JTextField) comp).setText(value.toString());
            }
        }
    }

    private void setComboBoxValue(String key, String value) {
        Object o = allComs.get(key);
        if (o instanceof JComboBox) {
            ((JComboBox<?>) o).setSelectedItem(value);
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
            JComboBox<?> comboBox = (JComboBox<?>) o;
            Object item = comboBox.isEditable() ? comboBox.getEditor().getItem() : comboBox.getSelectedItem();
            String itemName = item == null ? "" : item.toString();
            return contactWindow.getPropValue(prop, itemName);
        }
        return "";
    }
}
