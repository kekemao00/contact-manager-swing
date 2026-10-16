package me.kekemao;

import me.kekemao.ui.Animator;
import me.kekemao.ui.FlatButton;
import me.kekemao.ui.Fonts;
import me.kekemao.ui.Glyph;
import me.kekemao.ui.MorphButton;
import me.kekemao.ui.MorphText;
import me.kekemao.ui.Motion;
import me.kekemao.ui.Palette;
import me.kekemao.ui.Sheet;
import me.kekemao.ui.SheetLayout;
import me.kekemao.ui.Spring;
import me.kekemao.ui.SpringValue;
import me.kekemao.ui.StyledTable;
import me.kekemao.ui.Text;
import me.kekemao.ui.TextView;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 更新面板。同一个面板走完整个更新：发现新版本 → 下载（进度、速度、剩余时间）→ 校验 → 立即重启。
 * 主按钮本身在“下载并更新 → 加载 → 对勾 → 立即重启更新”之间变形，下面的进度条只是陪衬。
 * 关掉面板下载也不会停，好了之后在顶部通知里说一声。
 */
final class UpdateSheet {
    private static Sheet current;

    private final JFrame window;
    private final SheetLayout layout;
    private final ProgressStrip strip = new ProgressStrip();
    private final FlatButton left = new FlatButton("跳过此版本", FlatButton.Kind.GHOST);
    private final FlatButton secondary = new FlatButton("稍后", FlatButton.Kind.SECONDARY);
    private final MorphButton primary = new MorphButton("下载并更新");
    private final Runnable listener = this::render;
    private Sheet sheet;
    private UpdateChecker.Phase shown;
    private Timer pendingIdle;

    // 下载速度：指数平滑，避免数字乱跳
    private double speed;
    private long lastBytes = -1;
    private double lastAt;

