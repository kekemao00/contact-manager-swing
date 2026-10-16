package me.kekemao.ui;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

/**
 * 两行菜单项：左边一块浅底图标，右边标题 + 一句说明，最右是快捷键。
 * 用在下拉菜单里，让低频操作把“做什么、作用到哪”一次说清楚。
 */
public class MenuRow extends JMenuItem {
    private static final int HEIGHT = 52;
    private static final int TILE = 32;

    private final Glyph glyph;
    private final String title;
    private final Supplier<String> caption;
    private final String shortcut;

    public MenuRow(Glyph glyph, String title, Supplier<String> caption, String shortcut) {
        super(title);
        this.glyph = glyph;
        this.title = title;
        this.caption = caption;
        this.shortcut = shortcut;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(300, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        int w = getWidth();
        boolean hot = isEnabled() && (isArmed() || getModel().isSelected());

        g.setColor(Palette.SURFACE);
        g.fillRect(0, 0, w, HEIGHT);
        if (hot) {
            g.setColor(Palette.SURFACE_2);
            g.fill(new RoundRectangle2D.Double(4, 1, w - 8, HEIGHT - 2, 14, 14));
        }

        double ty = (HEIGHT - TILE) / 2.0;
        RoundRectangle2D tile = new RoundRectangle2D.Double(12, ty, TILE, TILE, 16, 16);
        g.setColor(hot ? Palette.SURFACE : Palette.SURFACE_2);
        g.fill(tile);
        g.setColor(hot ? Palette.LINE_STRONG : Palette.LINE);
        g.setStroke(new BasicStroke(1f));
        g.draw(tile);
        Color ink = isEnabled() ? Palette.INK : Palette.INK_3;
        glyph.paint(g, 12 + (TILE - 16) / 2.0, ty + (TILE - 16) / 2.0, 16, ink);

        float tx = 12 + TILE + 12;
        float right = w - 14;
        if (shortcut != null) {
            float kw = Text.width(g, shortcut, Text.CAPTION);
            Text.draw(g, shortcut, right - kw, Text.centerBaseline(g, Text.CAPTION, 8, 18), Text.CAPTION, Palette.INK_3);
            right -= kw + 12;
        }
        Text.draw(g, Text.ellipsize(g, title, Text.BODY_MEDIUM, right - tx), tx,
                Text.centerBaseline(g, Text.BODY_MEDIUM, 8, 18), Text.BODY_MEDIUM, ink);
        String cap = caption == null ? null : caption.get();
        if (cap != null && !cap.isEmpty()) {
            Text.draw(g, Text.ellipsize(g, cap, Text.SMALL, w - 14 - tx), tx,
                    Text.centerBaseline(g, Text.SMALL, 26, 18), Text.SMALL, Palette.INK_3);
        }
        g.dispose();
    }
}
