package me.kekemao;

import com.google.zxing.common.BitMatrix;
import me.kekemao.ui.FlatButton;
import me.kekemao.ui.Fonts;
import me.kekemao.ui.Glyph;
import me.kekemao.ui.LogoMark;
import me.kekemao.ui.Palette;
import me.kekemao.ui.Sheet;
import me.kekemao.ui.SheetLayout;
import me.kekemao.ui.Text;
import me.kekemao.ui.TextInput;
import me.kekemao.ui.TextView;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分享联系人：左边是二维码，右边是分享码和可选密码；下面一行可以换成在线口令。
 * 复制分享码或二维码图片发给对方，对方复制后回到通讯录就会提示导入。
 */
final class ShareSheet {
    private static final int QR_BOX = 212;

    private final ContactWindow owner;
    private final List<ContactShare.Contact> contacts;
    private final QrView qrView = new QrView();
    private final CodeView codeView = new CodeView();
    private TextInput password;
    private TextView hint;
    private FlatButton copyImage;
    private FlatButton onlineButton;
    private TextView onlineHint;
    private String code;
    private String token;
    private Timer regenerate;
    private Sheet sheet;

    private ShareSheet(ContactWindow owner, List<ContactShare.Contact> contacts) {
        this.owner = owner;
        this.contacts = contacts;
    }

    static Sheet open(ContactWindow owner, List<Map<String, Object>> contacts, Component from) {
        List<ContactShare.Contact> list = new ArrayList<>();
        for (Map<String, Object> c : contacts) list.add(ContactShare.Contact.of(c));
        return new ShareSheet(owner, list).show(from);
    }

    private Sheet show(Component from) {
        SheetLayout layout = new SheetLayout("分享 " + contacts.size() + " 位联系人", names(contacts, 6));
        JPanel body = layout.body();
        body.setLayout(new BorderLayout(0, 16));

        JPanel top = new JPanel(new BorderLayout(20, 0));
        top.setOpaque(false);
        top.add(qrView, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(left(new TextView("分享码", Text.LABEL, Palette.INK_2)));
        right.add(Box.createVerticalStrut(6));
        codeView.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(codeView);
        right.add(Box.createVerticalStrut(14));
        right.add(left(new TextView("加密（可选）", Text.LABEL, Palette.INK_2)));
        right.add(Box.createVerticalStrut(6));
        password = TextInput.password("设置一个密码");
        password.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(password);
        right.add(Box.createVerticalStrut(6));
        hint = new TextView("", Text.SMALL, Palette.INK_3);
        right.add(left(hint));
        right.add(Box.createVerticalGlue());
        top.add(right, BorderLayout.CENTER);
        body.add(top, BorderLayout.CENTER);
        body.add(buildOnlineRow(), BorderLayout.SOUTH);

        copyImage = new FlatButton("复制二维码图片", Glyph.QR, FlatButton.Kind.SECONDARY);
        FlatButton copyCode = new FlatButton("复制分享码", Glyph.COPY, FlatButton.Kind.PRIMARY);
        copyImage.setFixedHeight(36);
        copyCode.setFixedHeight(36);
        copyImage.setToolTipText("把带二维码的卡片复制成图片，可以直接粘贴到聊天窗口");
        copyCode.setToolTipText("复制一段文字，对方复制后打开通讯录即可导入");
        layout.addFooter(copyImage);
        layout.addFooter(copyCode);
        layout.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override
            public Component getDefaultComponent(Container c) {
                return copyCode;
            }
        });
        layout.setFocusTraversalPolicyProvider(true);

