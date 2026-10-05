package me.kekemao.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * 文字标签：拉丁字符用 Geist、中文用系统字体；改文字时在模糊里切换。
 */
public class TextView extends JComponent {
    private final MorphText text;
    private Text.Style style;
    private Color color;
    private MorphText.Align align = MorphText.Align.LEFT;
    private Glyph glyph;

    public TextView(String text, Text.Style style, Color color) {
        this.text = new MorphText(this, text);
        this.style = style;
        this.color = color;
        setOpaque(false);
    }

    public TextView glyph(Glyph g) {
        this.glyph = g;
        revalidate();
        return this;
    }

    public TextView align(MorphText.Align a) {
        this.align = a;
        return this;
    }

    public void setText(String t) {
        String old = text.get();
        text.set(t);
        if (MorphText.measure(old, style) != MorphText.measure(t == null ? "" : t, style)) revalidate();
    }

    public String getText() {
        return text.get();
    }

    public void setColor(Color c) {
        this.color = c;
        repaint();
    }

    public void setStyle(Text.Style s) {
        this.style = s;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        int w = (int) Math.ceil(MorphText.measure(text.get(), style)) + (glyph != null ? 22 : 0) + 2;
        return new Dimension(w, (int) Math.ceil(style.size * 1.6));
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = getPreferredSize();
        return new Dimension(Math.min(d.width, 40), d.height);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        float x = 0;
        float w = getWidth();
        if (glyph != null) {
            glyph.paint(g, 0, (getHeight() - 16) / 2.0, 16, color);
            x = 22;
            w -= 22;
        }
        text.paint(g, x, 0, w, getHeight(), style, color, align);
        g.dispose();
    }
}
