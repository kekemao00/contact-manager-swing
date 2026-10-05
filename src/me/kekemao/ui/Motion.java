package me.kekemao.ui;

/**
 * 动效时钟。界面上所有动画都只由这个时间推出来，帧和帧之间不保存状态。
 * 测试或截图时可以冻结在任意时刻，逐帧检查。
 */
public final class Motion {
    private static final long ORIGIN = System.nanoTime();
    private static volatile double frozen = Double.NaN;

    private Motion() {}

    /** 当前时间，单位秒。 */
    public static double now() {
        double f = frozen;
        if (!Double.isNaN(f)) return f;
        return (System.nanoTime() - ORIGIN) / 1e9;
    }

    /** 把时钟停在 t 秒（截图、测试用）。 */
    public static void freeze(double t) {
        frozen = t;
    }

    public static void unfreeze() {
        frozen = Double.NaN;
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** 把 v 从 [a,b] 线性映射到 [0,1] 并截断。 */
    public static double progress(double v, double a, double b) {
        if (b == a) return v >= b ? 1 : 0;
        return clamp01((v - a) / (b - a));
    }
}
