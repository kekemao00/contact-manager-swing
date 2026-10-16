package me.kekemao.ui;

import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;

/**
 * 带下拉的按钮：文字在左，小箭头在右。菜单打开时按钮保持按下的底色、箭头转半圈，
 * 让人看得出“菜单是从这里出来的”。
 */
public class MenuButton extends FlatButton {
    private final SpringValue open = new SpringValue(this, Spring.SNAPPY, 0);
    private final JPopupMenu menu;
    /** 菜单开着时再点按钮：按下那一刻菜单已自己关掉，这里别马上又打开。 */
    private long closedAt;

    public MenuButton(String text, Glyph glyph, Kind kind, JPopupMenu menu) {
        super(text, glyph, kind);
        this.menu = menu;
        setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 12, 0, 10));
        menu.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                open.set(1);
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                open.set(0);
                closedAt = System.currentTimeMillis();
            }

            public void popupMenuCanceled(PopupMenuEvent e) {
                open.set(0);
            }
        });
        addActionListener(e -> {
            if (menu.isVisible() || System.currentTimeMillis() - closedAt < 400) return;
            Dimension ps = menu.getPreferredSize();
            menu.show(this, getWidth() - ps.width, getHeight() + 6);
        });
    }

    public JPopupMenu menu() {
        return menu;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width + 16 + 4, d.height);
    }

    @Override
    protected Color fill(double h, double p) {
        return super.fill(Math.max(h, open.get()), p);
    }

    @Override
    protected Color foreground() {
        Color fg = super.foreground();
        return kind == Kind.GHOST && isEnabled() ? Palette.mix(fg, Palette.INK, open.get()) : fg;
    }

    @Override
    protected void paintContent(Graphics2D g, double w, double ht, Color fg) {
        Insets in = getInsets();
        double x = in.left;
        if (glyph != null) {
            glyph.paint(g, x, (ht - 16) / 2, 16, fg);
            x += 16 + 6;
        }
        String t = label.get();
        float textW = t.isEmpty() ? 0 : MorphText.measure(t, style);
        label.paint(g, (float) x, 0, textW, (float) ht, style, fg, MorphText.Align.LEFT);

        double cx = w - in.right - 8, cy = ht / 2;
        Graphics2D c = (Graphics2D) g.create();
        c.rotate(Math.PI * open.get(), cx, cy);
        Glyph.CHEVRON_DOWN.paint(c, cx - 7, cy - 7, 14, Palette.mix(fg, Palette.INK_3, 0.35));
        c.dispose();
    }
}
