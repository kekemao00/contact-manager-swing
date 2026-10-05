package me.kekemao.ui;

import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * 扁平按钮。悬停、按下、焦点都是弹簧，样式在 paint 时由时间算出。
 * 文字切换走 {@link MorphText}，在一下模糊里换过去。
 */
public class FlatButton extends JButton {
    public enum Kind { PRIMARY, ACCENT, SECONDARY, GHOST, DANGER }

    protected Kind kind;
    protected Glyph glyph;
    protected final MorphText label;
    protected final SpringValue hover = new SpringValue(this, Spring.SNAPPY, 0);
    protected final SpringValue press = new SpringValue(this, Spring.SNAPPY, 0);
    protected final SpringValue focus = new SpringValue(this, Spring.SNAPPY, 0);
    protected Text.Style style = Text.LABEL;
    protected float radius = 9;
    private int fixedHeight = 34;
    /** 变形成浮层期间，按钮本身不画（同一时刻只有一个形状）。 */
    boolean morphedAway;

    public FlatButton(String text, Kind kind) {
        this(text, null, kind);
    }

    public FlatButton(String text, Glyph glyph, Kind kind) {
        super(text);
        this.kind = kind;
        this.glyph = glyph;
        this.label = new MorphText(this, text);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 14, 0, 14));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) hover.set(1);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover.set(0);
                press.set(0);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) press.set(1);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                press.set(0);
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                // 只给键盘焦点画焦点环，鼠标点击不画
                if (!getModel().isPressed()) focus.set(1);
            }

            @Override
            public void focusLost(FocusEvent e) {
                focus.set(0);
            }
        });
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        if (label != null) {
            label.set(text);
            revalidate();
        }
    }

    public void setGlyph(Glyph glyph) {
        this.glyph = glyph;
        revalidate();
        repaint();
    }

    public void setKind(Kind kind) {
        this.kind = kind;
        repaint();
    }

    public void setFixedHeight(int h) {
        this.fixedHeight = h;
        radius = Math.min(radius, h / 2f);
        revalidate();
    }

    public void setRadius(float r) {
        this.radius = r;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        Insets in = getInsets();
        String t = label.get();
        float w = t.isEmpty() ? 0 : MorphText.measure(t, style);
        int iconW = glyph == null ? 0 : 16 + (t.isEmpty() ? 0 : 6);
        int width = (int) Math.ceil(w) + iconW + in.left + in.right;
        if (t.isEmpty()) width = fixedHeight;
        return new Dimension(width, fixedHeight);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    protected Color fill(double h, double p) {
        switch (kind) {
            case PRIMARY:
                return Palette.mix(Palette.mix(Palette.INK, Palette.INK_HOVER, h), Color.BLACK, p * 0.6);
            case ACCENT:
                return Palette.mix(Palette.mix(Palette.ACCENT, new Color(0x244BE8), h), new Color(0x1C3FCC), p);
            case DANGER:
                return Palette.mix(Palette.mix(Palette.DANGER, new Color(0xC23324), h), new Color(0xA82A1D), p);
            case SECONDARY:
                return Palette.mix(Palette.mix(Palette.SURFACE, Palette.SURFACE_2, h), Palette.SURFACE_3, p);
            case GHOST:
            default:
                return Palette.mix(Palette.mix(Palette.alpha(Palette.SURFACE_3, 0), Palette.alpha(Palette.SURFACE_3, 0.75), h),
                        Palette.SURFACE_3, p);
        }
    }

    protected Color foreground() {
        if (!isEnabled()) return Palette.INK_3;
        switch (kind) {
            case PRIMARY:
            case ACCENT:
            case DANGER:
                return Color.WHITE;
            case GHOST:
                return Palette.mix(Palette.INK_2, Palette.INK, hover.get());
            default:
                return Palette.INK;
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (morphedAway) return;
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        double h = hover.get(), p = press.get(), f = focus.get();
        double w = getWidth(), ht = getHeight();
        double scale = 1 - 0.035 * p;
        g.translate(w / 2, ht / 2);
        g.scale(scale, scale);
        g.translate(-w / 2, -ht / 2);
        paintBody(g, w, ht, h, p, f);
        g.dispose();
    }

    protected void paintBody(Graphics2D g, double w, double ht, double h, double p, double f) {
        RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, w - 1, ht - 1, radius * 2, radius * 2);
        Color bg = isEnabled() ? fill(h, p) : (kind == Kind.GHOST ? Palette.alpha(Palette.SURFACE_3, 0) : Palette.SURFACE_3);
        g.setColor(bg);
        g.fill(shape);
        if (kind == Kind.SECONDARY) {
            g.setColor(Palette.mix(Palette.LINE, Palette.LINE_STRONG, h));
            g.setStroke(new BasicStroke(1f));
            g.draw(shape);
        }
        if (f > 0.01) {
            g.setColor(Palette.alpha(Palette.ACCENT, f));
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Double(1, 1, w - 2, ht - 2, radius * 2 - 1, radius * 2 - 1));
        }
        paintContent(g, w, ht, foreground());
    }

    protected void paintContent(Graphics2D g, double w, double ht, Color fg) {
        String t = label.get();
        float textW = t.isEmpty() ? 0 : MorphText.measure(t, style);
        int iconW = glyph == null ? 0 : 16;
        int gap = (glyph != null && !t.isEmpty()) ? 6 : 0;
        double total = textW + iconW + gap;
        double x = (w - total) / 2;
        if (glyph != null) {
            glyph.paint(g, x, (ht - 16) / 2, 16, fg);
            x += iconW + gap;
        }
        label.paint(g, (float) x, 0, textW, (float) ht, style, fg,
                MorphText.Align.LEFT);
    }
}
