package me.kekemao.ui;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 顶部通知：一个墨黑胶囊，从一个小圆点展开成消息，停留一会儿再收回去。
 * 新消息到来时不新建，而是让同一个形状改变宽度、内容在模糊里换掉。
 */
public final class Toast extends JComponent {
    public enum Kind { SUCCESS, ERROR, WARNING, INFO }

    private static final int H = 40;
    private static final int TOP = 14;

    private final SpringValue width = new SpringValue(this, Spring.SMOOTH, 0);
    private final SpringValue height = new SpringValue(this, Spring.SMOOTH, 0);
    private final SpringValue alpha = new SpringValue(this, Spring.SNAPPY, 0);
    private final MorphText text = new MorphText(this, "");
    private Kind kind = Kind.INFO;
    private Kind prevKind = Kind.INFO;
    private double kindAt = -100;
    private Timer hideTimer;
    private Timer collapseTimer;

    private Toast() {
        setOpaque(false);
    }

    /** 在 anchor 所在窗口顶部显示一条通知。 */
    public static void show(Component anchor, String message, Kind kind) {
        JRootPane root = anchor == null ? null : SwingUtilities.getRootPane(anchor);
        if (root == null) {
            return;
        }
        of(root).present(message, kind);
    }

    private static Toast of(JRootPane root) {
        Object existing = root.getClientProperty(Toast.class);
        if (existing instanceof Toast) return (Toast) existing;
        Toast t = new Toast();
        JLayeredPane lp = root.getLayeredPane();
        lp.add(t, JLayeredPane.POPUP_LAYER);
        t.setBounds(0, 0, lp.getWidth(), H + TOP * 2);
        lp.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                t.setBounds(0, 0, lp.getWidth(), H + TOP * 2);
            }
        });
        root.putClientProperty(Toast.class, t);
        return t;
    }

    private void present(String message, Kind k) {
        if (collapseTimer != null) collapseTimer.stop();
        if (hideTimer != null) hideTimer.stop();
        boolean wasHidden = alpha.target() == 0;
        if (wasHidden) {
            width.snap(10);
            height.snap(10);
            text.snap(message);
            prevKind = k;
        } else {
            text.set(message);
            prevKind = kind;
        }
        kind = k;
        kindAt = Motion.now();
        float tw = MorphText.measure(message, Text.BODY_MEDIUM);
        double target = Math.min(getWidth() - 48, tw + 14 + 22 + 10 + 18);
        width.set(target);
        height.set(H);
        alpha.set(1);
        int stay = 1800 + Math.min(2600, message.length() * 60);
        hideTimer = new Timer(stay, e -> dismiss());
        hideTimer.setRepeats(false);
        hideTimer.start();
    }

    private void dismiss() {
        // 先收成圆，再缩小消失
        width.set(H);
        collapseTimer = new Timer(200, e -> {
            width.set(8);
            height.set(8);
            alpha.set(0);
        });
        collapseTimer.setRepeats(false);
        collapseTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        double a = alpha.get();
        if (a < 0.01) return;
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Motion.clamp01(a)));
        double w = Math.max(0, width.get()), h = Math.max(0, height.get());
        double x = (getWidth() - w) / 2, y = TOP + (H - h) / 2;
        RoundRectangle2D pill = new RoundRectangle2D.Double(x, y, w, h, Math.min(w, h), Math.min(w, h));
        g.setColor(Palette.INK);
        g.fill(pill);
        g.clip(pill);

        // 内容只在形状展开到足够大时出现
        double content = Motion.progress(Math.min(w / Math.max(1, width.target()), h / H), 0.6, 0.98);
        if (content > 0.01) {
            Graphics2D gc = (Graphics2D) g.create();
            gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (Motion.clamp01(a) * content)));
            double ix = x + 10, iy = y + (h - 22) / 2;
            double dt = Motion.now() - kindAt;
            double swap = Motion.progress(dt, 0, 0.22);
            if (prevKind != kind && swap < 1) paintBadge(gc, prevKind, ix, iy, 1 - swap, 1);
            paintBadge(gc, kind, ix, iy, swap, Motion.progress(dt, 0.1, 0.4));
            text.paint(gc, (float) (ix + 22 + 10), (float) y, (float) (w - 22 - 10 - 28), (float) h,
                    Text.BODY_MEDIUM, Color.WHITE, MorphText.Align.LEFT);
            gc.dispose();
        }
        g.dispose();
    }

    private static void paintBadge(Graphics2D g, Kind k, double x, double y, double alpha, double draw) {
        if (alpha < 0.01) return;
        Graphics2D gb = (Graphics2D) g.create();
        gb.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                (float) (((AlphaComposite) g.getComposite()).getAlpha() * Motion.clamp01(alpha))));
        Color c;
        Glyph glyph;
        switch (k) {
            case SUCCESS: c = Palette.ACCENT; glyph = Glyph.CHECK; break;
            case ERROR: c = Palette.DANGER; glyph = Glyph.X; break;
            case WARNING: c = new Color(255, 255, 255, 46); glyph = Glyph.ALERT; break;
            default: c = new Color(255, 255, 255, 46); glyph = Glyph.INFO; break;
        }
        if (c.getAlpha() == 255) {
            gb.setColor(c);
            gb.fill(new Ellipse2D.Double(x, y, 22, 22));
        }
        double p = k == Kind.SUCCESS ? 1 - Math.pow(1 - Motion.clamp01(draw), 3) : 1;
        double gs = (k == Kind.WARNING || k == Kind.INFO) ? 22 : 14;
        glyph.paint(gb, x + (22 - gs) / 2, y + (22 - gs) / 2, gs, Color.WHITE, p);
        gb.dispose();
    }
}
