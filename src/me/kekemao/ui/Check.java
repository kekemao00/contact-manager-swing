package me.kekemao.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 带文字的勾选框：方框 + 标签，点击文字也能切换，空格键切换。 */
public class Check extends JComponent {
    private static final double BOX = 16;

    private final String text;
    private final Text.Style style = new Text.Style(12, Fonts.Weight.MEDIUM);
    private final SpringValue on = new SpringValue(this, Spring.SNAPPY, 0);
    private final SpringValue hover = new SpringValue(this, Spring.SNAPPY, 0);
    private final SpringValue focus = new SpringValue(this, Spring.SNAPPY, 0);
    private final List<Consumer<Boolean>> listeners = new ArrayList<>();
    private boolean selected;

    public Check(String text) {
        this.text = text;
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover.set(1);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover.set(0);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                setSelected(!selected);
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) setSelected(!selected);
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                focus.set(1);
            }

            @Override
            public void focusLost(FocusEvent e) {
                focus.set(0);
            }
        });
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean value) {
        if (value == selected) return;
        selected = value;
        on.set(value ? 1 : 0);
        for (Consumer<Boolean> l : listeners) l.accept(value);
    }

    public void addChangeListener(Consumer<Boolean> l) {
        listeners.add(l);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension((int) Math.ceil(BOX + 8 + MorphText.measure(text, style)) + 4, 22);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        double a = on.get(), h = hover.get(), f = focus.get();
        double y = (getHeight() - BOX) / 2;
        RoundRectangle2D box = new RoundRectangle2D.Double(1.5, y, BOX, BOX, 6, 6);
        if (f > 0.01) {
            g.setColor(Palette.alpha(Palette.FOCUS_RING, f));
            g.setStroke(new BasicStroke(3f));
            g.draw(new RoundRectangle2D.Double(0, y - 1.5, BOX + 3, BOX + 3, 8, 8));
        }
        g.setColor(Palette.mix(Palette.SURFACE, Palette.INK, a));
        g.fill(box);
        g.setColor(Palette.mix(Palette.mix(Palette.LINE_STRONG, Palette.INK_3, h), Palette.INK, a));
        g.setStroke(new BasicStroke(1f));
        g.draw(box);
        if (a > 0.01) {
            Graphics2D gc = (Graphics2D) g.create();
            gc.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Motion.clamp01(a)));
            Glyph.CHECK.paint(gc, 3.5, y + 2, 12, Palette.SURFACE);
            gc.dispose();
        }
        Text.draw(g, text, (float) (BOX + 10), Text.centerBaseline(g, style, 0, getHeight()), style,
                Palette.mix(Palette.INK_2, Palette.INK, a));
        g.dispose();
    }
}
