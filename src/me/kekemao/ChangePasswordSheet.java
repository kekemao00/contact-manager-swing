package me.kekemao;

import me.kekemao.ui.FlatButton;
import me.kekemao.ui.Fonts;
import me.kekemao.ui.Palette;
import me.kekemao.ui.Sheet;
import me.kekemao.ui.SheetLayout;
import me.kekemao.ui.Text;
import me.kekemao.ui.TextInput;
import me.kekemao.ui.TextView;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/** 修改密码面板：在主窗口里从“修改密码”入口展开。 */
public final class ChangePasswordSheet {
    private final TextInput oldInput = TextInput.password("原密码");
    private final TextInput newInput = TextInput.password("新密码，至少 " + AccountService.MIN_PASSWORD_LENGTH + " 位");
    private final TextInput confirmInput = TextInput.password("再输入一次新密码");
    private final Component anchor;
    private Sheet sheet;

    private ChangePasswordSheet(Component anchor) {
        this.anchor = anchor;
    }

    /**
     * @param reminder 非空时在顶部展示提醒（例如仍在使用默认密码）
     */
    public static void open(Component anchor, String reminder) {
        new ChangePasswordSheet(anchor).show(reminder);
    }

    private void show(String reminder) {
        SheetLayout layout = new SheetLayout("修改密码", "修改后下次登录请使用新密码");
        JPanel body = layout.body();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        if (reminder != null) {
            TextView r = new TextView(reminder, new Text.Style(12.5f, Fonts.Weight.MEDIUM), Palette.DANGER);
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(r);
            body.add(Box.createVerticalStrut(12));
        }
        for (TextInput input : new TextInput[]{oldInput, newInput, confirmInput}) {
            input.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(input);
            body.add(Box.createVerticalStrut(10));
        }

        FlatButton cancel = new FlatButton(reminder != null ? "稍后再说" : "取消", FlatButton.Kind.SECONDARY);
        FlatButton submit = new FlatButton("确认修改", FlatButton.Kind.PRIMARY);
        cancel.setFixedHeight(36);
        submit.setFixedHeight(36);
        layout.addFooter(cancel);
        layout.addFooter(submit);

        int height = reminder != null ? 360 : 330;
        sheet = Sheet.open(anchor, layout, new Dimension(420, height));
        layout.onClose(sheet::close);
        cancel.addActionListener(e -> sheet.close());
        submit.addActionListener(e -> submit());
        for (TextInput input : new TextInput[]{oldInput, newInput, confirmInput}) {
            input.field().addActionListener(e -> submit());
        }
        SwingUtilities.invokeLater(() -> oldInput.field().requestFocusInWindow());
    }

    private void submit() {
        char[] oldPassword = ((JPasswordField) oldInput.field()).getPassword();
        char[] newPassword = ((JPasswordField) newInput.field()).getPassword();
        char[] confirmPassword = ((JPasswordField) confirmInput.field()).getPassword();
        try {
            String error = AccountService.changePassword(oldPassword, newPassword, confirmPassword);
            if (error != null) {
                Theme.showMessage(anchor, error, Theme.MESSAGE_ERROR);
                TextInput target = error.startsWith("原密码") || error.equals("请输入原密码") ? oldInput : newInput;
                target.flagError();
                target.field().selectAll();
                target.field().requestFocusInWindow();
                return;
            }
            sheet.close();
            Theme.showMessage(anchor, "密码已修改，下次登录请使用新密码", Theme.MESSAGE_SUCCESS);
        } finally {
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
            Arrays.fill(confirmPassword, '\0');
        }
    }
}
