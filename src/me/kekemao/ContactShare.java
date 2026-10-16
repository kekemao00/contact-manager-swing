package me.kekemao;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 联系人分享码：把几位联系人压缩成一串可以直接粘贴进聊天窗口的文字（可选密码加密），
 * 同一串文字也能画成二维码。接收方复制这串文字或二维码图片后，回到通讯录即可导入。
 *
 * <p>格式：{@code cm1.} + base64url(版本 | 标志 | [盐 16 | IV 12] | 正文)。
 * 正文是 DEFLATE 压缩后的 UTF-8 文本，每位联系人一条记录（0x1E 分隔），字段之间用 0x1F 分隔。
 * 加密时用 PBKDF2-SHA256 从密码派生 AES-256-GCM 密钥，改动任何一个字符都会解不开。
 */
final class ContactShare {
    static final String PREFIX = "cm1.";
    /** 记录里字段的顺序；以后加字段只往后加，旧版本读取时忽略多出来的。 */
    static final String[] FIELDS = {"c_name", "c_nickname", "c_phone", "c_email", "c_address", "c_company", "c_job_title", "notes"};

    private static final byte VERSION = 1;
    private static final byte FLAG_ENCRYPTED = 1;
    private static final int SALT = 16;
    private static final int IV = 12;
    private static final int ITERATIONS = 120_000;
    private static final char RECORD = '\u001E';
    private static final char UNIT = '\u001F';
    /** 防止恶意分享码解压出巨大的数据。 */
    private static final int MAX_PLAIN = 4 * 1024 * 1024;
    private static final Pattern CODE = Pattern.compile("cm1\\.([A-Za-z0-9_-]{8,})");
    private static final SecureRandom RANDOM = new SecureRandom();

    private ContactShare() {}

    /** 一位被分享的联系人，字段与 {@link #FIELDS} 一一对应（分类是显示值）。 */
    static final class Contact {
        final String[] values = new String[FIELDS.length];

        Contact() {
            Arrays.fill(values, "");
        }

        static Contact of(Map<String, Object> map) {
            Contact c = new Contact();
            for (int i = 0; i < FIELDS.length; i++) {
                Object v = map.get(FIELDS[i]);
                c.values[i] = v == null ? "" : v.toString().trim();
            }
            return c;
        }

        String get(String field) {
            int i = Arrays.asList(FIELDS).indexOf(field);
            return i < 0 ? "" : values[i];
        }

        String name() {
            return values[0];
        }
    }

    /** 分享码解开之前就能知道的信息。 */
    static final class Parsed {
        final String code;
        final boolean encrypted;
        private final byte[] raw;

        private Parsed(String code, byte[] raw) {
            this.code = code;
            this.raw = raw;
            this.encrypted = (raw[1] & FLAG_ENCRYPTED) != 0;
        }

