package me.kekemao;

import me.kekemao.ui.Toast;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * 检查更新：向 GitHub Releases 查询最新的正式版本，与当前运行的版本比较。
 * 只读取公开的版本信息，不上传任何数据。
 */
public final class UpdateChecker {
    static final String REPO = "kekemao00/contact-manager-swing";
    private static final String LATEST_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases/latest";
    private static final long AUTO_INTERVAL_MS = TimeUnit.HOURS.toMillis(24);
    private static final int STARTUP_DELAY_MS = 3000;

    private static final String PREF_AUTO = "update.auto";
    private static final String PREF_LAST_CHECK = "update.lastCheck";
    private static final String PREF_SKIPPED = "update.skipped";

    private UpdateChecker() {}

    /** 一个已发布的版本。 */
    static final class Release {
        final String version;
        final String tag;
        final String notes;
        final String url;
        final String publishedAt;

        Release(String version, String tag, String notes, String url, String publishedAt) {
            this.version = version;
            this.tag = tag;
            this.notes = notes;
            this.url = url;
            this.publishedAt = publishedAt;
        }
    }

    // ================= 版本 =================

    /** 当前运行的版本号；从源码直接运行（没有打包）时返回 null。 */
    static String currentVersion() {
        String v = UpdateChecker.class.getPackage().getImplementationVersion();
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** 正式打包的版本才会自动检查；开发构建（SNAPSHOT 或未打包）不打扰。 */
    static boolean isReleaseBuild() {
        String v = currentVersion();
        return v != null && !v.contains("SNAPSHOT");
    }

    static String displayVersion() {
        String v = currentVersion();
        if (v == null) return "开发版本";
        return v.contains("SNAPSHOT") ? "开发版本（" + v + "）" : "v" + v;
    }

    /** 比较语义化版本号：a 比 b 新返回正数。带预发布后缀的版本比同号正式版旧。 */
    static int compareVersions(String a, String b) {
        String[] pa = splitPre(a), pb = splitPre(b);
        String[] na = pa[0].split("\\."), nb = pb[0].split("\\.");
        for (int i = 0; i < Math.max(na.length, nb.length); i++) {
            int c = Long.compare(number(na, i), number(nb, i));
            if (c != 0) return c;
        }
        if (pa[1] == null || pb[1] == null) {
            return pa[1] == null ? (pb[1] == null ? 0 : 1) : -1;
        }
        String[] ia = pa[1].split("\\."), ib = pb[1].split("\\.");
        for (int i = 0; i < Math.min(ia.length, ib.length); i++) {
            boolean da = ia[i].matches("\\d+"), db = ib[i].matches("\\d+");
            int c = da && db ? Long.compare(Long.parseLong(ia[i]), Long.parseLong(ib[i]))
                    : da != db ? (da ? -1 : 1) : ia[i].compareTo(ib[i]);
            if (c != 0) return c;
        }
        return Integer.compare(ia.length, ib.length);
    }

    private static String[] splitPre(String v) {
        String s = v == null ? "0" : v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int plus = s.indexOf('+');
        if (plus >= 0) s = s.substring(0, plus);
        int dash = s.indexOf('-');
        return dash >= 0 ? new String[]{s.substring(0, dash), s.substring(dash + 1)} : new String[]{s, null};
    }

    private static long number(String[] parts, int i) {
        if (i >= parts.length) return 0;
        try {
            return Long.parseLong(parts[i].replaceAll("\\D.*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** latest 比当前运行的版本新。当前版本未知时视为任何正式版都更新。 */
    static boolean isNewer(Release latest) {
        String current = currentVersion();
        if (current == null) return true;
        return compareVersions(latest.version, current) > 0;
    }

    // ================= 设置 =================

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(UpdateChecker.class);
    }

    static boolean isAutoCheckEnabled() {
        try {
            return prefs().getBoolean(PREF_AUTO, true);
        } catch (Exception e) {
            return true;
        }
    }

    static void setAutoCheckEnabled(boolean enabled) {
        try {
            prefs().putBoolean(PREF_AUTO, enabled);
        } catch (Exception ignored) {
        }
    }

    static void skipVersion(String version) {
        try {
            prefs().put(PREF_SKIPPED, version);
        } catch (Exception ignored) {
        }
    }

    private static boolean isSkipped(String version) {
        try {
            return version.equals(prefs().get(PREF_SKIPPED, ""));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkedRecently() {
        try {
            long last = prefs().getLong(PREF_LAST_CHECK, 0);
            long now = System.currentTimeMillis();
            return last > 0 && last <= now && now - last < AUTO_INTERVAL_MS;
        } catch (Exception e) {
            return false;
        }
    }

    private static void markChecked() {
        try {
            prefs().putLong(PREF_LAST_CHECK, System.currentTimeMillis());
        } catch (Exception ignored) {
        }
    }

    // ================= 检查 =================

    /**
     * 启动后在后台检查一次（每天最多一次）。有新版本时在顶部通知里提示，
     * 点击通知打开更新面板；没有新版本或网络不通时什么也不显示。
     */
    static void checkOnStartup(JFrame window) {
        if (!isReleaseBuild() || !isAutoCheckEnabled() || checkedRecently()) return;
        Timer delay = new Timer(STARTUP_DELAY_MS, e -> fetchAsync(release -> {
            markChecked();
            if (release == null || !isNewer(release) || isSkipped(release.version) || !window.isShowing()) return;
            Toast[] toast = new Toast[1];
            toast[0] = Toast.show(window, "发现新版本 v" + release.version, Toast.Kind.UPDATE,
                    () -> UpdateSheet.open(window, release, toast[0], toast[0].pillBounds()));
        }, error -> {}));
        delay.setRepeats(false);
        delay.start();
    }

    /** 用户手动检查：结果都给出反馈。 */
    static void checkNow(JFrame window) {
        Toast.show(window, "正在检查更新…", Toast.Kind.INFO);
        fetchAsync(release -> {
            markChecked();
            if (!window.isShowing()) return;
            if (release == null) {
                Theme.showMessage(window, "还没有发布过版本", Theme.MESSAGE_WARNING);
            } else if (isNewer(release)) {
                Toast.show(window, "发现新版本 v" + release.version, Toast.Kind.UPDATE);
                UpdateSheet.open(window, release, null, null);
            } else {
                Theme.showMessage(window, "已是最新版本 v" + currentVersion(), Theme.MESSAGE_SUCCESS);
            }
        }, error -> {
            if (window.isShowing()) Theme.showMessage(window, "检查更新失败：" + error, Theme.MESSAGE_ERROR);
        });
    }

    /** 在后台线程请求，结果回到界面线程。release 为 null 表示仓库还没有正式版本。 */
    private static void fetchAsync(Consumer<Release> onResult, Consumer<String> onError) {
        Thread t = new Thread(() -> {
            try {
                Release r = fetchLatest();
                SwingUtilities.invokeLater(() -> onResult.accept(r));
            } catch (Exception e) {
                String msg = e instanceof CheckFailed ? e.getMessage() : "网络不可用";
                SwingUtilities.invokeLater(() -> onError.accept(msg));
            }
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    static Release fetchLatest() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(LATEST_API).toURL().openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        String v = currentVersion();
        conn.setRequestProperty("User-Agent", "ContactManager/" + (v == null ? "dev" : v));
        try {
            int code = conn.getResponseCode();
            if (code == 404) return null;
            if (code == 403 || code == 429) throw new CheckFailed("请求过于频繁，请稍后再试");
            if (code != 200) throw new CheckFailed("服务器返回 " + code);
            Object parsed = Json.parse(readAll(conn.getInputStream()));
            if (!(parsed instanceof Map)) throw new CheckFailed("无法识别的响应");
            Map<?, ?> json = (Map<?, ?>) parsed;
            String tag = str(json.get("tag_name"));
            if (tag.isEmpty()) throw new CheckFailed("无法识别的响应");
            String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
            String url = str(json.get("html_url"));
            return new Release(version, tag, str(json.get("body")),
                    url.startsWith("https://") ? url : RELEASES_PAGE, str(json.get("published_at")));
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 2 * 1024 * 1024) throw new CheckFailed("响应过大");
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static String str(Object o) {
        return o instanceof String ? (String) o : "";
    }

    /** 用系统浏览器打开下载页；打不开时把链接复制到剪贴板。 */
    static void openDownloadPage(Component anchor, String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(url), null);
        Theme.showMessage(anchor, "无法打开浏览器，下载链接已复制", Theme.MESSAGE_WARNING);
    }

    /** 可以直接展示给用户的失败原因；其余异常一律显示为网络不可用。 */
    private static final class CheckFailed extends IOException {
        CheckFailed(String message) {
            super(message);
        }
    }

    /** 只够读 GitHub 响应的最小 JSON 解析器，避免为此引入依赖。 */
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String text) throws IOException {
            Json p = new Json(text);
            p.ws();
            Object v = p.value();
            p.ws();
            if (p.i != p.s.length()) throw p.error();
            return v;
        }

        private IOException error() {
            return new CheckFailed("无法识别的响应");
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private Object value() throws IOException {
            if (i >= s.length()) throw error();
            char c = s.charAt(i);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': return literal("true", Boolean.TRUE);
                case 'f': return literal("false", Boolean.FALSE);
                case 'n': return literal("null", null);
                default: return number();
            }
        }

        private Object literal(String word, Object v) throws IOException {
            if (!s.startsWith(word, i)) throw error();
            i += word.length();
            return v;
        }

        private Map<String, Object> object() throws IOException {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (peek('}')) return m;
            while (true) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') throw error();
                String k = string();
                ws();
                expect(':');
                ws();
                m.put(k, value());
                ws();
                if (peek('}')) return m;
                expect(',');
            }
        }

        private List<Object> array() throws IOException {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (peek(']')) return l;
            while (true) {
                ws();
                l.add(value());
                ws();
                if (peek(']')) return l;
                expect(',');
            }
        }

        private boolean peek(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        private void expect(char c) throws IOException {
            if (!peek(c)) throw error();
        }

        private String string() throws IOException {
            StringBuilder b = new StringBuilder();
            i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw error();
                        try {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error();
                        }
                        i += 4;
                        break;
                    default: b.append(e);
                }
            }
            throw error();
        }

        private Object number() throws IOException {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw error();
            try {
                return Double.parseDouble(s.substring(start, i));
            } catch (NumberFormatException e) {
                throw error();
            }
        }
    }
}
