package me.kekemao;

import me.kekemao.ui.FlatButton;
import me.kekemao.ui.Fonts;
import me.kekemao.ui.Glyph;
import me.kekemao.ui.Palette;
import me.kekemao.ui.Sheet;
import me.kekemao.ui.SheetLayout;
import me.kekemao.ui.StyledTable;
import me.kekemao.ui.Text;
import me.kekemao.ui.TextView;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/** 发现新版本时的面板：版本号、更新内容，以及前往下载 / 跳过此版本。 */
final class UpdateSheet {
    private static Sheet current;

    private UpdateSheet() {}

    /**
     * @param from       面板展开的起点（例如顶部通知），可为 null
     * @param fromBounds 起点矩形（相对于 from），可为 null
     */
    static void open(JFrame window, UpdateChecker.Release release, Component from, Rectangle fromBounds) {
        if (current != null && current.getParent() != null && !current.isClosing()) return;

        String published = release.publishedAt.length() >= 10 ? " · 发布于 " + release.publishedAt.substring(0, 10) : "";
        SheetLayout layout = new SheetLayout("发现新版本 v" + release.version,
                "当前 " + UpdateChecker.displayVersion() + published);
        JPanel body = layout.body();
        body.setLayout(new BorderLayout(0, 8));

        TextView label = new TextView("更新内容", Text.LABEL, Palette.INK_2);
        body.add(label, BorderLayout.NORTH);

        JTextArea notes = new JTextArea(readableNotes(release.notes));
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setFont(Fonts.cjk(13, Fonts.Weight.REGULAR));
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
        body.add(box, BorderLayout.CENTER);

        FlatButton skip = new FlatButton("跳过此版本", FlatButton.Kind.GHOST);
        FlatButton later = new FlatButton("稍后", FlatButton.Kind.SECONDARY);
        FlatButton download = new FlatButton("前往下载", Glyph.DOWNLOAD, FlatButton.Kind.PRIMARY);
        skip.setFixedHeight(36);
        later.setFixedHeight(36);
        download.setFixedHeight(36);
        skip.setToolTipText("不再自动提醒 v" + release.version + "，可在账号菜单里手动检查");
        layout.addFooterLeft(skip);
        layout.addFooter(later);
        layout.addFooter(download);
        layout.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override
            public Component getDefaultComponent(Container c) {
                return download;
            }
        });
        layout.setFocusTraversalPolicyProvider(true);

        Dimension size = new Dimension(500, 460);
        Sheet sheet = from != null && from.isShowing()
                ? Sheet.open(window, layout, size, from, fromBounds)
                : Sheet.open(window, layout, size);
        current = sheet;
        layout.onClose(sheet::close);
        later.addActionListener(e -> sheet.close());
        skip.addActionListener(e -> {
            UpdateChecker.skipVersion(release.version);
            sheet.close();
            Theme.showMessage(window, "已跳过 v" + release.version, Theme.MESSAGE_SUCCESS);
        });
        download.addActionListener(e -> {
            sheet.close();
            UpdateChecker.openDownloadPage(window, release.url);
        });
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
}
