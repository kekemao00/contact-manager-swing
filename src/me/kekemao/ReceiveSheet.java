package me.kekemao;

import me.kekemao.ui.FlatButton;
import me.kekemao.ui.Fonts;
import me.kekemao.ui.Glyph;
import me.kekemao.ui.Palette;
import me.kekemao.ui.Sheet;
import me.kekemao.ui.SheetLayout;
import me.kekemao.ui.Spring;
import me.kekemao.ui.SpringValue;
import me.kekemao.ui.StyledTable;
import me.kekemao.ui.Text;
import me.kekemao.ui.TextInput;
import me.kekemao.ui.TextView;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 导入别人分享的联系人。一个面板里依次是：粘贴分享码（没有从剪贴板读到时）→ 输入密码（加密的分享）
 * → 预览勾选 → 导入。已经在通讯录里的人（姓名和电话都相同）默认不勾选。
 */
final class ReceiveSheet {
    private static final String INPUT = "input", PASSWORD = "password", LOADING = "loading", PREVIEW = "preview";

    private final ContactWindow owner;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final List<Row> rows = new ArrayList<>();
    private final JPanel list = new JPanel();
    private TextInput codeInput;
    private TextView inputError;
    private TextInput passwordInput;
    private TextView passwordTitle;
    private TextView loadingTitle;
    private TextView previewSummary;
    private TextView footerSummary;
    private FlatButton primary;
    private FlatButton cancel;
    private String state;
    private ShareInbox.Found found;
    private Sheet sheet;

    private ReceiveSheet(ContactWindow owner) {
        this.owner = owner;
    }

    /** @param found 已经从剪贴板读到的分享；为 null 时先让用户粘贴 */
    static Sheet open(ContactWindow owner, ShareInbox.Found found, Component from, Rectangle fromBounds) {
        return new ReceiveSheet(owner).show(found, from, fromBounds);
    }

    private Sheet show(ShareInbox.Found initial, Component from, Rectangle fromBounds) {
        SheetLayout layout = new SheetLayout("导入分享的联系人", "分享码、在线口令或二维码图片都可以");
        JPanel body = layout.body();
        body.setLayout(new BorderLayout());
        deck.setOpaque(false);
        deck.add(buildInput(), INPUT);
        deck.add(buildPassword(), PASSWORD);
        deck.add(buildLoading(), LOADING);
        deck.add(buildPreview(), PREVIEW);
        body.add(deck, BorderLayout.CENTER);

        cancel = new FlatButton("取消", FlatButton.Kind.SECONDARY);
        primary = new FlatButton("下一步", FlatButton.Kind.PRIMARY);
        cancel.setFixedHeight(36);
        primary.setFixedHeight(36);
        footerSummary = new TextView("", Text.SMALL, Palette.INK_2);
        layout.addFooterLeft(footerSummary);
        layout.addFooter(cancel);
        layout.addFooter(primary);

        Dimension size = new Dimension(560, 560);
        sheet = from != null && from.isShowing()
                ? Sheet.open(owner, layout, size, from, fromBounds)
                : Sheet.open(owner, layout, size);
        layout.onClose(sheet::close);
        cancel.addActionListener(e -> sheet.close());
        primary.addActionListener(e -> next());

        if (initial != null) accept(initial);
        else go(INPUT);
        return sheet;
    }

    // ---------- 各个状态 ----------

    private JComponent buildInput() {
        JPanel col = column(Glyph.DOWNLOAD, new TextView("粘贴收到的分享码或口令", Text.TITLE, Palette.INK),
                "复制了分享二维码图片的话，点输入框右侧的按钮识别");
        codeInput = new TextInput("cm1.…  或  CM-XXXX-XXXX-XXXX-XXXX", null).withAction(Glyph.QR, this::pickImage);
        codeInput.field().getDocument().addDocumentListener(new DocumentListener() {
            void changed() {
                inputError.setText("");
                // 粘贴进来就自动识别，不用再点下一步
                ShareInbox.Found f = ShareInbox.fromText(codeInput.getText(), false);
                if (f != null) SwingUtilities.invokeLater(() -> accept(f));
            }

            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        });
        codeInput.field().addActionListener(e -> next());
        col.add(fill(codeInput));
        col.add(Box.createVerticalStrut(8));
        inputError = new TextView("", Text.SMALL, Palette.DANGER);
        col.add(fill(inputError));
        return center(col);
    }

