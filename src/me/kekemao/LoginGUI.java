package me.kekemao;

import me.kekemao.ui.Fade;
import me.kekemao.ui.Fonts;
import me.kekemao.ui.Glyph;
import me.kekemao.ui.LogoMark;
import me.kekemao.ui.MorphButton;
import me.kekemao.ui.Palette;
import me.kekemao.ui.Text;
import me.kekemao.ui.TextInput;
import me.kekemao.ui.TextView;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.prefs.Preferences;

public class LoginGUI extends JFrame {
    private static final String LOGIN_TEXT = "登录";
    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginGUI.class);
    private static final String PREF_LAST_USERNAME = "lastUsername";
    /** 文字与输入框的可见边框左对齐 */
    private static final javax.swing.border.Border INDENT =
            BorderFactory.createEmptyBorder(0, TextInput.RING, 0, TextInput.RING);

    private final TextInput usernameInput;
    private final TextInput passwordInput;
    private final MorphButton loginButton;
    private Timer lockCountdown;

    public LoginGUI() {
        setTitle("通讯录");
        Theme.decorate(this);
        setSize(420, 580);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Palette.CANVAS);
        setContentPane(root);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setPreferredSize(new Dimension(320, 470));

        JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, TextInput.RING, 0));
        logoRow.setOpaque(false);
        logoRow.add(new LogoMark(48));
        logoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        column.add(logoRow);
        column.add(Box.createVerticalStrut(22));

        TextView title = new TextView("欢迎回来", Text.HEADLINE, Palette.INK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(INDENT);
        column.add(title);
        column.add(Box.createVerticalStrut(4));
        TextView subtitle = new TextView("登录通讯录以继续", Text.BODY, Palette.INK_2);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(INDENT);
        column.add(subtitle);
        column.add(Box.createVerticalStrut(30));

        column.add(fieldLabel("账号"));
        column.add(Box.createVerticalStrut(6));
        usernameInput = new TextInput("请输入账号", Glyph.USER);
        usernameInput.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(usernameInput);
        column.add(Box.createVerticalStrut(14));

        column.add(fieldLabel("密码"));
        column.add(Box.createVerticalStrut(6));
        passwordInput = TextInput.password("请输入密码");
        passwordInput.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(passwordInput);
        column.add(Box.createVerticalStrut(24));

        loginButton = new MorphButton(LOGIN_TEXT);
        loginButton.setPreferredSize(new Dimension(320 - TextInput.RING * 2, 44));
        // 与输入框的可见边框对齐（输入框外侧留了焦点环的位置）
        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setOpaque(false);
        buttonRow.setBorder(BorderFactory.createEmptyBorder(0, TextInput.RING, 0, TextInput.RING));
        buttonRow.add(loginButton, BorderLayout.CENTER);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        column.add(buttonRow);

        column.add(Box.createVerticalGlue());
        TextView hint = new TextView("默认账号 " + Utils.DEFAULT_ADMIN_USERNAME + "，首次登录后请修改密码",
                Text.SMALL, Palette.INK_3);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(INDENT);
        column.add(hint);
        column.add(Box.createVerticalStrut(2));
        TextView data = new TextView(Utils.getDataDir().replace("\\", "/"), Text.SMALL, Palette.INK_3).glyph(Glyph.FOLDER);
        data.setAlignmentX(Component.LEFT_ALIGNMENT);
        data.setBorder(INDENT);
        data.setToolTipText("数据存储位置");
        column.add(data);
        root.add(column);

        loginButton.addActionListener(e -> doLogin());
        getRootPane().setDefaultButton(loginButton);

        // 记住上次登录的账号，首次使用时预填默认账号
        String lastUsername = PREFS.get(PREF_LAST_USERNAME, Utils.DEFAULT_ADMIN_USERNAME);
        usernameInput.setText(lastUsername);

        setVisible(true);
        Fade.in(getRootPane());
        // 记住了上次的账号时，直接把焦点放到密码框
        JComponent initialFocus = Utils.isBlank(lastUsername) ? usernameInput.field() : passwordInput.field();
        SwingUtilities.invokeLater(initialFocus::requestFocusInWindow);
    }

    private static Component fieldLabel(String text) {
        TextView label = new TextView(text, new Text.Style(12, Fonts.Weight.MEDIUM), Palette.INK_2);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(INDENT);
        return label;
    }

    private void doLogin() {
        if (loginButton.getState() == MorphButton.State.LOADING || loginButton.getState() == MorphButton.State.SUCCESS) {
            return;
        }
        String username = usernameInput.getText().trim();
        char[] password = ((JPasswordField) passwordInput.field()).getPassword();
        if (username.isEmpty()) {
            usernameInput.flagError();
            Theme.showMessage(this, "请输入账号", Theme.MESSAGE_ERROR);
            usernameInput.field().requestFocusInWindow();
            return;
        }
        if (password.length == 0) {
            passwordInput.flagError();
            Theme.showMessage(this, "请输入密码", Theme.MESSAGE_ERROR);
            passwordInput.field().requestFocusInWindow();
            return;
        }

        setInputsEnabled(false);
        loginButton.loading();
        long started = System.currentTimeMillis();
        // 密码哈希校验较耗时，放到后台线程，避免界面卡住
        new SwingWorker<AccountService.LoginResult, Void>() {
            @Override
            protected AccountService.LoginResult doInBackground() throws Exception {
                AccountService.LoginResult result = AccountService.login(username, password);
                // 让加载状态至少完整出现一下，避免一闪而过
                long wait = 420 - (System.currentTimeMillis() - started);
                if (wait > 0) Thread.sleep(wait);
                return result;
            }

            @Override
            protected void done() {
                Arrays.fill(password, '\0');
                AccountService.LoginResult result;
                try {
                    result = get();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    setInputsEnabled(true);
                    loginButton.error("登录出错");
                    Theme.showMessage(LoginGUI.this, "登录时发生错误：" + ex.getMessage(), Theme.MESSAGE_ERROR);
                    return;
                }
                onLoginResult(username, result);
            }
        }.execute();
    }

    private void onLoginResult(String username, AccountService.LoginResult result) {
        if (result.isSuccess()) {
            PREFS.put(PREF_LAST_USERNAME, username);
            loginButton.success();
            Timer t = new Timer(650, ev -> {
                dispose();
                ContactWindow contactWindow = new ContactWindow();
                if (result.usingDefaultPassword) {
                    SwingUtilities.invokeLater(() ->
                            ChangePasswordSheet.open(contactWindow, "当前仍在使用默认密码，建议立即修改"));
                }
            });
            t.setRepeats(false);
            t.start();
            return;
        }

        passwordInput.setText("");
        if (result.status == AccountService.Status.LOCKED) {
            loginButton.idle();
            startLockCountdown(result.lockSeconds);
            Theme.showMessage(this, result.message, Theme.MESSAGE_WARNING);
            return;
        }
        setInputsEnabled(true);
        loginButton.error(result.message);
        passwordInput.flagError();
        passwordInput.field().requestFocusInWindow();
    }

    private void startLockCountdown(int seconds) {
        setInputsEnabled(false);
        loginButton.setEnabled(false);
        final int[] remaining = {Math.max(1, seconds)};
        loginButton.setIdleText("请等待 " + remaining[0] + " 秒");
        if (lockCountdown != null) lockCountdown.stop();
        lockCountdown = new Timer(1000, null);
        lockCountdown.addActionListener(e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                lockCountdown.stop();
                setInputsEnabled(true);
                loginButton.setEnabled(true);
                loginButton.setIdleText(LOGIN_TEXT);
                passwordInput.field().requestFocusInWindow();
            } else {
                loginButton.setIdleText("请等待 " + remaining[0] + " 秒");
            }
        });
        lockCountdown.start();
    }

    private void setInputsEnabled(boolean enabled) {
        usernameInput.field().setEnabled(enabled);
        passwordInput.field().setEnabled(enabled);
    }

    @Override
    public void dispose() {
        if (lockCountdown != null) lockCountdown.stop();
        super.dispose();
    }
}
