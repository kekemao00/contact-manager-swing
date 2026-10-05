package me.kekemao.ui;

import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 会变形的主按钮：同一个形状在“按钮 → 加载 → 对勾 / 错误”之间改变宽度、圆角和颜色，
 * 里面的内容在一下模糊里切换。
 */
public class MorphButton extends FlatButton {
    public enum State { IDLE, LOADING, SUCCESS, ERROR }

    private State state = State.IDLE;
    /** 1 = 完整宽度，0 = 收成圆形。 */
    private final SpringValue extent = new SpringValue(this, Spring.DEFAULT, 1);
    /** 0 = 墨黑，1 = 危险红。 */
    private final SpringValue danger = new SpringValue(this, Spring.DEFAULT, 0);
    private double stateAt = -100;
    private String idleText;
    private Timer revert;

    public MorphButton(String text) {
        super(text, Kind.PRIMARY);
        this.idleText = text;
        setFixedHeight(42);
        radius = 11;
    }

    public State getState() {
        return state;
    }

    public void setIdleText(String text) {
        idleText = text;
        if (state == State.IDLE) setText(text);
    }

    public void loading() {
        go(State.LOADING);
        extent.set(0);
        danger.set(0);
        Animator.hold(this);
    }

    public void success() {
        go(State.SUCCESS);
        extent.set(0);
        danger.set(0);
        Animator.stop(this);
        Animator.animate(this, Motion.now() + 0.8);
    }

    /** 变成错误提示，一段时间后自己变回按钮。 */
    public void error(String message) {
        go(State.ERROR);
        Animator.stop(this);
        extent.set(1);
        danger.set(1);
        setText(message);
        if (revert != null) revert.stop();
        revert = new Timer(1800, e -> idle());
        revert.setRepeats(false);
        revert.start();
    }

    public void idle() {
        go(State.IDLE);
        Animator.stop(this);
        extent.set(1);
        danger.set(0);
        setText(idleText);
    }

    private void go(State s) {
        state = s;
        stateAt = Motion.now();
        if (revert != null && s != State.ERROR) revert.stop();
        setEnabled(s == State.IDLE || s == State.ERROR);
    }

    @Override
    protected void paintBody(Graphics2D g, double w, double ht, double h, double p, double f) {
        double e = extent.get();
        double bw = ht + (w - ht) * e;
        double x = (w - bw) / 2;
        double r = radius + (ht / 2 - radius) * (1 - Motion.clamp01(e));
        Color base = Palette.mix(Palette.INK, Palette.INK_HOVER, state == State.IDLE ? h : 0);
        base = Palette.mix(base, Color.BLACK, p * 0.6);
        Color fill = Palette.mix(base, Palette.DANGER, danger.get());
        RoundRectangle2D shape = new RoundRectangle2D.Double(x + 0.5, 0.5, bw - 1, ht - 1, r * 2, r * 2);
        g.setColor(fill);
        g.fill(shape);
        if (f > 0.01) {
            g.setColor(Palette.alpha(Palette.ACCENT, f));
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Double(x + 1, 1, bw - 2, ht - 2, r * 2 - 1, r * 2 - 1));
        }

        double dt = Motion.now() - stateAt;
        Color fg = Color.WHITE;
        // 文字只在形状足够宽时出现，收窄时先模糊淡出
        double textAlpha = Motion.progress(e, 0.55, 0.95);
        if (textAlpha > 0.01) {
            Graphics2D gt = (Graphics2D) g.create();
            gt.clip(shape);
            gt.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) textAlpha));
            String t = label.get();
            float tw = MorphText.measure(t, style);
            Glyph icon = state == State.ERROR ? Glyph.ALERT : null;
            double total = tw + (icon != null ? 22 : 0);
            double cx = (w - total) / 2;
            if (icon != null) {
                icon.paint(gt, cx, (ht - 16) / 2, 16, fg);
                cx += 22;
            }
            label.paint(gt, (float) cx, 0, tw, (float) ht, style, fg, MorphText.Align.LEFT);
            gt.dispose();
        }

        double cx = w / 2, cy = ht / 2;
        if (state == State.LOADING) {
            double a = Motion.progress(dt, 0.12, 0.3);
            if (a > 0) {
                double now = Motion.now();
                double rot = now * 400 % 360;
                double sweep = 90 + 120 * (0.5 + 0.5 * Math.sin(now * 4.2));
                double rad = 8;
                g.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(Palette.alpha(Color.WHITE, 0.22 * a));
                g.draw(new java.awt.geom.Ellipse2D.Double(cx - rad, cy - rad, rad * 2, rad * 2));
                g.setColor(Palette.alpha(Color.WHITE, a));
                g.draw(new Arc2D.Double(cx - rad, cy - rad, rad * 2, rad * 2, -rot, sweep, Arc2D.OPEN));
            }
        } else if (state == State.SUCCESS) {
            double draw = Motion.progress(dt, 0.08, 0.36);
            double ease = 1 - Math.pow(1 - draw, 3);
            Glyph.CHECK.paint(g, cx - 10, cy - 10, 20, fg, ease);
        }
    }
}
