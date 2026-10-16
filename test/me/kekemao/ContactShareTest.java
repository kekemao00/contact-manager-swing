package me.kekemao;

import com.google.zxing.common.BitMatrix;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContactShareTest {

    private static List<ContactShare.Contact> sample(int n) {
        List<ContactShare.Contact> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> m = new HashMap<>();
            m.put("c_name", "联系人" + i);
            m.put("c_nickname", "OEM");
            m.put("c_phone", "1380000" + String.format("%04d", i));
            m.put("c_email", "user" + i + "@example.com");
            m.put("c_address", "深圳市南山区科技园 " + i + " 号");
            m.put("c_company", "示例科技有限公司");
            m.put("c_job_title", "采购经理");
            m.put("notes", i % 2 == 0 ? "" : "备注\n第二行");
            list.add(ContactShare.Contact.of(m));
        }
        return list;
    }

    @Test
    void plainRoundTrip() throws Exception {
        List<ContactShare.Contact> in = sample(5);
        String code = ContactShare.encode(in, null);
        assertTrue(code.startsWith("cm1."));
        ContactShare.Parsed p = ContactShare.find(code);
        assertNotNull(p);
        assertFalse(p.encrypted);
        List<ContactShare.Contact> out = p.open(null);
        assertEquals(5, out.size());
        for (int i = 0; i < 5; i++) assertArrayEquals(in.get(i).values, out.get(i).values);
    }

    @Test
    void encryptedNeedsRightPassword() throws Exception {
        String code = ContactShare.encode(sample(2), "芝麻开门");
        ContactShare.Parsed p = ContactShare.find(code);
        assertTrue(p.encrypted);
        assertThrows(ContactShare.ShareException.class, () -> p.open(null));
        ContactShare.ShareException wrong = assertThrows(ContactShare.ShareException.class, () -> p.open("123"));
        assertEquals("密码不对", wrong.getMessage());
        assertEquals("联系人1", p.open("芝麻开门").get(1).name());
    }

    @Test
    void findsCodeInsideChatMessageEvenWhenWrapped() throws Exception {
        List<ContactShare.Contact> in = sample(3);
        String code = ContactShare.encode(in, null);
        String message = ContactShare.message(in, code, false);
        // 聊天软件把长串折行、前后加了别的话
        String wrapped = "老王：\n" + message.substring(0, message.length() - 20) + "\n  " + message.substring(message.length() - 20) + "\n收到请回复";
        ContactShare.Parsed p = ContactShare.find(wrapped);
        assertNotNull(p);
        assertEquals(3, p.open(null).size());
        assertNull(ContactShare.find("没有分享码的普通文字 cm1 cm1.短"));
    }

    @Test
    void truncatedCodeIsReported() {
        String code = ContactShare.encode(sample(10), null);
        ContactShare.Parsed p = ContactShare.find(code.substring(0, code.length() / 2));
        assertNotNull(p);
        assertThrows(ContactShare.ShareException.class, () -> p.open(null));
    }

    @Test
    void qrRoundTrip() {
        String code = ContactShare.encode(sample(8), "pw");
        BitMatrix m = ContactShare.qr(code);
        assertNotNull(m);
        int scale = 4, quiet = 16;
        BufferedImage img = new BufferedImage(m.getWidth() * scale + quiet * 2, m.getHeight() * scale + quiet * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(new Color(0x141413));
        for (int y = 0; y < m.getHeight(); y++)
            for (int x = 0; x < m.getWidth(); x++)
                if (m.get(x, y)) g.fillRect(quiet + x * scale, quiet + y * scale, scale, scale);
        g.dispose();
        assertEquals(code, ContactShare.readQr(img));
    }

    @Test
    void tooManyContactsForQr() {
        assertNull(ContactShare.qr(ContactShare.encode(sample(400), null)));
    }

    @Test
    void tokenFormatAndNormalisation() {
        String t = ShareRelay.newToken();
        assertTrue(t.matches("CM(-[0-9A-HJKMNP-TV-Z]{4}){4}"), t);
        assertEquals(t, ShareRelay.findToken("口令：" + t.toLowerCase() + "，12 小时内有效"));
        assertEquals("CM-0110-ABCD-EFGH-JK00", ShareRelay.findToken("cm-oIlo-abcd-efgh-jkOO"));
        assertNull(ShareRelay.findToken("CM-ABCD-EFGH"));
        assertNotEquals(ShareRelay.topic(t), ShareRelay.topic(ShareRelay.newToken()));
    }

    /** 用一个最小的 ntfy 兼容服务验证在线口令的收发。 */
    @Test
    void relayRoundTrip() throws Exception {
        Map<String, String> topics = new HashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            byte[] out;
            if ("POST".equals(ex.getRequestMethod())) {
                try (InputStream in = ex.getRequestBody()) {
                    topics.put(path.substring(1), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
                out = "{\"event\":\"message\"}".getBytes(StandardCharsets.UTF_8);
            } else {
                String topic = path.substring(1, path.length() - "/json".length());
                String msg = topics.get(topic);
                out = (msg == null ? "" : "{\"id\":\"x\",\"event\":\"message\",\"topic\":\"" + topic
                        + "\",\"message\":\"" + msg + "\"}\n").getBytes(StandardCharsets.UTF_8);
            }
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        System.setProperty("contactmanager.relay", "http://127.0.0.1:" + server.getAddress().getPort());
        try {
            String token = ShareRelay.send(sample(4));
            assertEquals(1, topics.size());
            String stored = topics.values().iterator().next();
            // 服务器上只有密文，看不到姓名
            assertFalse(new String(java.util.Base64.getUrlDecoder().decode(stored.substring(4)), StandardCharsets.ISO_8859_1).contains("example"));
            assertTrue(ContactShare.find(stored).encrypted);
            List<ContactShare.Contact> got = ShareRelay.receive(token);
            assertEquals(4, got.size());
            assertEquals("联系人3", got.get(3).name());
            assertThrows(ContactShare.ShareException.class, () -> ShareRelay.receive(ShareRelay.newToken()));
        } finally {
            System.clearProperty("contactmanager.relay");
            server.stop(0);
        }
    }
}
