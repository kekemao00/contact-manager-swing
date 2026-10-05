package me.kekemao.ui;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在变形容器里切换的文字：旧文字先模糊淡出，新文字稍后从模糊里淡入，
 * 两者各有自己的进出场时间，不会叠在一起。
 */
public final class MorphText {
    public enum Align { LEFT, CENTER, RIGHT }

    private static final double OUT = 0.11;
    private static final double IN_DELAY = 0.08;
    private static final double IN = 0.17;
    private static final double MAX_BLUR = 4.0;

    private final JComponent host;
    private String current;
    private String previous;
    private double swapAt = -100;
    private double fadeAt = -100;

    public MorphText(JComponent host, String initial) {
        this.host = host;
        this.current = initial == null ? "" : initial;
    }

    public String get() {
        return current;
    }

    public void set(String text) {
        String t = text == null ? "" : text;
        if (t.equals(current)) return;
        double now = Motion.now();
        // 上一段新文字还没出场时，让更早的旧文字继续淡出，避免闪一下
        if (now - swapAt >= IN_DELAY) {
            previous = current;
            fadeAt = now;
        }
        current = t;
        swapAt = now;
        Animator.animate(host, now + IN_DELAY + IN + 0.02);
    }

    /** 立即替换，不做过渡。 */
    public void snap(String text) {
        current = text == null ? "" : text;
        previous = null;
        swapAt = -100;
    }

    public boolean isSwapping() {
        return Motion.now() - swapAt < IN_DELAY + IN;
    }

    public void paint(Graphics2D g, float x, float y, float w, float h, Text.Style s, Color color, Align align) {
        double now = Motion.now();
        double dt = now - swapAt;
        double df = now - fadeAt;
        if (previous != null && df < OUT) {
            double p = Motion.clamp01(df / OUT);
            drawOne(g, previous, x, y, w, h, s, color, align, 1 - p, p * MAX_BLUR, -3 * p);
        }
        double pin = Motion.clamp01((dt - IN_DELAY) / IN);
        if (pin > 0) {
            double ease = 1 - Math.pow(1 - pin, 3);
            drawOne(g, current, x, y, w, h, s, color, align, ease, (1 - ease) * MAX_BLUR, 3 * (1 - ease));
        }
    }

    /** 文字在给定样式下的宽度（用于让容器尺寸跟着内容变）。 */
    public static float measure(String text, Text.Style s) {
        return Text.width(Text.frc(), text, s);
    }

    private static void drawOne(Graphics2D g, String text, float x, float y, float w, float h,
                                Text.Style s, Color color, Align align, double alpha, double blur, double dy) {
        if (alpha <= 0.01 || text.isEmpty()) return;
        float tw = Text.width(g, text, s);
        float tx = align == Align.LEFT ? x : align == Align.CENTER ? x + (w - tw) / 2f : x + w - tw;
        float base = Text.centerBaseline(g, s, y, h) + (float) dy;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Motion.clamp01(alpha)));
        int r = (int) Math.round(blur);
        if (r <= 0) {
            Text.draw(g, text, tx, base, s, color);
        } else {
            Blurred img = blurred(text, s, color, r);
            g.drawImage(img.image, Math.round(tx - img.pad), Math.round(base - img.ascent - img.pad), null);
        }
        g.setComposite(old);
    }

    private static final class Blurred {
        final BufferedImage image;
        final int pad;
        final int ascent;

        Blurred(BufferedImage image, int pad, int ascent) {
            this.image = image;
            this.pad = pad;
            this.ascent = ascent;
        }
    }

    private static final Map<String, Blurred> CACHE = new LinkedHashMap<String, Blurred>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Blurred> e) {
            return size() > 96;
        }
    };

    private static Blurred blurred(String text, Text.Style s, Color color, int r) {
        String key = text + '\u0000' + s.size + s.weight + s.mono + color.getRGB() + '/' + r;
        Blurred b = CACHE.get(key);
        if (b != null) return b;
        int pad = r * 2 + 2;
        int tw = (int) Math.ceil(Text.width(Text.frc(), text, s));
        int ascent = (int) Math.ceil(s.size * 1.1);
        int th = (int) Math.ceil(s.size * 1.5);
        BufferedImage img = new BufferedImage(tw + pad * 2, th + pad * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Text.draw(g, text, pad, pad + ascent, s, color);
        g.dispose();
        boxBlur(img, r);
        boxBlur(img, r);
        b = new Blurred(img, pad, ascent);
        CACHE.put(key, b);
        return b;
    }

    /** 可分离的盒式模糊（预乘 alpha，避免边缘发黑）。 */
    static void boxBlur(BufferedImage img, int r) {
        int w = img.getWidth(), h = img.getHeight();
        int[] src = img.getRGB(0, 0, w, h, null, 0, w);
        int[] tmp = new int[src.length];
        pass(src, tmp, w, h, r, true);
        pass(tmp, src, w, h, r, false);
        img.setRGB(0, 0, w, h, src, 0, w);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h;
        int lines = horizontal ? h : w;
        int div = r * 2 + 1;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) {
                long a = 0, rr = 0, gg = 0, bb = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.min(len - 1, Math.max(0, i + k));
                    int px = horizontal ? in[l * w + j] : in[j * w + l];
                    int pa = px >>> 24;
                    a += pa;
                    rr += ((px >> 16) & 0xFF) * pa;
                    gg += ((px >> 8) & 0xFF) * pa;
                    bb += (px & 0xFF) * pa;
                }
                int oa = (int) (a / div);
                int or = a == 0 ? 0 : (int) (rr / a);
                int og = a == 0 ? 0 : (int) (gg / a);
                int ob = a == 0 ? 0 : (int) (bb / a);
                int idx = horizontal ? l * w + i : i * w + l;
                out[idx] = (oa << 24) | (or << 16) | (og << 8) | ob;
            }
        }
    }
}
