package com.txl;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.plaf.metal.*;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * 全局主题 & 工具类
 * 扁平化现代风格：圆角、柔和配色、阴影、平滑交互
 */
public class Theme {

    // ========== 配色 ==========
    public static final Color PRIMARY       = new Color(66, 133, 244);    // 主色蓝
    public static final Color PRIMARY_HOVER  = new Color(48, 115, 225);
    public static final Color PRIMARY_PRESSED = new Color(30, 100, 200);
    public static final Color DANGER         = new Color(234, 67, 53);    // 红色
    public static final Color DANGER_HOVER   = new Color(210, 50, 40);
    public static final Color SUCCESS        = new Color(52, 168, 83);    // 绿色
    public static final Color WARNING        = new Color(251, 188, 4);    // 黄色
    public static final Color BG             = new Color(245, 247, 250);  // 背景浅灰
    public static final Color CARD_BG        = Color.WHITE;               // 卡片白色
    public static final Color TEXT_PRIMARY   = new Color(32, 33, 36);    // 主文字
    public static final Color TEXT_SECONDARY = new Color(95, 99, 104);   // 副文字
    public static final Color TEXT_HINT      = new Color(154, 160, 166);  // 提示文字
    public static final Color BORDER         = new Color(218, 220, 224);  // 边框
    public static final Color TABLE_HEADER   = new Color(241, 243, 244);  // 表头背景
    public static final Color TABLE_ALT_ROW  = new Color(248, 249, 250);  // 表格交替行
    public static final Color SHADOW         = new Color(0, 0, 0, 20);   // 阴影

    public static final int RADIUS = 8;       // 圆角半径
    public static final int RADIUS_SM = 5;
    public static final Font FONT_DEFAULT  = new Font("微软雅黑", Font.PLAIN, 13);
    public static final Font FONT_BOLD     = new Font("微软雅黑", Font.BOLD, 13);
    public static final Font FONT_TITLE    = new Font("微软雅黑", Font.BOLD, 18);
    public static final Font FONT_TABLE    = new Font("微软雅黑", Font.PLAIN, 12);
    public static final Font FONT_TABLE_HD = new Font("微软雅黑", Font.BOLD, 12);

    // ========== 全局初始化 ==========
    public static void init() {
        // 设置全局字体
        setUIFont(FONT_DEFAULT);

        // 设置 Look and Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // 覆盖默认颜色
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", CARD_BG);
        UIManager.put("OptionPane.messageFont", FONT_DEFAULT);
        UIManager.put("OptionPane.buttonFont", FONT_DEFAULT);
        UIManager.put("Button.font", FONT_DEFAULT);
        UIManager.put("Label.font", FONT_DEFAULT);
        UIManager.put("TextField.font", FONT_DEFAULT);
        UIManager.put("ComboBox.font", FONT_DEFAULT);
        UIManager.put("Table.font", FONT_TABLE);
        UIManager.put("Table.headerFont", FONT_TABLE_HD);
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        UIManager.put("Table.selectionBackground", new Color(232, 240, 254));
        UIManager.put("Table.selectionForeground", TEXT_PRIMARY);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollPane.background", CARD_BG);
    }

