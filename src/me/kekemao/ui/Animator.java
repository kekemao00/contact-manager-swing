package me.kekemao.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 唯一的重绘时钟。它不推进任何动画状态，只是在有东西在动的时候
 * 按显示器节奏请求重绘；每个组件在 paint 时自己用 {@link Motion#now()} 算样式。
 */
public final class Animator {
    private static final Map<JComponent, Double> UNTIL = new IdentityHashMap<>();
    private static final Timer TIMER = new Timer(1000 / 120, e -> tick());

    static {
        TIMER.setCoalesce(true);
    }

    private Animator() {}

    /** 在 endTime（秒）之前持续重绘 c。 */
    public static void animate(JComponent c, double endTime) {
        if (c == null) return;
        Double prev = UNTIL.get(c);
        if (prev == null || prev < endTime) UNTIL.put(c, endTime);
        c.repaint();
        if (!TIMER.isRunning()) TIMER.start();
    }

    /** 持续重绘（加载动画等），直到 {@link #stop(JComponent)}。 */
    public static void hold(JComponent c) {
        animate(c, Double.POSITIVE_INFINITY);
    }

    public static void stop(JComponent c) {
        if (UNTIL.remove(c) != null) c.repaint();
    }

    private static void tick() {
        double now = Motion.now();
        List<JComponent> done = new ArrayList<>();
        for (Map.Entry<JComponent, Double> e : UNTIL.entrySet()) {
            JComponent c = e.getKey();
            c.repaint();
            if (now > e.getValue() || !c.isDisplayable()) done.add(c);
        }
        for (JComponent c : done) UNTIL.remove(c);
        if (UNTIL.isEmpty()) TIMER.stop();
    }
}
