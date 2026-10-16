package me.kekemao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在线口令：把分享码经开源的 ntfy（默认用公共的 ntfy.sh，免注册、免费）中转，
 * 对方只要输入一个 16 位口令就能领取，适合在电话里念、或者分享码太长发不出去的时候。
 *
 * <p>口令本身就是密钥：内容先用口令加密（与带密码的分享码相同），再发到由口令哈希得到的频道，
 * 中转服务器只看到一段密文和一个随机频道名，拿不到口令也就解不开。消息由服务器保存约 12 小时后自动删除。
 */
final class ShareRelay {
    static final String DEFAULT_SERVER = "https://ntfy.sh";
    /** 口令有效期，ntfy.sh 默认缓存消息 12 小时；分享码超过 4KB 时会作为附件存，只保留 3 小时。 */
    static final int SMALL_LIMIT = 4000;
    private static final String PREF_SERVER = "share.relay";
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final Pattern TOKEN = Pattern.compile(
            "(?i)\\bCM-([0-9A-Z]{4})-([0-9A-Z]{4})-([0-9A-Z]{4})-([0-9A-Z]{4})\\b");
    private static final SecureRandom RANDOM = new SecureRandom();

    private ShareRelay() {}

    /** 中转服务器地址；自建 ntfy 的用户可以用 -Dcontactmanager.relay=... 或偏好设置改掉。 */
    static String server() {
        String s = System.getProperty("contactmanager.relay");
        if (s == null || s.isBlank()) s = Preferences.userNodeForPackage(ShareRelay.class).get(PREF_SERVER, DEFAULT_SERVER);
        return s.replaceAll("/+$", "");
    }

    static String serverName() {
        return URI.create(server()).getHost();
    }

    /** 新口令：80 位随机数，写成 CM-XXXX-XXXX-XXXX-XXXX（不含 I、L、O、U，念出来不会混）。 */
    static String newToken() {
        byte[] b = new byte[10];
        RANDOM.nextBytes(b);
        StringBuilder s = new StringBuilder("CM");
        long bits = 0;
        int n = 0, written = 0;
        for (byte x : b) {
            bits = (bits << 8) | (x & 0xFF);
            n += 8;
            while (n >= 5) {
                if (written % 4 == 0) s.append('-');
                s.append(ALPHABET.charAt((int) ((bits >> (n - 5)) & 31)));
                n -= 5;
                written++;
            }
        }
        return s.toString();
    }

    /** 从任意文字里找出口令并规范化（小写、O 写成 0、I/L 写成 1 都能认）。 */
    static String findToken(String text) {
        if (text == null) return null;
        Matcher m = TOKEN.matcher(text);
        if (!m.find()) return null;
        StringBuilder s = new StringBuilder("CM");
        for (int g = 1; g <= 4; g++) {
            s.append('-');
            for (char c : m.group(g).toUpperCase().toCharArray()) {
                if (c == 'O') c = '0';
                else if (c == 'I' || c == 'L') c = '1';
                if (ALPHABET.indexOf(c) < 0) return null;
                s.append(c);
            }
        }
        return s.toString();
    }

    /** 把联系人加密后发到中转服务器，返回口令。在后台线程调用。 */
    static String send(List<ContactShare.Contact> contacts) throws IOException {
        String token = newToken();
        String code = ContactShare.encode(contacts, token);
        HttpURLConnection conn = open(server() + "/" + topic(token));
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        // 超过消息长度上限时由服务器存成附件，这里给它起个名字
        if (code.length() > SMALL_LIMIT) conn.setRequestProperty("Filename", "contacts.txt");
        try {
            try (OutputStream out = conn.getOutputStream()) {
                out.write(code.getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status == 413) throw new IOException("联系人太多，在线口令放不下，请改用分享码");
            if (status == 429) throw new IOException("发送太频繁，请稍后再试");
            if (status / 100 != 2) throw new IOException("中转服务器返回 " + status);
            read(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
        return token;
    }

    /** 领取口令对应的联系人。在后台线程调用。 */
    static List<ContactShare.Contact> receive(String token) throws IOException, ContactShare.ShareException {
        HttpURLConnection conn = open(server() + "/" + topic(token) + "/json?poll=1&since=all");
        String body;
        try {
            int status = conn.getResponseCode();
            if (status == 429) throw new IOException("请求太频繁，请稍后再试");
            if (status / 100 != 2) throw new IOException("中转服务器返回 " + status);
            body = read(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
        String code = null;
        for (String line : body.split("\n")) {
            if (line.isBlank()) continue;
            Object parsed = UpdateChecker.Json.parse(line);
            if (!(parsed instanceof Map) || !"message".equals(((Map<?, ?>) parsed).get("event"))) continue;
            Map<?, ?> msg = (Map<?, ?>) parsed;
            Object attachment = msg.get("attachment");
            if (attachment instanceof Map && ((Map<?, ?>) attachment).get("url") instanceof String) {
                code = download((String) ((Map<?, ?>) attachment).get("url"));
            } else if (msg.get("message") instanceof String) {
                code = (String) msg.get("message");
            }
        }
        if (code == null) throw new ContactShare.ShareException("口令不存在或已过期");
        ContactShare.Parsed parsed = ContactShare.find(code);
        if (parsed == null) throw new ContactShare.ShareException("口令对应的内容无法识别");
        return parsed.open(token);
    }

    static String topic(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(("contact-manager-share:" + token).getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder("cm-");
            for (int i = 0; i < 16; i++) s.append(String.format("%02x", h[i]));
            return s.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String download(String url) throws IOException {
        // 附件地址来自服务器响应，只接受同一台服务器上的 https/http 地址
        URI u = URI.create(url);
        URI s = URI.create(server());
        if (u.getHost() == null || !u.getHost().equalsIgnoreCase(s.getHost())) throw new IOException("附件地址不可信");
        HttpURLConnection conn = open(url);
        try {
            int status = conn.getResponseCode();
            if (status == 404) throw new IOException("口令已过期");
            if (status / 100 != 2) throw new IOException("中转服务器返回 " + status);
            return read(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        String v = UpdateChecker.currentVersion();
        conn.setRequestProperty("User-Agent", "ContactManager/" + (v == null ? "dev" : v));
        return conn;
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 8 * 1024 * 1024) throw new IOException("响应过大");
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
