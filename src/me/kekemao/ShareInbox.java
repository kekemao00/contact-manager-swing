package me.kekemao;

import me.kekemao.ui.Sheet;
import me.kekemao.ui.Toast;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * 剪贴板收件箱：窗口回到前台时看一眼剪贴板，里面有分享码、口令或分享二维码图片，
 * 就在顶部提示一次“点这里导入”。同一份分享只提示一次，自己刚复制出去的不提示。
 */
final class ShareInbox {
    private static final String PREF_LAST = "share.lastOffered";
    private static final Set<String> seen = Collections.synchronizedSet(new LinkedHashSet<>());
    private static volatile boolean checking;
    private static String lastImageKey;

    private ShareInbox() {}

    /** 剪贴板里找到的内容：分享码或在线口令，二选一。 */
    static final class Found {
        final ContactShare.Parsed parsed;
        final String token;
        final boolean fromImage;

        Found(ContactShare.Parsed parsed, String token, boolean fromImage) {
            this.parsed = parsed;
            this.token = token;
            this.fromImage = fromImage;
        }

        String key() {
            return parsed != null ? parsed.code : token;
        }
    }

    /** 自己复制出去的分享，不要再提示自己导入。 */
    static void ignore(String key) {
        if (key != null) seen.add(key);
    }

    static void watch(ContactWindow window) {
        window.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                check(window);
            }
        });
    }

    /** 在后台线程读剪贴板（识别图片里的二维码可能要一点时间），有新分享时回到界面线程提示。 */
    static void check(ContactWindow window) {
        if (checking) return;
        checking = true;
        Thread t = new Thread(() -> {
            try {
                Found found = read(false);
                if (found == null || seen.contains(found.key()) || found.key().equals(prefs().get(PREF_LAST, null))) return;
                SwingUtilities.invokeLater(() -> offer(window, found));
            } finally {
                checking = false;
            }
        }, "share-inbox");
        t.setDaemon(true);
        t.start();
    }

    private static void offer(ContactWindow window, Found found) {
        if (!window.isShowing() || sheetOpen(window)) return;
        seen.add(found.key());
        prefs().put(PREF_LAST, found.key());
        String message;
        if (found.token != null) message = "剪贴板里有联系人口令，点这里领取";
        else if (found.parsed.encrypted) message = "剪贴板里有加密的联系人分享，点这里输入密码导入";
        else {
            int n;
            try {
                n = found.parsed.open(null).size();
            } catch (ContactShare.ShareException e) {
                return;
            }
            message = "剪贴板里有 " + n + " 位联系人的分享，点这里导入";
        }
        Toast[] toast = new Toast[1];
        toast[0] = Toast.show(window, message, Toast.Kind.INFO,
                () -> ReceiveSheet.open(window, found, toast[0], toast[0].pillBounds()));
    }

    /**
     * 读取剪贴板里的分享。
     * @param force 为 true 时即使是上次识别过的同一张图片也重新识别
     */
    static Found read(boolean force) {
        Transferable t;
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            t = cb.getContents(null);
        } catch (Exception e) {
            return null;
        }
        if (t == null) return null;
        try {
            if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Found f = fromText((String) t.getTransferData(DataFlavor.stringFlavor), false);
                if (f != null) return f;
            }
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                String key = imageKey(img);
                if (!force && key != null && key.equals(lastImageKey)) return null;
                lastImageKey = key;
                String text = ContactShare.readQr(img);
                return text == null ? null : fromText(text, true);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static Found fromText(String text, boolean fromImage) {
        if (text == null) return null;
        ContactShare.Parsed p = ContactShare.find(text);
        if (p != null) return new Found(p, null, fromImage);
        String token = ShareRelay.findToken(text);
        return token == null ? null : new Found(null, token, fromImage);
    }

    /** 粗略区分剪贴板里是不是同一张图片，避免每次切回窗口都重新识别。 */
    private static String imageKey(Image image) {
        if (!(image instanceof BufferedImage)) return null;
        BufferedImage b = (BufferedImage) image;
        long h = b.getWidth() * 31L + b.getHeight();
        int step = Math.max(1, b.getWidth() * b.getHeight() / 4096);
        for (int i = 0; i < b.getWidth() * b.getHeight(); i += step) h = h * 131 + b.getRGB(i % b.getWidth(), i / b.getWidth());
        return Long.toHexString(h);
    }

    private static boolean sheetOpen(JFrame window) {
        for (Component c : window.getLayeredPane().getComponents()) {
            if (c instanceof Sheet && !((Sheet) c).isClosing()) return true;
        }
        return false;
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(ShareInbox.class);
    }
}