        regenerate = new Timer(260, e -> refreshCode());
        regenerate.setRepeats(false);
        password.field().getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { regenerate.restart(); }
            public void removeUpdate(DocumentEvent e) { regenerate.restart(); }
            public void changedUpdate(DocumentEvent e) { regenerate.restart(); }
        });
        refreshCode();

        sheet = Sheet.open(owner, layout, new Dimension(600, 488), from, null);
        layout.onClose(sheet::close);
        copyCode.addActionListener(e -> copyCode());
        copyImage.addActionListener(e -> copyImage());
        password.field().addActionListener(e -> copyCode());
        return sheet;
    }

    private JComponent buildOnlineRow() {
        JPanel row = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Text.hints(g);
                RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 24, 24);
                g.setColor(Palette.SURFACE_2);
                g.fill(r);
                g.setColor(Palette.LINE);
                g.draw(r);
                g.dispose();
            }
        };
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 10));
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(left(new TextView("在线口令", Text.BODY_MEDIUM, Palette.INK).glyph(Glyph.KEY)));
        texts.add(Box.createVerticalStrut(2));
        onlineHint = new TextView("分享码太长发不出去时，换成 16 位口令，电话里也能念", Text.SMALL, Palette.INK_2);
        texts.add(left(onlineHint));
        row.add(texts, BorderLayout.CENTER);
        onlineButton = new FlatButton("生成口令", FlatButton.Kind.SECONDARY);
        onlineButton.setFixedHeight(34);
        onlineButton.setToolTipText("经开源的 ntfy 公共服务器中转，内容先用口令加密，服务器看不到；"
                + "约 12 小时后自动删除，不需要注册");
        onlineButton.addActionListener(e -> onOnline());
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);
        wrap.add(onlineButton);
        row.add(wrap, BorderLayout.EAST);
        return row;
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension p = c.getPreferredSize();
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.height));
        return c;
    }

    private String passwordText() {
        return new String(((JPasswordField) password.field()).getPassword());
    }

    private void refreshCode() {
        String pw = passwordText();
        code = ContactShare.encode(contacts, pw);
        codeView.setCode(code);
        BitMatrix m = ContactShare.qr(code);
        qrView.setMatrix(m);
        copyImage.setEnabled(m != null);
        hint.setText(pw.isEmpty() ? "不设密码时，拿到分享码的人都能导入" : "对方导入时需要输入这个密码，请另外告诉对方");
        hint.setColor(pw.isEmpty() ? Palette.INK_3 : Palette.INK_2);
    }

    private void copyCode() {
        if (regenerate.isRunning()) {
            regenerate.stop();
            refreshCode();
        }
        boolean encrypted = !passwordText().isEmpty();
        ShareInbox.ignore(code);
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(ContactShare.message(contacts, code, encrypted)), null);
        sheet.close();
        Theme.showMessage(owner, "分享码已复制，粘贴到聊天里发给对方", Theme.MESSAGE_SUCCESS);
    }

    private void copyImage() {
        if (regenerate.isRunning()) {
            regenerate.stop();
            refreshCode();
        }
        BitMatrix m = ContactShare.qr(code);
        if (m == null) return;
        BufferedImage card = renderCard(contacts, m, !passwordText().isEmpty());
        ShareInbox.ignore(code);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(card), null);
        sheet.close();
        Theme.showMessage(owner, "二维码图片已复制，粘贴到聊天里发给对方", Theme.MESSAGE_SUCCESS);
    }

    private void onOnline() {
        if (token != null) {
            copyToken();
            return;
        }
        onlineButton.setEnabled(false);
        onlineButton.setText("正在生成…");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return ShareRelay.send(contacts);
            }

            @Override
            protected void done() {
                onlineButton.setEnabled(true);
                try {
                    token = get();
                    onlineButton.setText(token);
                    onlineButton.setGlyph(Glyph.COPY);
                    onlineButton.setToolTipText("点击复制口令");
                    copyToken();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    onlineButton.setText("重试");
                    String reason = cause instanceof java.io.IOException && cause.getMessage() != null
                            && !cause.getMessage().isEmpty() && cause.getMessage().matches(".*[\\u4e00-\\u9fa5].*")
                            ? cause.getMessage() : "连不上 " + ShareRelay.serverName() + "，请检查网络或改用分享码";
                    onlineHint.setText(reason);
                    onlineHint.setColor(Palette.DANGER);
                }
            }
        }.execute();
    }

    private void copyToken() {
        ShareInbox.ignore(token);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(
                "【通讯录】联系人口令 " + token + "\n打开通讯录粘贴口令即可领取，12 小时内有效。"), null);
        onlineHint.setColor(Palette.INK_2);
        onlineHint.setText("口令已复制，12 小时内有效，对方粘贴即可领取");
        Theme.showMessage(owner, "口令已复制", Theme.MESSAGE_SUCCESS);
    }

    static String names(List<ContactShare.Contact> contacts, int max) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < contacts.size() && i < max; i++) {
            if (i > 0) b.append("、");
            b.append(contacts.get(i).name());
        }
        if (contacts.size() > max) b.append(" 等 ").append(contacts.size()).append(" 位");
        return b.toString();
    }

    // ---------- 分享卡片图片 ----------

    /** 发到聊天里的图片：标志、标题、名字和一个大二维码。按 2 倍尺寸绘制，在高分屏上也清楚。 */
    static BufferedImage renderCard(List<ContactShare.Contact> contacts, BitMatrix m, boolean encrypted) {
        int scale = 2, w = 400, pad = 28, qr = w - pad * 2;
        int h = pad + 40 + 20 + qr + 20 + 18 + pad;
        BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(scale, scale);
        Text.hints(g);
        g.setColor(Palette.SURFACE);
        g.fillRect(0, 0, w, h);
        LogoMark logo = new LogoMark(36);
        logo.setSize(36, 36);
        Graphics2D gl = (Graphics2D) g.create(pad, pad + 2, 36, 36);
        logo.paint(gl);
        gl.dispose();
        float tx = pad + 48;
        Text.draw(g, "通讯录 · 分享了 " + contacts.size() + " 位联系人", tx, pad + 17, Text.TITLE, Palette.INK);
        Text.draw(g, Text.ellipsize(g, names(contacts, 4), Text.SMALL, w - tx - pad), tx, pad + 36, Text.SMALL, Palette.INK_2);
        int qy = pad + 40 + 20;
        paintMatrix(g, m, pad, qy, qr);
        String foot = encrypted ? "复制这张图片，打开通讯录并输入密码即可导入" : "复制这张图片，打开通讯录即可导入";
        float fw = Text.width(g, foot, Text.SMALL);
        Text.draw(g, foot, (w - fw) / 2, qy + qr + 20 + 12, Text.SMALL, Palette.INK_3);
        g.dispose();
        return img;
    }

    /** 画在 size×size 的正方形里，四周留出识别需要的空白。 */
    static void paintMatrix(Graphics2D g, BitMatrix m, double x, double y, double size) {
        int n = m.getWidth();
        double quiet = 2;
        double cell = size / (n + quiet * 2);
        g.setColor(Palette.INK);
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (!m.get(c, r)) continue;
                int x0 = (int) Math.round(x + (c + quiet) * cell), y0 = (int) Math.round(y + (r + quiet) * cell);
                int x1 = (int) Math.round(x + (c + 1 + quiet) * cell), y1 = (int) Math.round(y + (r + 1 + quiet) * cell);
                g.fillRect(x0, y0, x1 - x0, y1 - y0);
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    private static final class ImageSelection implements Transferable {
        private final Image image;

        ImageSelection(Image image) {
            this.image = image;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        public Object getTransferData(DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
            return image;
        }
    }

    /** 白底圆角框里的二维码；联系人太多画不下时显示说明。 */
    private static final class QrView extends JComponent {
        private BitMatrix matrix;

        QrView() {
            setPreferredSize(new Dimension(QR_BOX, QR_BOX));
        }

        void setMatrix(BitMatrix m) {
            matrix = m;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Text.hints(g);
            int s = Math.min(getWidth(), QR_BOX);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, s - 1, s - 1, 28, 28);
            g.setColor(Palette.SURFACE);
            g.fill(box);
            g.setColor(Palette.LINE);
            g.draw(box);
            if (matrix != null) {
                paintMatrix(g, matrix, 10, 10, s - 20);
            } else {
                String[] lines = {"联系人较多，", "二维码放不下", "请用分享码或在线口令"};
                Glyph.QR.paint(g, (s - 20) / 2.0, s / 2.0 - 46, 20, Palette.INK_3);
                for (int i = 0; i < lines.length; i++) {
                    Text.Style st = i == 2 ? Text.SMALL : Text.BODY_MEDIUM;
                    float lw = Text.width(g, lines[i], st);
                    Text.draw(g, lines[i], (s - lw) / 2, s / 2f - 2 + i * 20, st, i == 2 ? Palette.INK_3 : Palette.INK_2);
                }
            }
            g.dispose();
        }
    }

    /** 分享码预览：等宽字体，按字符折行，最多显示几行，末尾省略。 */
    private static final class CodeView extends JComponent {
        private static final int LINES = 4;
        private static final Text.Style STYLE = new Text.Style(12, Fonts.Weight.REGULAR, true);
        private String code = "";

        CodeView() {
            setPreferredSize(new Dimension(300, LINES * 19 + 22));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, LINES * 19 + 22));
        }

        void setCode(String c) {
            code = c;
            setToolTipText(c.length() + " 个字符");
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Text.hints(g);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 20, 20);
            g.setColor(Palette.SURFACE_2);
            g.fill(box);
            g.setColor(Palette.LINE);
            g.draw(box);
            float maxW = getWidth() - 28;
            int i = 0;
            for (int line = 0; line < LINES && i < code.length(); line++) {
                int j = i;
                while (j < code.length() && Text.width(g, code.substring(i, j + 1), STYLE) <= maxW) j++;
                String part = code.substring(i, j);
                if (line == LINES - 1 && j < code.length()) {
                    while (part.length() > 1 && Text.width(g, part + "…", STYLE) > maxW) part = part.substring(0, part.length() - 1);
                    part += "…";
                }
                Text.draw(g, part, 14, 11 + 14 + line * 19, STYLE, Palette.INK_2);
                i = j;
            }
            g.dispose();
        }
    }
}
