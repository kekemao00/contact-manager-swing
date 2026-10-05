package me.kekemao.ui;

import javax.swing.JComponent;
import javax.swing.JRootPane;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** 窗口打开时的入场：一层画布色的遮罩用弹簧淡掉，内容随之出现。 */
public final class Fade extends JComponent {
    private final SpringValue cover = new SpringValue(this, Spring.SMOOTH, 1);

    private Fade() {
        setOpaque(false);
    }

    public static void in(JRootPane root) {
        Fade f = new Fade();
        root.setGlassPane(f);
        f.setVisible(true);
        f.cover.set(0);
        Animator.animate(f, Motion.now() + Spring.SMOOTH.settleTime());
        javax.swing.Timer t = new javax.swing.Timer((int) (Spring.SMOOTH.settleTime() * 1000), e -> f.setVisible(false));
        t.setRepeats(false);
        t.start();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        double c = cover.get();
        if (c < 0.005) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(Palette.alpha(Palette.CANVAS, c));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }
}