        List<Contact> open(String password) throws ShareException {
            byte[] body;
            if (encrypted) {
                if (password == null || password.isEmpty()) throw new ShareException("需要密码");
                if (raw.length < 2 + SALT + IV + 16) throw new ShareException("分享码不完整");
                byte[] salt = Arrays.copyOfRange(raw, 2, 2 + SALT);
                byte[] iv = Arrays.copyOfRange(raw, 2 + SALT, 2 + SALT + IV);
                try {
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, key(password, salt), new GCMParameterSpec(128, iv));
                    cipher.updateAAD(raw, 0, 2);
                    body = cipher.doFinal(raw, 2 + SALT + IV, raw.length - 2 - SALT - IV);
                } catch (AEADBadTagException e) {
                    throw new ShareException("密码不对");
                } catch (GeneralSecurityException e) {
                    throw new ShareException("无法解密：" + e.getMessage());
                }
            } else {
                body = Arrays.copyOfRange(raw, 2, raw.length);
            }
            return decodeContacts(inflate(body));
        }
    }

    static final class ShareException extends Exception {
        ShareException(String message) {
            super(message);
        }
    }

    // ---------- 生成 ----------

    /** @param password 为空表示不加密 */
    static String encode(List<Contact> contacts, String password) {
        byte[] body = deflate(encodeContacts(contacts).getBytes(StandardCharsets.UTF_8));
        boolean encrypt = password != null && !password.isEmpty();
        byte[] header = {VERSION, encrypt ? FLAG_ENCRYPTED : 0};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header);
        if (encrypt) {
            byte[] salt = new byte[SALT];
            byte[] iv = new byte[IV];
            RANDOM.nextBytes(salt);
            RANDOM.nextBytes(iv);
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), new GCMParameterSpec(128, iv));
                cipher.updateAAD(header);
                out.writeBytes(salt);
                out.writeBytes(iv);
                out.writeBytes(cipher.doFinal(body));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        } else {
            out.writeBytes(body);
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /** 复制到剪贴板的整段文字：前面一句人能看懂的说明，最后一行是分享码。 */
    static String message(List<Contact> contacts, String code, boolean encrypted) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < contacts.size() && i < 3; i++) {
            if (i > 0) names.append("、");
            names.append(contacts.get(i).name());
        }
        if (contacts.size() > 3) names.append(" 等");
        return "【通讯录】分享了 " + contacts.size() + " 位联系人：" + names
                + (encrypted ? "（需要密码）" : "")
                + "\n复制这整段文字，打开通讯录即可导入。\n" + code;
    }

    // ---------- 读取 ----------

    /** 从任意一段文字里找出分享码（聊天软件可能把长串折成多行，先去掉空白）。 */
    static Parsed find(String text) {
        if (text == null || !text.contains("cm1")) return null;
        Matcher m = CODE.matcher(text.replaceAll("\\s+", ""));
        while (m.find()) {
            try {
                byte[] raw = Base64.getUrlDecoder().decode(m.group(1));
                if (raw.length >= 3 && raw[0] == VERSION) return new Parsed(PREFIX + m.group(1), raw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    // ---------- 二维码 ----------

    /** 画不下时返回 null（联系人太多，超出二维码容量）。 */
    static BitMatrix qr(String code) {
        for (ErrorCorrectionLevel level : new ErrorCorrectionLevel[]{ErrorCorrectionLevel.M, ErrorCorrectionLevel.L}) {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, level);
            hints.put(EncodeHintType.MARGIN, 0);
            try {
                return new QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 0, 0, hints);
            } catch (WriterException | IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    /** 从图片里识别二维码文字，识别不到返回 null。 */
    static String readQr(Image image) {
        BufferedImage img = toBuffered(image);
        if (img == null) return null;
        int w = img.getWidth(), h = img.getHeight();
        int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
        // 透明像素当作白色，否则截图里的透明底会被识别成黑色
        for (int i = 0; i < pixels.length; i++) {
            int a = pixels[i] >>> 24;
            if (a < 255) {
                int r = blend((pixels[i] >> 16) & 0xFF, a), g = blend((pixels[i] >> 8) & 0xFF, a), b = blend(pixels[i] & 0xFF, a);
                pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        try {
            Result r = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w, h, pixels))), hints);
            return r.getText();
        } catch (Exception e) {
            return null;
        }
    }

    private static int blend(int c, int a) {
        return (c * a + 255 * (255 - a)) / 255;
    }

    private static BufferedImage toBuffered(Image image) {
        if (image instanceof BufferedImage) return (BufferedImage) image;
        if (image == null) return null;
        int w = image.getWidth(null), h = image.getHeight(null);
        if (w <= 0 || h <= 0) return null;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.createGraphics().drawImage(image, 0, 0, null);
        return img;
    }

    // ---------- 内部 ----------

    private static String encodeContacts(List<Contact> contacts) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < contacts.size(); i++) {
            if (i > 0) b.append(RECORD);
            String[] values = contacts.get(i).values;
            int last = values.length - 1;
            while (last > 0 && values[last].isEmpty()) last--;
            for (int f = 0; f <= last; f++) {
                if (f > 0) b.append(UNIT);
                b.append(clean(values[f]));
            }
        }
        return b.toString();
    }

    private static List<Contact> decodeContacts(String text) throws ShareException {
        List<Contact> list = new ArrayList<>();
        for (String record : text.split(String.valueOf(RECORD), -1)) {
            String[] parts = record.split(String.valueOf(UNIT), -1);
            Contact c = new Contact();
            for (int f = 0; f < FIELDS.length && f < parts.length; f++) c.values[f] = parts[f].trim();
            if (!c.name().isEmpty()) list.add(c);
        }
        if (list.isEmpty()) throw new ShareException("分享码里没有联系人");
        return list;
    }

    private static String clean(String s) {
        return s.replace(RECORD, ' ').replace(UNIT, ' ');
    }

    private static SecretKeySpec key(String password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(k, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION, true);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return out.toByteArray();
    }

    private static String inflate(byte[] data) throws ShareException {
        Inflater in = new Inflater(true);
        in.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        try {
            while (!in.finished()) {
                int n = in.inflate(buf);
                if (n == 0 && (in.needsInput() || in.needsDictionary())) throw new ShareException("分享码不完整，请完整复制");
                out.write(buf, 0, n);
                if (out.size() > MAX_PLAIN) throw new ShareException("分享码过大");
            }
        } catch (DataFormatException e) {
            throw new ShareException("分享码已损坏");
        } finally {
            in.end();
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
