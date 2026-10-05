package me.kekemao.ui;

/**
 * 解析解形式的阻尼弹簧：给定经过的时间，直接算出位置，不做逐帧积分。
 * 阻尼比略小于 1，最多只有一点点回弹。
 */
public final class Spring {
    /** 按钮按压、悬停之类的小反馈。 */
    public static final Spring SNAPPY = new Spring(0.26, 0.86);
    /** 尺寸、位置变化的默认弹簧。 */
    public static final Spring DEFAULT = new Spring(0.38, 0.82);
    /** 大面积形变（面板展开、通知胶囊）。 */
    public static final Spring SMOOTH = new Spring(0.50, 0.86);
    /** 指示条的前沿：比后沿快。 */
    public static final Spring LEAD = new Spring(0.26, 0.84);
    /** 指示条的后沿：比前沿慢，移动时把指示条拉长。 */
    public static final Spring TRAIL = new Spring(0.46, 0.86);

    private final double omega;
    private final double zeta;
    private final double omegaD;
    private final double settle;

    /**
     * @param response 无阻尼振动周期（秒），越小越快
     * @param dampingRatio 阻尼比，0.8~0.9 只会有很轻的回弹
     */
    public Spring(double response, double dampingRatio) {
        this.omega = 2 * Math.PI / response;
        this.zeta = Math.min(0.999, Math.max(0.3, dampingRatio));
        this.omegaD = omega * Math.sqrt(1 - zeta * zeta);
        double amp = 1 + zeta * omega / omegaD + 1 / omegaD;
        this.settle = Math.log(amp / 0.0005) / (zeta * omega);
    }

    /** 单位阶跃响应：从 0 以零速度出发，趋向 1。 */
    public double step(double t) {
        if (t <= 0) return 0;
        if (t >= settle) return 1;
        double e = Math.exp(-zeta * omega * t);
        return 1 - e * (Math.cos(omegaD * t) + (zeta * omega / omegaD) * Math.sin(omegaD * t));
    }

    /** 单位阶跃响应的速度。 */
    public double stepVelocity(double t) {
        if (t <= 0 || t >= settle) return 0;
        return Math.exp(-zeta * omega * t) * (omega * omega / omegaD) * Math.sin(omegaD * t);
    }

    /** 初速度为 1、目标为 0 的响应（用来接住拖拽松手时的速度）。 */
    public double impulse(double t) {
        if (t <= 0 || t >= settle) return 0;
        return Math.exp(-zeta * omega * t) * Math.sin(omegaD * t) / omegaD;
    }

    public double impulseVelocity(double t) {
        if (t <= 0 || t >= settle) return 0;
        double e = Math.exp(-zeta * omega * t);
        return e * (Math.cos(omegaD * t) - (zeta * omega / omegaD) * Math.sin(omegaD * t));
    }

    /** 多久之后可以视为完全静止（秒）。 */
    public double settleTime() {
        return settle;
    }
}