    private static void setUIFont(Font font) {
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof Font) {
                UIManager.put(key, font);
            }
        }
    }

    // ========== 组件工厂 ==========

    /** 创建圆角面板（带阴影效果） */
    public static JPanel createCardPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS, RADIUS);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        return panel;
    }

    /** 创建主色按钮 */
    public static JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(PRIMARY_PRESSED);
                } else if (getModel().isRollover()) {
                    g2.setColor(PRIMARY_HOVER);
                } else {
                    g2.setColor(PRIMARY);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS_SM, RADIUS_SM);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(Color.WHITE);
        btn.setFont(FONT_BOLD);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        return btn;
    }

    /** 创建危险操作按钮（红色） */
    public static JButton createDangerButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(DANGER_HOVER);
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(240, 80, 65));
                } else {
                    g2.setColor(DANGER);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS_SM, RADIUS_SM);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(Color.WHITE);
        btn.setFont(FONT_BOLD);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        return btn;
    }

    /** 创建普通按钮 */
    public static JButton createFlatButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(new Color(230, 230, 230));
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(240, 242, 245));
                } else {
                    g2.setColor(CARD_BG);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS_SM, RADIUS_SM);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS_SM, RADIUS_SM);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(TEXT_PRIMARY);
        btn.setFont(FONT_BOLD);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        return btn;
    }

    /** 创建搜索按钮（带图标） */
    public static JButton createSearchButton() {
        JButton btn = new JButton("🔍 查询") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(PRIMARY_PRESSED);
                } else if (getModel().isRollover()) {
                    g2.setColor(PRIMARY_HOVER);
                } else {
                    g2.setColor(PRIMARY);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS_SM, RADIUS_SM);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(Color.WHITE);
        btn.setFont(FONT_BOLD);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        return btn;
    }

    /** 创建输入框 */
    public static JTextField createTextField(String placeholder) {
        JTextField tf = new JTextField() {
            private String hint = placeholder;
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(TEXT_HINT);
                    g2.setFont(getFont());
                    Insets insets = getInsets();
                    g2.drawString(hint, insets.left + 4, getHeight() - insets.bottom - 4);
                    g2.dispose();
                }
            }
        };
        tf.setFont(FONT_DEFAULT);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        tf.setBackground(CARD_BG);
        tf.setSelectionColor(PRIMARY);
        tf.setSelectedTextColor(Color.WHITE);
        return tf;
    }

    /** 创建下拉框 */
    public static <T> JComboBox<T> createComboBox(T[] items) {
        JComboBox<T> cb = new JComboBox<>(items);
        cb.setFont(FONT_DEFAULT);
        cb.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        cb.setBackground(CARD_BG);
        cb.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return cb;
    }

    /** 创建标签 */
    public static JLabel createLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_DEFAULT);
        lbl.setForeground(TEXT_PRIMARY);
        return lbl;
    }

    /** 创建副标题标签 */
    public static JLabel createSubLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_DEFAULT);
        lbl.setForeground(TEXT_SECONDARY);
        return lbl;
    }

    /** 创建表格（isCellEditable 由调用方的 DefaultTableModel 匿名子类控制，此处不覆盖） */
    public static JTable createTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? CARD_BG : TABLE_ALT_ROW);
                    c.setForeground(TEXT_PRIMARY);
                } else {
                    // 选中行：主色浅蓝背景，文字保持深色
                    c.setBackground(new Color(232, 240, 254));
                    c.setForeground(TEXT_PRIMARY);
                }
                if (c instanceof JLabel) {
                    ((JLabel) c).setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                }
                return c;
            }
        };
        table.setRowHeight(36);
        table.setShowGrid(true);
        table.setGridColor(BORDER);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getTableHeader().setPreferredSize(new Dimension(table.getTableHeader().getWidth(), 40));
        table.getTableHeader().setReorderingAllowed(false);
        // 支持键盘导航
        table.setSurrendersFocusOnKeystroke(true);

        // 表头样式
        JTableHeader header = table.getTableHeader();
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setFont(FONT_TABLE_HD);
                setBackground(TABLE_HEADER);
                setForeground(TEXT_SECONDARY);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER),
                        BorderFactory.createEmptyBorder(0, 8, 0, 8)
                ));
                setHorizontalAlignment(JLabel.LEFT);
                return this;
            }
        });
        return table;
    }

    /** 创建空布局面板的固定位置组件 */
    public static void setLocation(Component c, int x, int y, int w, int h) {
        c.setBounds(x, y, w, h);
    }

    /** 显示提示消息（替代 JOptionPane） */
    public static void showMessage(Component parent, String message, int type) {
        // type: 0=info(success), 1=warning, -1=error
        String title;
        Color color;
        String icon;
        switch (type) {
            case -1: title = "提示"; color = DANGER; icon = "❌"; break;
            case 1:  title = "注意"; color = WARNING; icon = "⚠️"; break;
            default: title = "提示"; color = SUCCESS; icon = "✅"; break;
        }
        JOptionPane.showMessageDialog(parent,
                "<html><div style='font-family:微软雅黑;font-size:13px;padding:4px;'>"
                + icon + "  " + message + "</div></html>",
                title, JOptionPane.PLAIN_MESSAGE);
    }

    /** 显示确认对话框 */
    public static int showConfirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent,
                "<html><div style='font-family:微软雅黑;font-size:13px;padding:4px;'>"
                + "⚠️  " + message + "</div></html>",
                "确认操作", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
    }
}