    private JComponent buildPassword() {
        passwordTitle = new TextView("这份分享设了密码", Text.TITLE, Palette.INK);
        JPanel col = column(Glyph.LOCK, passwordTitle, "密码由分享人另外告诉你");
        passwordInput = TextInput.password("输入密码");
        passwordInput.field().addActionListener(e -> next());
        col.add(fill(passwordInput));
        return center(col);
    }

    private JComponent buildLoading() {
        loadingTitle = new TextView("正在领取口令", Text.TITLE, Palette.INK);
        JPanel col = column(Glyph.KEY, loadingTitle, "从 " + ShareRelay.serverName() + " 下载，并在本机用口令解密");
        return center(col);
    }

    private JComponent buildPreview() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        previewSummary = new TextView("", Text.SMALL, Palette.INK_2);
        head.add(previewSummary, BorderLayout.CENTER);
        FlatButton all = new FlatButton("全选", FlatButton.Kind.GHOST);
        all.setFixedHeight(28);
        all.addActionListener(e -> {
            boolean every = rows.stream().allMatch(r -> r.selected);
            for (Row r : rows) r.setSelected(!every);
            all.setText(every ? "全选" : "全不选");
            refreshSummary();
        });
        head.add(all, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        StyledTable.styleScrollPane(scroll);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        JPanel box = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Text.hints(g);
                RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 24, 24);
                g.setColor(Palette.SURFACE);
                g.fill(r);
                g.setColor(Palette.LINE);
                g.draw(r);
                g.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        box.add(scroll, BorderLayout.CENTER);
        p.add(box, BorderLayout.CENTER);
        return p;
    }

    /** 空状态式的一列：圆形图标、标题、说明，下面再放输入框。 */
    private static JPanel column(Glyph glyph, TextView title, String hint) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JComponent icon = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Text.hints(g);
                g.setColor(Palette.SURFACE_2);
                g.fill(new Ellipse2D.Double(0, 0, 44, 44));
                glyph.paint(g, 12, 12, 20, Palette.INK_2);
                g.dispose();
            }
        };
        Dimension d = new Dimension(44, 44);
        icon.setPreferredSize(d);
        icon.setMaximumSize(d);
        icon.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(icon);
        col.add(Box.createVerticalStrut(14));
        col.add(fill(title));
        col.add(Box.createVerticalStrut(4));
        col.add(fill(new TextView(hint, Text.SMALL, Palette.INK_2)));
        col.add(Box.createVerticalStrut(18));
        return col;
    }

    private static JComponent fill(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private static JComponent center(JComponent col) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        col.setPreferredSize(new Dimension(380, col.getPreferredSize().height));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 30, 0);
        p.add(col, c);
        return p;
    }

    private void go(String s) {
        state = s;
        cards.show(deck, s);
        cancel.setVisible(true);
        footerSummary.setText("");
        switch (s) {
            case INPUT:
                primary.setText("下一步");
                primary.setGlyph(null);
                primary.setEnabled(true);
                focusLater(codeInput.field());
                break;
            case PASSWORD:
                primary.setText("解锁");
                primary.setGlyph(Glyph.LOCK);
                primary.setEnabled(true);
                focusLater(passwordInput.field());
                break;
            case LOADING:
                primary.setText("领取中…");
                primary.setGlyph(null);
                primary.setEnabled(false);
                break;
            case PREVIEW:
                primary.setGlyph(Glyph.CHECK);
                refreshSummary();
                SwingUtilities.invokeLater(primary::requestFocusInWindow);
                break;
        }
        deck.revalidate();
        deck.repaint();
    }

    private static void focusLater(JComponent c) {
        SwingUtilities.invokeLater(c::requestFocusInWindow);
    }

    // ---------- 行为 ----------

    private void accept(ShareInbox.Found f) {
        if (state != null && !INPUT.equals(state)) return;
        found = f;
        if (f.token != null) {
            loadingTitle.setText("正在领取口令 " + f.token);
            go(LOADING);
            fetch(f.token);
        } else if (f.parsed.encrypted) {
            go(PASSWORD);
        } else {
            try {
                showPreview(f.parsed.open(null));
            } catch (ContactShare.ShareException e) {
                go(INPUT);
                inputError.setText(e.getMessage());
            }
        }
    }

    private void next() {
        switch (state) {
            case INPUT: {
                ShareInbox.Found f = ShareInbox.fromText(codeInput.getText(), false);
                if (f == null) {
                    codeInput.flagError();
                    inputError.setText(codeInput.getText().isBlank() ? "请粘贴分享码或口令" : "没有识别出分享码或口令，请完整复制");
                    return;
                }
                accept(f);
                break;
            }
            case PASSWORD: {
                String pw = new String(((JPasswordField) passwordInput.field()).getPassword());
                try {
                    showPreview(found.parsed.open(pw));
                } catch (ContactShare.ShareException e) {
                    passwordInput.flagError();
                    passwordTitle.setText(e.getMessage());
                    passwordInput.field().selectAll();
                }
                break;
            }
            case PREVIEW:
                importSelected();
                break;
            default:
        }
    }

    private void fetch(String token) {
        new SwingWorker<List<ContactShare.Contact>, Void>() {
            @Override
            protected List<ContactShare.Contact> doInBackground() throws Exception {
                return ShareRelay.receive(token);
            }

            @Override
            protected void done() {
                if (sheet.isClosing()) return;
                try {
                    showPreview(get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    state = null;
                    go(INPUT);
                    codeInput.setText("");
                    String msg = cause instanceof ContactShare.ShareException ? cause.getMessage()
                            : cause.getMessage() != null && cause.getMessage().matches(".*[\\u4e00-\\u9fa5].*")
                            ? cause.getMessage() : "连不上 " + ShareRelay.serverName() + "，请检查网络，或让对方改发分享码";
                    inputError.setText(msg);
                }
            }
        }.execute();
    }

    private void pickImage() {
        // 先看剪贴板里是不是一张二维码图片，不是再让用户选文件
        ShareInbox.Found f = ShareInbox.read(true);
        if (f != null) {
            accept(f);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择分享的二维码图片");
        chooser.setFileFilter(new FileNameExtensionFilter("图片 (*.png, *.jpg)", "png", "jpg", "jpeg", "bmp", "gif"));
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage img = ImageIO.read(chooser.getSelectedFile());
            String text = img == null ? null : ContactShare.readQr(img);
            ShareInbox.Found fromImage = ShareInbox.fromText(text, true);
            if (fromImage == null) {
                inputError.setText("这张图片里没有识别到联系人二维码");
                return;
            }
            accept(fromImage);
        } catch (Exception e) {
            inputError.setText("无法读取图片：" + e.getMessage());
        }
    }

    private void showPreview(List<ContactShare.Contact> contacts) {
        Set<String> existing;
        try {
            existing = Utils.existingContactKeys();
        } catch (Exception e) {
            existing = Collections.emptySet();
        }
        rows.clear();
        list.removeAll();
        for (ContactShare.Contact c : contacts) {
            boolean dup = existing.contains(Utils.contactKey(c.name(), c.get("c_phone")));
            Row r = new Row(c, dup);
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            rows.add(r);
            list.add(r);
        }
        if (found != null) ShareInbox.ignore(found.key());
        go(PREVIEW);
    }

    private void refreshSummary() {
        long dup = rows.stream().filter(r -> r.duplicate).count();
        long chosen = rows.stream().filter(r -> r.selected).count();
        String source = found == null ? "" : found.token != null ? "来自在线口令 · " : found.fromImage ? "来自二维码 · " : "";
        previewSummary.setText(source + "共 " + rows.size() + " 位"
                + (dup > 0 ? " · " + dup + " 位已在通讯录里，默认不导入" : ""));
        primary.setText(chosen > 0 ? "导入 " + chosen + " 位" : "导入");
        primary.setEnabled(chosen > 0);
    }

    private void importSelected() {
        if (sheet.isClosing()) return;
        List<String[]> chosen = new ArrayList<>();
        for (Row r : rows) if (r.selected) chosen.add(r.contact.values);
        if (chosen.isEmpty()) return;
        try {
            int n = Utils.insertContacts(chosen);
            sheet.close();
            owner.showWindow();
            Theme.showMessage(owner, "已导入 " + n + " 位联系人", Theme.MESSAGE_SUCCESS);
        } catch (Exception ex) {
            ex.printStackTrace();
            Theme.showMessage(owner, "导入失败：" + ex.getMessage(), Theme.MESSAGE_ERROR);
        }
    }

    /** 预览里的一行：勾选框、头像、姓名和电话 / 公司；已存在的人右侧有标签。 */
    private final class Row extends JComponent {
        final ContactShare.Contact contact;
        final boolean duplicate;
        boolean selected;
        private final SpringValue on;
        private final SpringValue hover = new SpringValue(this, Spring.SNAPPY, 0);

        Row(ContactShare.Contact contact, boolean duplicate) {
            this.contact = contact;
            this.duplicate = duplicate;
            this.selected = !duplicate;
            on = new SpringValue(this, Spring.SNAPPY, selected ? 1 : 0);
            setPreferredSize(new Dimension(400, 54));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover.set(1);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover.set(0);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    toggle();
                }
            });
            addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_SPACE) toggle();
                }
            });
            String tip = detail(true);
            setToolTipText(tip.isEmpty() ? null : tip);
        }

        void toggle() {
            setSelected(!selected);
            refreshSummary();
        }

        void setSelected(boolean v) {
            selected = v;
            on.set(v ? 1 : 0);
        }

        private String detail(boolean full) {
            List<String> parts = new ArrayList<>();
            for (String f : full ? new String[]{"c_phone", "c_company", "c_nickname", "c_email", "c_job_title", "c_address"}
                    : new String[]{"c_phone", "c_company", "c_nickname"}) {
                String v = contact.get(f);
                if (!v.isEmpty()) parts.add(v);
            }
            return String.join(" · ", parts);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Text.hints(g);
            int w = getWidth(), h = getHeight();
            double hv = hover.get(), a = on.get();
            if (hv > 0.01) {
                g.setColor(Palette.alpha(Palette.SURFACE_2, hv));
                g.fill(new RoundRectangle2D.Double(2, 1, w - 4, h - 2, 20, 20));
            }
            // 勾选框
            double bx = 14, by = (h - 16) / 2.0;
            RoundRectangle2D box = new RoundRectangle2D.Double(bx, by, 16, 16, 6, 6);
            g.setColor(Palette.mix(Palette.SURFACE, Palette.INK, a));
            g.fill(box);
            g.setColor(Palette.mix(Palette.mix(Palette.LINE_STRONG, Palette.INK_3, hv), Palette.INK, a));
            g.draw(box);
            if (a > 0.01) {
                Graphics2D gc = (Graphics2D) g.create();
                gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, a)));
                Glyph.CHECK.paint(gc, bx + 2, by + 2, 12, Palette.SURFACE);
                gc.dispose();
            }
            // 头像
            double d = 30, ax = 44, ay = (h - d) / 2;
            g.setColor(Palette.SURFACE_3);
            g.fill(new Ellipse2D.Double(ax, ay, d, d));
            String name = contact.name();
            String initial = name.isEmpty() ? "" : name.substring(0, name.offsetByCodePoints(0, 1));
            Text.Style is = new Text.Style(12, Fonts.Weight.MEDIUM);
            Text.draw(g, initial, (float) (ax + (d - Text.width(g, initial, is)) / 2),
                    Text.centerBaseline(g, is, (float) ay, (float) d), is, Palette.INK);
            // 右侧标签
            float right = w - 14;
            if (duplicate) {
                String chip = "已存在";
                float tw = Text.width(g, chip, Text.CAPTION);
                double cw = tw + 18, ch = 22, cx = right - cw, cy = (h - ch) / 2;
                RoundRectangle2D r = new RoundRectangle2D.Double(cx, cy, cw, ch, ch, ch);
                g.setColor(Palette.SURFACE);
                g.fill(r);
                g.setColor(Palette.LINE_STRONG);
                g.draw(r);
                Text.draw(g, chip, (float) cx + 9, Text.centerBaseline(g, Text.CAPTION, (float) cy, (float) ch), Text.CAPTION, Palette.INK_2);
                right = (float) cx - 10;
            }
            float tx = (float) (ax + d + 12);
            Color nameColor = Palette.mix(Palette.INK_2, Palette.INK, a);
            Text.draw(g, Text.ellipsize(g, name, Text.BODY_MEDIUM, right - tx), tx, h / 2f - 3, Text.BODY_MEDIUM, nameColor);
            String det = detail(false);
            if (!det.isEmpty()) {
                Text.draw(g, Text.ellipsize(g, det, Text.SMALL, right - tx), tx, h / 2f + 14, Text.SMALL, Palette.INK_3);
            }
            if (rows.indexOf(this) < rows.size() - 1) {
                g.setColor(Palette.LINE);
                g.drawLine(12, h - 1, w - 12, h - 1);
            }
            g.dispose();
        }
    }
}
