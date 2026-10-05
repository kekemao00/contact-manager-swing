package me.kekemao.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 混排文字绘制：把一段文字切成拉丁和中文两种片段，拉丁用 Geist，中文用系统字体，
 * 画在同一条基线上。自绘组件里的文字都走这里。
 */
public final class Text {
    private Text() {}

    public static final class Style {
        public final float size;
        public final Fonts.Weight weight;
        public final boolean mono;

        public Style(float size, Fonts.Weight weight) {
            this(size, weight, false);
        }

        public Style(float size, Fonts.Weight weight, boolean mono) {
            this.size = size;
            this.weight = weight;
            this.mono = mono;
        }
    }

    public static final Style BODY = new Style(13f, Fonts.Weight.REGULAR);
    public static final Style BODY_MEDIUM = new Style(13f, Fonts.Weight.MEDIUM);
    public static final Style SMALL = new Style(12f, Fonts.Weight.REGULAR);
    public static final Style CAPTION = new Style(11f, Fonts.Weight.MEDIUM);
    public static final Style LABEL = new Style(13f, Fonts.Weight.MEDIUM);
    public static final Style TITLE = new Style(15f, Fonts.Weight.SEMIBOLD);
    public static final Style HEADLINE = new Style(22f, Fonts.Weight.SEMIBOLD);
    public static final Style MONO = new Style(13f, Fonts.Weight.REGULAR, true);

    private static final class Run {
        final String text;
        final Font font;

        Run(String text, Font font) {
            this.text = text;
            this.font = font;
        }
    }

    private static List<Run> runs(String text, Style s) {
        List<Run> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        Font latin = s.mono ? Fonts.mono(s.size) : Fonts.latin(s.size, s.weight);
        Font cjk = Fonts.cjk(s.size, s.weight);
        StringBuilder sb = new StringBuilder();
        Boolean curLatin = null;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            boolean isLatin = Fonts.latinCanDisplay(cp) && cp < 0x2E80;
            if (cp == ' ' && curLatin != null) isLatin = curLatin;
            if (curLatin != null && isLatin != curLatin) {
                out.add(new Run(sb.toString(), curLatin ? latin : cjk));
                sb.setLength(0);
            }
            curLatin = isLatin;
            sb.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        if (sb.length() > 0) out.add(new Run(sb.toString(), Boolean.TRUE.equals(curLatin) ? latin : cjk));
        return out;
    }

    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /** 以 baseline 为基线、x 为左边缘绘制文字，返回宽度。 */
    public static float draw(Graphics2D g, String text, float x, float baseline, Style s, Color color) {
        hints(g);
        g.setColor(color);
        float cx = x;
        for (Run r : runs(text, s)) {
            g.setFont(r.font);
            g.drawString(r.text, cx, baseline);
            cx += (float) r.font.getStringBounds(r.text, g.getFontRenderContext()).getWidth();
        }
        return cx - x;
    }

    public static float width(Graphics2D g, String text, Style s) {
        return width(g.getFontRenderContext(), text, s);
    }

    public static float width(FontRenderContext frc, String text, Style s) {
        float w = 0;
        for (Run r : runs(text, s)) {
            w += (float) r.font.getStringBounds(r.text, frc).getWidth();
        }
        return w;
    }

    public static FontRenderContext frc() {
        return new FontRenderContext(null, true, true);
    }

    /** 让文字在高度为 h 的盒子里垂直居中时的基线位置。 */
    public static float centerBaseline(Graphics2D g, Style s, float top, float h) {
        FontMetrics fm = g.getFontMetrics(Fonts.cjk(s.size, s.weight));
        return top + (h - fm.getAscent() - fm.getDescent()) / 2f + fm.getAscent() - 0.5f;
    }

    /** 超出宽度时在末尾加省略号。 */
    public static String ellipsize(Graphics2D g, String text, Style s, float maxWidth) {
        if (text == null) return "";
        if (width(g, text, s) <= maxWidth) return text;
        String dots = "…";
        int end = text.length();
        while (end > 0 && width(g, text.substring(0, end) + dots, s) > maxWidth) end--;
        return text.substring(0, end) + dots;
    }
}
