package me.kekemao;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.prefs.Preferences;

public class LoginGUI extends JFrame {
    private static final String LOGIN_TEXT = "登 录";
    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginGUI.class);
    private static final String PREF_LAST_USERNAME = "lastUsername";

    private LoginGUI loginGUI;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;

    public LoginGUI() {
        loginGUI = this;

        // 窗口设置
        setTitle("通讯录管理系统");
        setSize(420, 520);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(Theme.BG);

        // 主面板 - 垂直居中布局
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(0, 0));
        mainPanel.setBackground(Theme.BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 40, 0, 40));
        setContentPane(mainPanel);

        // ====== 顶部区域 ======
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(Theme.BG);
        topPanel.setBorder(BorderFactory.createEmptyBorder(40, 0, 20, 0));

        JLabel iconLabel = new JLabel("📇");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        topPanel.add(iconLabel);

        topPanel.add(Box.createVerticalStrut(12));

        JLabel titleLabel = new JLabel("通讯录管理系统");
        titleLabel.setFont(Theme.FONT_TITLE);
        titleLabel.setForeground(Theme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        topPanel.add(titleLabel);

        topPanel.add(Box.createVerticalStrut(4));

        JLabel subtitleLabel = new JLabel("登录您的账号以继续");
        subtitleLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        subtitleLabel.setForeground(Theme.TEXT_SECONDARY);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        topPanel.add(subtitleLabel);

        mainPanel.add(topPanel, BorderLayout.NORTH);

        // ====== 卡片区域 ======
        JPanel cardPanel = Theme.createCardPanel();
        cardPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 14, 0);
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        gbc.gridy = 0;
        JLabel usernameLabel = new JLabel("账号");
        usernameLabel.setFont(Theme.FONT_BOLD);
        usernameLabel.setForeground(Theme.TEXT_PRIMARY);
        cardPanel.add(usernameLabel, gbc);

        gbc.gridy = 1;
        usernameField = Theme.createTextField("请输入账号");
        usernameField.setPreferredSize(new Dimension(300, 38));
        cardPanel.add(usernameField, gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(8, 0, 14, 0);
        JLabel passwordLabel = new JLabel("密码");
        passwordLabel.setFont(Theme.FONT_BOLD);
        passwordLabel.setForeground(Theme.TEXT_PRIMARY);
        cardPanel.add(passwordLabel, gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 20, 0);
        passwordField = new JPasswordField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getPassword().length == 0 && !hasFocus()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.TEXT_HINT);
                    g2.setFont(Theme.FONT_DEFAULT);
                    Insets insets = getInsets();
                    g2.drawString("请输入密码", insets.left + 4, getHeight() - insets.bottom - 4);
                    g2.dispose();
                }
            }
        };
        passwordField.setFont(Theme.FONT_DEFAULT);
        passwordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        passwordField.setBackground(Theme.CARD_BG);
        passwordField.setEchoChar('●');
        passwordField.setPreferredSize(new Dimension(300, 38));
        passwordField.setSelectionColor(Theme.PRIMARY);
        passwordField.setSelectedTextColor(Color.WHITE);
        cardPanel.add(passwordField, gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(0, 0, 0, 0);
        loginButton = Theme.createPrimaryButton(LOGIN_TEXT);
        loginButton.setPreferredSize(new Dimension(300, 42));
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardPanel.add(loginButton, gbc);

        mainPanel.add(cardPanel, BorderLayout.CENTER);

        // ====== 底部信息 ======
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(Theme.BG);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 12, 0));
        JLabel footerLabel = new JLabel("默认账号 " + Utils.DEFAULT_ADMIN_USERNAME);
        footerLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        footerLabel.setForeground(Theme.TEXT_HINT);
        footerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottomPanel.add(footerLabel);
        bottomPanel.add(Box.createVerticalStrut(2));
        JLabel dataLabel = new JLabel("📁 数据存储：" + Utils.getDataDir().replace("\\", "/"));
        dataLabel.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        dataLabel.setForeground(new Color(180, 180, 185));
        dataLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottomPanel.add(dataLabel);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // ====== 事件绑定 ======
        loginButton.addActionListener(e -> doLogin());
        // 记住上次登录的账号，首次使用时预填默认账号
        String lastUsername = PREFS.get(PREF_LAST_USERNAME, Utils.DEFAULT_ADMIN_USERNAME);
        usernameField.setText(lastUsername);

        getRootPane().setDefaultButton(loginButton);

        usernameField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                usernameField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.PRIMARY),
                        BorderFactory.createEmptyBorder(7, 10, 7, 10)
                ));
            }
            @Override
            public void focusLost(FocusEvent e) {
                usernameField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDER),
                        BorderFactory.createEmptyBorder(7, 10, 7, 10)
                ));
            }
        });
        passwordField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                passwordField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.PRIMARY),
                        BorderFactory.createEmptyBorder(7, 10, 7, 10)
                ));
            }
            @Override
            public void focusLost(FocusEvent e) {
                passwordField.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDER),
                        BorderFactory.createEmptyBorder(7, 10, 7, 10)
                ));
            }
        });

        this.setVisible(true);
        // 记住了上次的账号时，直接把焦点放到密码框
        JComponent initialFocus = Utils.isBlank(lastUsername) ? usernameField : passwordField;
        SwingUtilities.invokeLater(initialFocus::requestFocusInWindow);
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        if (username.isEmpty()) {
            Theme.showMessage(loginGUI, "请输入账号", -1);
            usernameField.requestFocusInWindow();
            return;
        }
        if (password.length == 0) {
            Theme.showMessage(loginGUI, "请输入密码", -1);
            passwordField.requestFocusInWindow();
            return;
        }

        setInputsEnabled(false);
        loginButton.setText("登录中…");
        // 密码哈希校验较耗时，放到后台线程，避免界面卡住
        new SwingWorker<AccountService.LoginResult, Void>() {
            @Override
            protected AccountService.LoginResult doInBackground() {
                return AccountService.login(username, password);
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
                    loginButton.setText(LOGIN_TEXT);
                    Theme.showMessage(loginGUI, "登录时发生错误：" + ex.getMessage(), -1);
                    return;
                }
                onLoginResult(username, result);
            }
        }.execute();
    }

    private void onLoginResult(String username, AccountService.LoginResult result) {
        if (result.isSuccess()) {
            PREFS.put(PREF_LAST_USERNAME, username);
            loginButton.setText("登录成功 ✓");
            Timer t = new Timer(400, ev -> {
                loginGUI.dispose();
                ContactWindow contactWindow = new ContactWindow();
                if (result.usingDefaultPassword) {
                    new ChangePasswordDialog(contactWindow, "当前仍在使用默认密码，建议立即修改").showDialog();
                }
            });
            t.setRepeats(false);
            t.start();
            return;
        }

        passwordField.setText("");
        if (result.status == AccountService.Status.LOCKED) {
            startLockCountdown(result.lockSeconds);
            Theme.showMessage(loginGUI, result.message, 1);
            return;
        }
        setInputsEnabled(true);
        loginButton.setText(LOGIN_TEXT);
        shake(result.message);
    }

    private void startLockCountdown(int seconds) {
        setInputsEnabled(false);
        final int[] remaining = {Math.max(1, seconds)};
        loginButton.setText("请等待 " + remaining[0] + " 秒");
        Timer countdown = new Timer(1000, null);
        countdown.addActionListener(e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                countdown.stop();
                setInputsEnabled(true);
                loginButton.setText(LOGIN_TEXT);
                passwordField.requestFocusInWindow();
            } else {
                loginButton.setText("请等待 " + remaining[0] + " 秒");
            }
        });
        countdown.start();
    }

    private void setInputsEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
    }

    /** 登录失败：提示并抖动窗口，保留账号，聚焦密码框 */
    private void shake(String msg) {
        Theme.showMessage(loginGUI, msg, -1);
        passwordField.requestFocusInWindow();
        int x = loginGUI.getLocation().x;
        int y = loginGUI.getLocation().y;
        int[] offsets = {-4, 4, -3, 3, -2, 2, -1, 1, 0};
        Timer shake = new Timer(30, e -> {
            int idx = 0;
            for (int off : offsets) {
                idx++;
                Timer t = new Timer(30 * idx, ev -> loginGUI.setLocation(x + off, y));
                t.setRepeats(false);
                t.start();
            }
        });
        shake.setRepeats(false);
        shake.start();
    }
}
