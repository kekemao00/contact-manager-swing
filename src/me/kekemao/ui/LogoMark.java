package me.kekemao.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/** 程序标志：墨黑圆角方块里一个白色通讯录图标。 */
public class LogoMark extends JComponent {
    private final int size;

    public LogoMark(int size) {
        this.size = size;
        Dimension d = new Dimension(size, size);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        double r = size * 0.3;
        g.setColor(Palette.INK);
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, r * 2, r * 2));
        double inner = Math.min(24, size * 0.56);
        Glyph.CONTACTS.paint(g, (size - inner) / 2, (size - inner) / 2, inner, Color.WHITE);
        g.dispose();
    }
}
