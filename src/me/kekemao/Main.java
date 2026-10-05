package me.kekemao;

import javax.swing.*;
import java.util.Arrays;

public class Main {
    /** 忘记密码时使用：java ... me.kekemao.Main --reset-password */
    private static final String RESET_PASSWORD_ARG = "--reset-password";

    public static void main(String[] args) {
        boolean resetPassword = Arrays.asList(args).contains(RESET_PASSWORD_ARG);
        SwingUtilities.invokeLater(() -> {
            Theme.init();
            String initError = Utils.getInitError();
            if (initError != null) {
                Theme.showMessage(null, "数据库初始化失败，程序无法启动：" + initError
                        + "\n数据目录：" + Utils.getDataDir(), -1);
                System.exit(1);
                return;
            }
            if (resetPassword) {
                if (AccountService.resetAdminPassword()) {
                    Theme.showMessage(null, "管理员 " + Utils.DEFAULT_ADMIN_USERNAME + " 的密码已重置为默认密码，请登录后尽快修改", 1);
                } else {
                    Theme.showMessage(null, "重置密码失败", -1);
                }
            }
            new LoginGUI();
        });
    }
}
