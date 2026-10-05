package me.kekemao.ui;

import java.awt.Color;

/**
 * 颜色令牌：浅暖灰背景、黑白组件，外加一个只用在主操作和选中/焦点上的强调色。
 * 组件上不用渐变、不用发光。
 */
public final class Palette {
    private Palette() {}

    /** 窗口背景：浅暖灰。 */
    public static final Color CANVAS = new Color(0xEFEEEA);
    /** 卡片、输入框、表格等表面。 */
    public static final Color SURFACE = new Color(0xFFFFFF);
    /** 表面上的悬停/次级填充。 */
    public static final Color SURFACE_2 = new Color(0xF6F5F2);
    /** 按下时的填充。 */
    public static final Color SURFACE_3 = new Color(0xECEBE7);

    /** 主文字、主按钮。 */
    public static final Color INK = new Color(0x141413);
    public static final Color INK_HOVER = new Color(0x2B2A28);
    /** 次级文字。 */
    public static final Color INK_2 = new Color(0x5E5B56);
    /** 提示文字、占位符。 */
    public static final Color INK_3 = new Color(0x9A968F);

    /** 分隔线、描边。 */
    public static final Color LINE = new Color(0xE3E1DC);
    public static final Color LINE_STRONG = new Color(0xCFCCC5);

    /** 唯一的强调色。 */
    public static final Color ACCENT = new Color(0x2F5BFF);
    public static final Color ACCENT_SOFT = new Color(0xE8EDFF);
    /** 焦点环：强调色的半透明版本。 */
    public static final Color FOCUS_RING = new Color(0x2F, 0x5B, 0xFF, 56);

    /** 只用于危险操作的确认和错误提示。 */
    public static final Color DANGER = new Color(0xD93B2B);
    public static final Color DANGER_SOFT = new Color(0xFCEBE8);

    /** 遮罩：暖灰而不是纯黑。 */
    public static final Color SCRIM = new Color(0x1A, 0x18, 0x14);

    public static Color mix(Color a, Color b, double t) {
        t = Motion.clamp01(t);
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public static Color alpha(Color c, double a) {
        int al = (int) Math.round(Motion.clamp01(a) * c.getAlpha());
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), al);
    }
}
