package me.kekemao.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.function.Consumer;

/** 确认面板的内容：图标、标题、说明和两个按钮。 */
final class ConfirmPanel extends JPanel {
    private Consumer<Boolean> choice = b -> {};
    private final FlatButton ok;

    ConfirmPanel(String title, String message, String okText, boolean danger) {
        super(new BorderLayout(0, 18));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        Component badge = new Badge(danger);
        head.add(badge);
        head.add(Box.createVerticalStrut(14));
        TextView t = new TextView(title, Text.TITLE, Palette.INK);
        t.setAlignmentX(LEFT_ALIGNMENT);
        head.add(t);
        head.add(Box.createVerticalStrut(6));
        JTextArea msg = new JTextArea(message);
        msg.setLineWrap(true);
        msg.setWrapStyleWord(true);
        msg.setEditable(false);
        msg.setFocusable(false);
        msg.setOpaque(false);
        msg.setBorder(null);
        msg.setFont(Fonts.cjk(13, Fonts.Weight.REGULAR));
        msg.setForeground(Palette.INK_2);
        msg.setAlignmentX(LEFT_ALIGNMENT);
        head.add(msg);
        add(head, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        FlatButton cancel = new FlatButton("取消", FlatButton.Kind.SECONDARY);
        ok = new FlatButton(okText, danger ? FlatButton.Kind.DANGER : FlatButton.Kind.PRIMARY);
        cancel.setFixedHeight(36);
        ok.setFixedHeight(36);
        cancel.addActionListener(e -> choice.accept(false));
        ok.addActionListener(e -> choice.accept(true));
        buttons.add(cancel);
        buttons.add(ok);
        add(buttons, BorderLayout.SOUTH);

        int lines = Math.max(1, (int) Math.ceil(MorphText.measure(message, Text.BODY) / 320.0));
        setPreferredSize(new Dimension(400, 196 + (lines - 1) * 20));
        setFocusTraversalPolicy(new javax.swing.LayoutFocusTraversalPolicy() {
            @Override
            public Component getDefaultComponent(java.awt.Container c) {
                return ok;
            }
        });
        setFocusCycleRoot(true);
        setFocusTraversalPolicyProvider(true);
    }

    void onChoice(Consumer<Boolean> c) {
        this.choice = c;
        // 面板里按回车 = 确认
        registerKeyboardAction(e -> choice.accept(true),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
                WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private static final class Badge extends javax.swing.JComponent {
        private final boolean danger;

        Badge(boolean danger) {
            this.danger = danger;
            setAlignmentX(LEFT_ALIGNMENT);
            Dimension d = new Dimension(36, 36);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Text.hints(g);
            Color bg = danger ? Palette.DANGER_SOFT : Palette.SURFACE_2;
            Color fg = danger ? Palette.DANGER : Palette.INK;
            g.setColor(bg);
            g.fill(new Ellipse2D.Double(0, 0, 36, 36));
            (danger ? Glyph.TRASH : Glyph.INFO).paint(g, 8, 8, 20, fg);
            g.dispose();
        }
    }
}
