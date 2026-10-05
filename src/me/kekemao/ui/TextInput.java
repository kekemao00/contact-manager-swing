package me.kekemao.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * 圆角输入框：前置图标、占位符、可选的尾部操作（清空 / 显示密码）。
 * 焦点环和错误态都是弹簧；出错时整个框做一次有阻尼的水平抖动。
 */
public class TextInput extends JPanel {
    private static final int RING = 3;

    private final JTextField field;
    private final Glyph leading;
    private String placeholder;
    private final SpringValue focus = new SpringValue(this, Spring.SNAPPY, 0);
    private final SpringValue hover = new SpringValue(this, Spring.SNAPPY, 0);
    private final SpringValue error = new SpringValue(this, Spring.DEFAULT, 0);
    private final SpringValue shake = new SpringValue(this, new Spring(0.16, 0.32), 0);
    private final SpringValue empty;
    private final TrailingButton trailing;
    private int height = 40;

    public TextInput(String placeholder, Glyph leading) {
        this(new JTextField(), placeholder, leading, false);
    }

    public static TextInput password(String placeholder) {
        return new TextInput(new JPasswordField(), placeholder, Glyph.LOCK, true);
    }

    /** 带清空按钮的搜索框。 */
    public static TextInput search(String placeholder) {
        return new TextInput(new JTextField(), placeholder, Glyph.SEARCH, false).withClear();
    }

