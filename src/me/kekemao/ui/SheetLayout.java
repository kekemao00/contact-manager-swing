package me.kekemao.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;

/** 浮层面板的统一骨架：标题 + 副标题 + 关闭按钮、正文、底部按钮。 */
public class SheetLayout extends JPanel {
    private final JPanel body = new JPanel();
    private final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private final FlatButton close = new FlatButton("", Glyph.X, FlatButton.Kind.GHOST);
    private final JPanel footerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    public SheetLayout(String title, String subtitle) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 20));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        TextView t = new TextView(title, Text.TITLE, Palette.INK);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(t);
        if (subtitle != null) {
            titles.add(Box.createVerticalStrut(2));
            TextView s = new TextView(subtitle, Text.SMALL, Palette.INK_2);
            s.setAlignmentX(Component.LEFT_ALIGNMENT);
            titles.add(s);
        }
        head.add(titles, BorderLayout.CENTER);
        close.setFixedHeight(32);
        close.setToolTipText("关闭（Esc）");
        close.setFocusable(false);
        JPanel closeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeWrap.setOpaque(false);
        closeWrap.add(close);
        head.add(closeWrap, BorderLayout.EAST);
        head.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        add(head, BorderLayout.NORTH);

        body.setOpaque(false);
        add(body, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 4));
        footer.setOpaque(false);
        footerLeft.setOpaque(false);
        bottom.add(footerLeft, BorderLayout.WEST);
        bottom.add(footer, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    public JPanel body() {
        return body;
    }

    public void addFooter(JComponent c) {
        footer.add(c);
    }

    public void addFooterLeft(JComponent c) {
        footerLeft.add(c);
    }

    public void onClose(Runnable r) {
        close.addActionListener(e -> r.run());
    }
}
