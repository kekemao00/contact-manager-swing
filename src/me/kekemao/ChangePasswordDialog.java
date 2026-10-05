package me.kekemao;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Arrays;

/** 修改密码弹窗 */
public class ChangePasswordDialog extends JDialog {
    private final JPasswordField oldPasswordField = createPasswordField();
    private final JPasswordField newPasswordField = createPasswordField();
    private final JPasswordField confirmPasswordField = createPasswordField();
    private boolean changed;

    /**
     * @param reminder 非空时在顶部展示提醒（例如仍在使用默认密码）
     */
    public ChangePasswordDialog(Window owner, String reminder) {
        super(owner, "修改密码", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel cardPanel = Theme.createCardPanel();
        cardPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.gridy = 0;

        if (reminder != null) {
            JLabel reminderLabel = new JLabel(reminder);
            reminderLabel.setFont(Theme.FONT_DEFAULT);
            reminderLabel.setForeground(Theme.DANGER);
            gbc.insets = new Insets(0, 0, 12, 0);
            cardPanel.add(reminderLabel, gbc);
            gbc.gridy++;
        }

        addRow(cardPanel, gbc, "原密码", oldPasswordField);
        addRow(cardPanel, gbc, "新密码（至少 " + AccountService.MIN_PASSWORD_LENGTH + " 位）", newPasswordField);
        addRow(cardPanel, gbc, "确认新密码", confirmPasswordField);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttonPanel.setBackground(Theme.BG);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        JButton cancelButton = Theme.createFlatButton(reminder != null ? "稍后再说" : "取 消");
        cancelButton.setPreferredSize(new Dimension(100, 36));
        JButton submitButton = Theme.createPrimaryButton("确认修改");
        submitButton.setPreferredSize(new Dimension(100, 36));
        buttonPanel.add(cancelButton);
        buttonPanel.add(submitButton);

        cancelButton.addActionListener(e -> dispose());
        submitButton.addActionListener(e -> submit());
        getRootPane().setDefaultButton(submitButton);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Theme.BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        mainPanel.add(cardPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        pack();
        setSize(Math.max(getWidth(), 380), getHeight());
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(oldPasswordField::requestFocusInWindow);
    }

    /** 显示弹窗并阻塞直到关闭，返回密码是否修改成功 */
    public boolean showDialog() {
        setVisible(true);
        return changed;
    }

    private void submit() {
        char[] oldPassword = oldPasswordField.getPassword();
        char[] newPassword = newPasswordField.getPassword();
        char[] confirmPassword = confirmPasswordField.getPassword();
        try {
            String error = AccountService.changePassword(oldPassword, newPassword, confirmPassword);
            if (error != null) {
                Theme.showMessage(this, error, -1);
                if (error.startsWith("原密码") || error.equals("请输入原密码")) {
                    oldPasswordField.selectAll();
                    oldPasswordField.requestFocusInWindow();
                } else {
                    newPasswordField.selectAll();
                    newPasswordField.requestFocusInWindow();
                }
                return;
            }
            changed = true;
            Theme.showMessage(this, "密码修改成功，下次登录请使用新密码", 0);
            dispose();
        } finally {
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
            Arrays.fill(confirmPassword, '\0');
        }
    }

    private static void addRow(JPanel panel, GridBagConstraints gbc, String label, JPasswordField field) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(Theme.FONT_BOLD);
        lbl.setForeground(Theme.TEXT_PRIMARY);
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(lbl, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 12, 0);
        panel.add(field, gbc);
        gbc.gridy++;
    }

    private static JPasswordField createPasswordField() {
        JPasswordField field = new JPasswordField();
        field.setFont(Theme.FONT_DEFAULT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        field.setBackground(Theme.CARD_BG);
        field.setEchoChar('●');
        field.setPreferredSize(new Dimension(300, 36));
        return field;
    }
}