    private TextInput(JTextField field, String placeholder, Glyph leading, boolean password) {
        super(new BorderLayout());
        this.field = field;
        this.leading = leading;
        this.placeholder = placeholder;
        setOpaque(false);
        int left = RING + (leading != null ? 38 : 12);
        setBorder(BorderFactory.createEmptyBorder(RING, left, RING, RING + 4));

        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        field.setFont(Fonts.cjk(13.5f, Fonts.Weight.REGULAR));
        field.setForeground(Palette.INK);
        field.setCaretColor(Palette.INK);
        field.setSelectionColor(Palette.ACCENT_SOFT);
        field.setSelectedTextColor(Palette.INK);
        if (field instanceof JPasswordField) ((JPasswordField) field).setEchoChar('•');
        add(field, BorderLayout.CENTER);

        empty = new SpringValue(this, Spring.SNAPPY, field.getText().isEmpty() ? 1 : 0);
        field.getDocument().addDocumentListener(new DocumentListener() {
            void changed() {
                empty.set(field.getDocument().getLength() == 0 ? 1 : 0);
                if (error.target() > 0) error.set(0);
                if (trailing != null) trailing.refresh();
            }

            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                focus.set(1);
            }

            @Override
            public void focusLost(FocusEvent e) {
                focus.set(0);
            }
        });
        MouseAdapter hoverer = new MouseAdapter() {
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
                field.requestFocusInWindow();
            }
        };
        addMouseListener(hoverer);
        field.addMouseListener(hoverer);
        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

        if (password) {
            trailing = new TrailingButton(Glyph.EYE, () -> {
                JPasswordField pf = (JPasswordField) field;
                boolean hidden = pf.getEchoChar() != 0;
                pf.setEchoChar(hidden ? (char) 0 : '•');
                return hidden ? Glyph.EYE_OFF : Glyph.EYE;
            }, true);
            add(trailing, BorderLayout.EAST);
        } else {
            trailing = null;
        }
    }

    private TrailingButton clearButton;

    private TextInput withClear() {
        clearButton = new TrailingButton(Glyph.X, () -> {
            field.setText("");
            field.requestFocusInWindow();
            return Glyph.X;
        }, false);
        add(clearButton, BorderLayout.EAST);
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { clearButton.refresh(); }
            public void removeUpdate(DocumentEvent e) { clearButton.refresh(); }
            public void changedUpdate(DocumentEvent e) { clearButton.refresh(); }
        });
        return this;
    }

    public JTextField field() {
        return field;
    }

    public String getText() {
        return field.getText();
    }

    public void setText(String text) {
        field.setText(text);
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    public void setFieldHeight(int h) {
        this.height = h;
        revalidate();
    }

    /** 标记为出错：描边变红并抖一下，用户一输入就恢复。 */
    public void flagError() {
        error.set(1);
        shake.snap(0);
        shake.kick(260);
    }

    public void clearError() {
        error.set(0);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, 120), height + RING * 2);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        double f = focus.get(), h = hover.get(), er = error.get();
        double dx = shake.get();
        g.translate(dx, 0);
        double w = getWidth() - RING * 2, ht = getHeight() - RING * 2;
        double r = 10;
        RoundRectangle2D box = new RoundRectangle2D.Double(RING + 0.5, RING + 0.5, w - 1, ht - 1, r * 2, r * 2);

        if (f > 0.01 || er > 0.01) {
            Color ring = Palette.mix(Palette.FOCUS_RING, Palette.alpha(Palette.DANGER, 0.22), er);
            g.setColor(Palette.alpha(ring, Math.max(f, er)));
            g.setStroke(new BasicStroke(RING * 2));
            g.draw(new RoundRectangle2D.Double(RING, RING, w, ht, r * 2 + 2, r * 2 + 2));
        }
        g.setColor(Palette.SURFACE);
        g.fill(box);
        Color border = Palette.mix(Palette.LINE, Palette.LINE_STRONG, h);
        border = Palette.mix(border, Palette.ACCENT, f);
        border = Palette.mix(border, Palette.DANGER, er);
        g.setColor(border);
        g.setStroke(new BasicStroke(1f));
        g.draw(box);

        if (leading != null) {
            Color ic = Palette.mix(Palette.INK_3, Palette.INK, Math.max(f, 1 - empty.get()) * 0.85);
            leading.paint(g, RING + 13, RING + (ht - 16) / 2, 16, Palette.mix(ic, Palette.DANGER, er));
        }

        double e = empty.get();
        if (e > 0.01 && placeholder != null) {
            Graphics2D gp = (Graphics2D) g.create();
            gp.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Motion.clamp01(e)));
            float x = field.getX() + 1 + (float) ((1 - e) * 6);
            float base = Text.centerBaseline(gp, Text.BODY, RING, (float) ht);
            Text.draw(gp, placeholder, x, base, new Text.Style(13.5f, Fonts.Weight.REGULAR), Palette.INK_3);
            gp.dispose();
        }
        g.dispose();
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(shake.get(), 0);
        super.paintChildren(g2);
        g2.dispose();
    }

    /** 尾部的小图标按钮。 */
    private final class TrailingButton extends JComponent {
        private Glyph glyph;
        private final java.util.function.Supplier<Glyph> action;
        private final boolean alwaysVisible;
        private final SpringValue visible;
        private final SpringValue hot = new SpringValue(this, Spring.SNAPPY, 0);

        TrailingButton(Glyph glyph, java.util.function.Supplier<Glyph> action, boolean alwaysVisible) {
            this.glyph = glyph;
            this.action = action;
            this.alwaysVisible = alwaysVisible;
            this.visible = new SpringValue(this, Spring.SNAPPY, alwaysVisible ? 1 : 0);
            setPreferredSize(new Dimension(28, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hot.set(1);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hot.set(0);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (visible.target() > 0) {
                        TrailingButton.this.glyph = action.get();
                        repaint();
                    }
                }
            });
        }

        void refresh() {
            if (!alwaysVisible) visible.set(field.getDocument().getLength() > 0 ? 1 : 0);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            double v = visible.get();
            if (v < 0.01) return;
            Graphics2D g = (Graphics2D) g0.create();
            Text.hints(g);
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Motion.clamp01(v)));
            double s = 0.8 + 0.2 * v;
            double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
            g.translate(cx, cy);
            g.scale(s, s);
            g.translate(-cx, -cy);
            double hv = hot.get();
            if (hv > 0.01) {
                g.setColor(Palette.alpha(Palette.SURFACE_3, hv));
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - 12, cy - 12, 24, 24));
            }
            glyph.paint(g, cx - 8, cy - 8, 16, Palette.mix(Palette.INK_3, Palette.INK, hv));
            g.dispose();
        }
    }
}
