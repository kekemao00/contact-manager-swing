package me.kekemao.ui;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * 一个随时间变化的数值。每次改目标都会叠加一段新的弹簧响应，
 * 所以任意时刻的值只取决于时间本身，位置和速度天然连续。
 */
public final class SpringValue {
    private static final class Segment {
        final double t0;
        final double amount;
        final boolean impulse;
        final Spring spring;

        Segment(double t0, double amount, boolean impulse, Spring spring) {
            this.t0 = t0;
            this.amount = amount;
            this.impulse = impulse;
            this.spring = spring;
        }

        double end() {
            return t0 + spring.settleTime();
        }
    }

    private final JComponent host;
    private final Spring spring;
    private final List<Segment> segments = new ArrayList<>();
    private double base;
    private double target;

    public SpringValue(JComponent host, Spring spring, double initial) {
        this.host = host;
        this.spring = spring;
        this.base = initial;
        this.target = initial;
    }

    public double get() {
        return valueAt(Motion.now());
    }

    public double valueAt(double t) {
        double v = base;
        for (Segment s : segments) {
            double dt = t - s.t0;
            v += s.impulse ? s.amount * s.spring.impulse(dt) : s.amount * s.spring.step(dt);
        }
        return v;
    }

    public double velocityAt(double t) {
        double v = 0;
        for (Segment s : segments) {
            double dt = t - s.t0;
            v += s.impulse ? s.amount * s.spring.impulseVelocity(dt) : s.amount * s.spring.stepVelocity(dt);
        }
        return v;
    }

    public double target() {
        return target;
    }

    public void set(double newTarget) {
        set(newTarget, spring);
    }

    /** 弹向新目标，用指定的弹簧。 */
    public void set(double newTarget, Spring with) {
        double now = Motion.now();
        prune(now);
        double delta = newTarget - target;
        if (Math.abs(delta) < 1e-9) return;
        segments.add(new Segment(now, delta, false, with));
        target = newTarget;
        Animator.animate(host, now + with.settleTime());
    }

    /** 直接跳到某个值，没有动画（例如拖拽时跟手）。 */
    public void snap(double value) {
        segments.clear();
        base = value;
        target = value;
        if (host != null) host.repaint();
    }

    /** 给当前运动加一个速度（例如抖动、拖拽松手的惯性）。 */
    public void kick(double velocity) {
        double now = Motion.now();
        prune(now);
        segments.add(new Segment(now, velocity, true, spring));
        Animator.animate(host, now + spring.settleTime());
    }

    /** 从当前位置带着速度 velocity 松手，弹回 newTarget。 */
    public void release(double from, double velocity, double newTarget) {
        snap(from);
        set(newTarget);
        if (Math.abs(velocity) > 1e-6) kick(velocity);
    }

    public boolean isMoving() {
        double now = Motion.now();
        for (Segment s : segments) {
            if (now < s.end()) return true;
        }
        return false;
    }

    private void prune(double now) {
        for (int i = segments.size() - 1; i >= 0; i--) {
            Segment s = segments.get(i);
            if (now >= s.end()) {
                if (!s.impulse) base += s.amount;
                segments.remove(i);
            }
        }
    }
}
