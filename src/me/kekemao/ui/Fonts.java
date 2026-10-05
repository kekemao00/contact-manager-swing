package me.kekemao.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 字体：拉丁字母和数字用 Geist，中文回落到系统里的中文界面字体。
 * Geist 文件随程序放在 resources/fonts 下，找不到时整体回落到系统字体，不影响运行。
 */
public final class Fonts {
    public enum Weight { REGULAR, MEDIUM, SEMIBOLD }

    private static final String[] CJK_CANDIDATES = {
            "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Hiragino Sans GB",
            "Noto Sans CJK SC", "Noto Sans SC", "Source Han Sans SC", "WenQuanYi Zen Hei", "微软雅黑"
    };

    private static final Map<Weight, Font> GEIST = new HashMap<>();
    private static Font geistMono;
    private static final String CJK_FAMILY;
    private static final Map<String, Font> CACHE = new HashMap<>();

    static {
        load(Weight.REGULAR, "Geist-Regular.ttf");
        load(Weight.MEDIUM, "Geist-Medium.ttf");
        load(Weight.SEMIBOLD, "Geist-SemiBold.ttf");
        geistMono = loadFile("GeistMono-Regular.ttf");

        Set<String> families = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        String cjk = Font.DIALOG;
        for (String candidate : CJK_CANDIDATES) {
            if (families.contains(candidate)) {
                cjk = candidate;
                break;
            }
        }
        CJK_FAMILY = cjk;
    }

    private Fonts() {}

    private static void load(Weight w, String file) {
        Font f = loadFile(file);
        if (f != null) GEIST.put(w, f);
    }

    private static Font loadFile(String file) {
        try (InputStream in = open(file)) {
            if (in == null) return null;
            Font f = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    private static InputStream open(String file) throws Exception {
        InputStream in = Fonts.class.getResourceAsStream("/fonts/" + file);
        if (in != null) return in;
        String dir = System.getProperty("user.dir");
        for (String sub : new String[]{"resources/fonts", "fonts", "lib/fonts"}) {
            File f = new File(dir, sub + File.separator + file);
            if (f.isFile()) return new FileInputStream(f);
        }
        return null;
    }

    public static boolean hasGeist() {
        return !GEIST.isEmpty();
    }

    /** Geist（拉丁字母、数字）。没有 Geist 时退回中文字体。 */
    public static Font latin(float size, Weight w) {
        Font base = GEIST.getOrDefault(w, GEIST.get(Weight.REGULAR));
        if (base == null) return cjk(size, w);
        return cached("g" + w + size, () -> base.deriveFont(size));
    }

    /** 等宽数字（电话号码、编号）。 */
    public static Font mono(float size) {
        if (geistMono == null) return latin(size, Weight.REGULAR);
        return cached("m" + size, () -> geistMono.deriveFont(size));
    }

    /** 能显示中文的界面字体，给 Swing 自带组件（输入框、菜单）用。 */
    public static Font cjk(float size, Weight w) {
        int style = w == Weight.SEMIBOLD ? Font.BOLD : Font.PLAIN;
        return cached("c" + w + size, () -> new Font(CJK_FAMILY, style, Math.round(size)).deriveFont(size));
    }

    /** Geist 是否能完整显示这段文字。 */
    public static boolean latinCanDisplay(String text) {
        Font g = GEIST.get(Weight.REGULAR);
        return g != null && g.canDisplayUpTo(text) == -1;
    }

    static boolean latinCanDisplay(int codePoint) {
        Font g = GEIST.get(Weight.REGULAR);
        return g != null && g.canDisplay(codePoint);
    }

    private interface Maker { Font make(); }

    private static Font cached(String key, Maker maker) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(key, k -> maker.make());
        }
    }
}