    private UpdateSheet(JFrame window, UpdateChecker.Release release) {
        this.window = window;
        layout = new SheetLayout(titleFor(UpdateChecker.phase(), release), subtitleFor(release));
        layout.body().setLayout(new BorderLayout(0, 14));
        layout.body().add(notesBox(release.notes), BorderLayout.CENTER);
        layout.body().add(strip, BorderLayout.SOUTH);

        left.setFixedHeight(36);
        secondary.setFixedHeight(36);
        secondary.setPreferredSize(new Dimension(108, 36));
        primary.setFixedHeight(36);
        primary.setRadius(9);
        primary.setPreferredSize(new Dimension(136, 36));
        layout.addFooterLeft(left);
        layout.addFooter(secondary);
        layout.addFooter(primary);
        layout.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override
            public Component getDefaultComponent(Container c) {
                return primary;
            }
        });
        layout.setFocusTraversalPolicyProvider(true);

        primary.addActionListener(e -> onPrimary());
        secondary.addActionListener(e -> onSecondary());
        left.addActionListener(e -> onLeft());
    }

    static boolean isOpen() {
        return current != null && current.getParent() != null && !current.isClosing();
    }

    /**
     * 打开当前这次更新的面板。
     *
     * @param from       面板展开的起点（例如顶部通知），可为 null
     * @param fromBounds 起点矩形（相对于 from），可为 null
     */
    static void open(JFrame window, Component from, Rectangle fromBounds) {
        UpdateChecker.Release release = UpdateChecker.release();
        if (release == null || isOpen()) return;
        UpdateSheet s = new UpdateSheet(window, release);
        s.render();
        s.show(from, fromBounds);
        UpdateChecker.addListener(s.listener);
    }

    /** 更新完成后第一次打开时的“已更新”面板：只看更新内容。 */
    static void openInstalled(JFrame window, Updater.Installed in, Component from, Rectangle fromBounds) {
        if (isOpen()) return;
        UpdateChecker.Release r = new UpdateChecker.Release(in.to, "v" + in.to, in.notes, UpdateChecker.RELEASES_PAGE,
                in.publishedAt, null);
        UpdateSheet s = new UpdateSheet(window, r);
        s.layout.setTitle("已更新到 v" + in.to);
        s.layout.setSubtitle((in.from.isEmpty() ? "" : "从 v" + in.from + " 升级 · ") + "联系人数据已完整保留");
        s.strip.show(Glyph.CHECK, Palette.ACCENT, "更新完成", publishedText(in.publishedAt), -1, false);
        s.left.setVisible(false);
        s.secondary.setVisible(false);
        s.primary.setIdleText("好的");
        s.shown = UpdateChecker.Phase.IDLE;
        s.show(from, fromBounds);
    }

    private void show(Component from, Rectangle fromBounds) {
        Dimension size = new Dimension(520, 500);
        sheet = from != null && from.isShowing()
                ? Sheet.open(window, layout, size, from, fromBounds)
                : Sheet.open(window, layout, size);
        current = sheet;
        layout.onClose(sheet::close);
        sheet.onClose(() -> UpdateChecker.removeListener(listener));
    }

    // ================= 状态 → 界面 =================

    private void render() {
        UpdateChecker.Release r = UpdateChecker.release();
        if (r == null) {
            if (sheet != null) sheet.close();
            return;
        }
        UpdateChecker.Phase ph = UpdateChecker.phase();
        UpdateChecker.Phase before = shown;
        shown = ph;
        layout.setTitle(titleFor(ph, r));
        if (sheet != null) sheet.dismissOnScrim(ph != UpdateChecker.Phase.INSTALLING);

        if (!UpdateChecker.canInstallInApp(r)) {
            strip.show(Glyph.INFO, Palette.INK_3, UpdateChecker.manualReason(r), "", -1, false);
            buttons("跳过此版本", "稍后", "前往下载页");
            return;
        }
        long done = UpdateChecker.bytesDone(), total = UpdateChecker.bytesTotal();
        switch (ph) {
            case AVAILABLE:
                strip.show(Glyph.INFO, Palette.INK_3, "约 " + mb(r.jar.size) + " · 下载后自动校验", "联系人数据不受影响", -1, false);
                buttons("跳过此版本", "稍后", "下载并更新");
                primaryIdle();
                break;
            case DOWNLOADING: {
                double p = total > 0 ? (double) done / total : 0;
                strip.show(Glyph.DOWNLOAD, Palette.INK_2, "正在下载", downloadDetail(done, total), p, false);
                buttons("取消下载", "在后台下载", null);
                primaryLoading();
                break;
            }
            case VERIFYING:
                strip.show(Glyph.LOCK, Palette.INK_2, "正在校验文件", "SHA-256", 1, false);
                buttons("取消下载", "在后台下载", null);
                primaryLoading();
                break;
            case READY:
                strip.show(Glyph.CHECK, Palette.ACCENT, "已下载并通过校验", "重启只需几秒，联系人数据不受影响", 1, true);
                buttons(null, "退出时安装", "立即重启更新");
                if (before == UpdateChecker.Phase.DOWNLOADING || before == UpdateChecker.Phase.VERIFYING) {
                    // 加载圈先画出对勾，再展开成“立即重启更新”
                    primary.setIdleText("立即重启更新");
                    primary.success();
                    if (pendingIdle != null) pendingIdle.stop();
                    pendingIdle = new Timer(760, e -> primary.idle());
                    pendingIdle.setRepeats(false);
                    pendingIdle.start();
                } else {
                    primaryIdle();
                }
                break;
            case INSTALLING:
                strip.show(Glyph.CHECK, Palette.ACCENT, "正在安装并重启…", "", 1, true);
                buttons(null, null, null);
                primaryLoading();
                break;
            case FAILED:
                boolean fin = UpdateChecker.errorFinal();
                strip.show(Glyph.ALERT, Palette.DANGER, UpdateChecker.error(), fin ? "" : "已下载的部分会保留",
                        total > 0 && !fin ? (double) done / total : -1, false);
                strip.danger();
                buttons(fin ? null : "前往下载页", "稍后", fin ? "前往下载页" : "重试");
                primaryIdle();
                break;
            default:
                break;
        }
    }

    /** null 表示隐藏这个按钮；主按钮为 null 时保持当前的样子（加载中）。 */
    private void buttons(String leftText, String secondaryText, String primaryText) {
        left.setVisible(leftText != null);
        if (leftText != null) left.setText(leftText);
        secondary.setVisible(secondaryText != null);
        if (secondaryText != null) secondary.setText(secondaryText);
        if (primaryText != null) primary.setIdleText(primaryText);
    }

    private void primaryIdle() {
        if (pendingIdle != null && pendingIdle.isRunning()) return;
        if (primary.getState() != MorphButton.State.IDLE) primary.idle();
    }

    private void primaryLoading() {
        if (primary.getState() != MorphButton.State.LOADING) primary.loading();
    }

    // ================= 按钮 =================

    private void onPrimary() {
        UpdateChecker.Release r = UpdateChecker.release();
        if (shown == UpdateChecker.Phase.IDLE || r == null) {
            sheet.close();
            return;
        }
        if (!UpdateChecker.canInstallInApp(r) || (shown == UpdateChecker.Phase.FAILED && UpdateChecker.errorFinal())) {
            sheet.close();
            UpdateChecker.openDownloadPage(window, r.url);
            return;
        }
        switch (shown) {
            case AVAILABLE:
            case FAILED:
                speed = 0;
                lastBytes = -1;
                UpdateChecker.startDownload(window);
                break;
            case READY:
                UpdateChecker.restartNow(window);
                break;
            default:
                break;
        }
    }

    private void onSecondary() {
        UpdateChecker.Release r = UpdateChecker.release();
        sheet.close();
        if (r == null) return;
        if (shown == UpdateChecker.Phase.DOWNLOADING || shown == UpdateChecker.Phase.VERIFYING) {
            Theme.showMessage(window, "正在后台下载 v" + r.version + "，好了会告诉你", Theme.MESSAGE_SUCCESS);
        } else if (shown == UpdateChecker.Phase.READY) {
            Theme.showMessage(window, "退出程序时会自动安装 v" + r.version, Theme.MESSAGE_SUCCESS);
        }
    }

    private void onLeft() {
        UpdateChecker.Release r = UpdateChecker.release();
        if (r == null) return;
        switch (shown) {
            case DOWNLOADING:
            case VERIFYING:
                UpdateChecker.cancelDownload();
                break;
            case FAILED:
                sheet.close();
                UpdateChecker.openDownloadPage(window, r.url);
                break;
            default:
                String v = r.version;
                UpdateChecker.skip();
                Theme.showMessage(window, "已跳过 v" + v, Theme.MESSAGE_SUCCESS);
                break;
        }
    }

    // ================= 文字 =================

    private static String titleFor(UpdateChecker.Phase ph, UpdateChecker.Release r) {
        if (ph == UpdateChecker.Phase.READY || ph == UpdateChecker.Phase.INSTALLING) return "v" + r.version + " 已准备就绪";
        return "发现新版本 v" + r.version;
    }

    private static String subtitleFor(UpdateChecker.Release r) {
        String published = r.publishedAt != null && r.publishedAt.length() >= 10 ? " · 发布于 " + r.publishedAt.substring(0, 10) : "";
        return "当前 " + UpdateChecker.displayVersion() + published;
    }

    private static String publishedText(String publishedAt) {
        return publishedAt != null && publishedAt.length() >= 10 ? "发布于 " + publishedAt.substring(0, 10) : "";
    }

    private String downloadDetail(long done, long total) {
        double now = System.nanoTime() / 1e9;
        if (lastBytes >= 0 && done > lastBytes && now - lastAt >= 0.25) {
            double inst = (done - lastBytes) / (now - lastAt);
            speed = speed == 0 ? inst : speed * 0.7 + inst * 0.3;
            lastBytes = done;
            lastAt = now;
        } else if (lastBytes < 0 || done < lastBytes) {
            lastBytes = done;
            lastAt = now;
        }
        StringBuilder b = new StringBuilder();
        if (total > 0) b.append((int) (done * 100 / total)).append("% · ");
        b.append(mbShort(done)).append(" / ").append(total > 0 ? mb(total) : "?");
        if (speed > 1024) {
            b.append(" · ").append(mb((long) speed)).append("/s");
            if (total > 0) {
                long secs = (long) Math.ceil((total - done) / speed);
                b.append(" · 剩余 ").append(secs < 60 ? secs + " 秒" : (secs + 59) / 60 + " 分钟");
            }
        }
        return b.toString();
    }

    static String mb(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024 * 1024) return Math.max(1, bytes / 1024) + " KB";
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }

    private static String mbShort(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / 1048576.0);
    }

    private static JComponent notesBox(String markdown) {
        Font font = Fonts.cjk(13, Fonts.Weight.REGULAR);
        String text = readableNotes(markdown);
        // 有的中文字体没有圆点，换成间隔号以免显示成方框
        if (!font.canDisplay('•')) text = text.replace('•', '·');
        JTextArea notes = new JTextArea(text);
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setFont(font);
        notes.setForeground(Palette.INK);
        notes.setBackground(Palette.SURFACE_2);
        notes.setSelectionColor(Palette.ACCENT_SOFT);
        notes.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        notes.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(notes);
        StyledTable.styleScrollPane(scroll);
        scroll.getViewport().setBackground(Palette.SURFACE_2);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        JPanel box = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Text.hints(g);
                RoundRectangle2D r = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 14, 14);
                g.setColor(Palette.SURFACE_2);
                g.fill(r);
                g.setColor(Palette.LINE);
                g.draw(r);
                g.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        box.add(scroll, BorderLayout.CENTER);
        return box;
    }

    /** 把 Release 的 Markdown 说明整理成适合纯文本阅读的样子。 */
    static String readableNotes(String markdown) {
        if (markdown == null || markdown.isBlank()) return "这个版本没有填写更新说明。";
        List<String> out = new ArrayList<>();
        for (String raw : markdown.replace("\r", "").split("\n")) {
            String line = raw.strip();
            // Release Please 的版本标题行、GitHub 自动说明的对比链接
            if (line.matches("^#{1,3}\\s*\\[?v?\\d+\\.\\d+.*") || line.startsWith("**Full Changelog**")
                    || line.endsWith("New Contributors") || line.contains("made their first contribution")) continue;
            line = line.replaceAll("\\s*\\(\\[[0-9a-f]{7,40}]\\([^)]*\\)\\)", "");   // ([abc1234](url))
            line = line.replaceAll("\\s+by @\\S+ in https?://\\S+", "");           // by @user in url
            line = line.replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1");                  // [文字](链接)
            line = line.replaceAll("\\*\\*([^*]*)\\*\\*", "$1").replace("`", "");
            line = line.replaceAll("^#{1,6}\\s*", "");
            line = line.replaceAll("^[*-]\\s+", "•  ");
            line = stripEmoji(line).strip();
            if (line.isEmpty() && (out.isEmpty() || out.get(out.size() - 1).isEmpty())) continue;
            out.add(line);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out.isEmpty() ? "这个版本没有填写更新说明。" : String.join("\n", out);
    }

    /** 系统字体通常画不出彩色表情，去掉以免显示成方框。 */
    private static String stripEmoji(String s) {
        StringBuilder b = new StringBuilder();
        s.codePoints().forEach(cp -> {
            boolean emoji = cp >= 0x1F000 || (cp >= 0x2600 && cp <= 0x27BF) || cp == 0xFE0F || cp == 0x200D
                    || (cp >= 0x2B00 && cp <= 0x2BFF) || (cp >= 0x2300 && cp <= 0x23FF);
            if (!emoji) b.appendCodePoint(cp);
        });
        return b.toString();
    }

    /**
     * 面板底部的一行状态：图标 + 说明 + 右侧细节，下面一条细进度条。
     * 进度条的长度和颜色都是弹簧，说明文字换的时候在模糊里切换。
     */
    private static final class ProgressStrip extends JComponent {
        private static final int ROW = 20;
        private static final int TRACK_Y = 30;
        private static final int TRACK_H = 6;

        private final MorphText label = new MorphText(this, "");
        private final SpringValue fill = new SpringValue(this, Spring.DEFAULT, 0);
        private final SpringValue track = new SpringValue(this, Spring.SNAPPY, 0);
        private final SpringValue accent = new SpringValue(this, Spring.DEFAULT, 0);
        private final SpringValue danger = new SpringValue(this, Spring.SNAPPY, 0);
        private Glyph glyph = Glyph.INFO;
        private Color glyphColor = Palette.INK_3;
        private double glyphAt = -100;
        private String detail = "";

        ProgressStrip() {
            setOpaque(false);
            setPreferredSize(new Dimension(10, TRACK_Y + TRACK_H + 2));
        }

        /** progress < 0 表示不显示进度条。 */
        void show(Glyph g, Color c, String text, String detail, double progress, boolean done) {
            if (g != glyph) glyphAt = Motion.now();
            glyph = g;
            glyphColor = c;
            if (!text.equals(label.get())) label.set(text);
            this.detail = detail == null ? "" : detail;
            track.set(progress < 0 ? 0 : 1);
            if (progress >= 0) fill.set(Motion.clamp01(progress));
            accent.set(done ? 1 : 0);
            danger.set(0);
            Animator.animate(this, Motion.now() + 0.6);
            repaint();
        }

        void danger() {
            danger.set(1);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Text.hints(g);
            double w = getWidth();
            double draw = glyph == Glyph.CHECK ? 1 - Math.pow(1 - Motion.progress(Motion.now() - glyphAt, 0.05, 0.33), 3) : 1;
            glyph.paint(g, 0, (ROW - 16) / 2.0, 16, glyphColor, draw);

            Text.Style small = new Text.Style(12f, Fonts.Weight.REGULAR, true);
            float dw = detail.isEmpty() ? 0 : Text.width(g, detail, isLatin(detail) ? small : Text.SMALL);
            label.paint(g, 24, 0, (float) (w - 24 - dw - 12), ROW, Text.BODY_MEDIUM,
                    Palette.mix(Palette.INK, Palette.DANGER, danger.get()), MorphText.Align.LEFT);
            if (!detail.isEmpty()) {
                Text.Style s = isLatin(detail) ? small : Text.SMALL;
                Text.draw(g, detail, (float) (w - dw), Text.centerBaseline(g, s, 0, ROW), s, Palette.INK_3);
            }

            double t = track.get();
            if (t > 0.01) {
                Graphics2D gt = (Graphics2D) g.create();
                gt.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Motion.clamp01(t)));
                gt.setColor(Palette.SURFACE_3);
                gt.fill(new RoundRectangle2D.Double(0, TRACK_Y, w, TRACK_H, TRACK_H, TRACK_H));
                double fw = Math.max(0, Math.min(w, fill.get() * w));
                if (fw > 0.5) {
                    Color c = Palette.mix(Palette.mix(Palette.INK, Palette.ACCENT, accent.get()), Palette.DANGER, danger.get());
                    gt.setColor(c);
                    gt.fill(new RoundRectangle2D.Double(0, TRACK_Y, Math.max(TRACK_H, fw), TRACK_H, TRACK_H, TRACK_H));
                }
                gt.dispose();
            }
            g.dispose();
        }

        private static boolean isLatin(String s) {
            return s.chars().allMatch(ch -> ch < 0x2E80);
        }
    }
}
