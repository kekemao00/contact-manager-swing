package me.kekemao;

import me.kekemao.ui.Glyph;
import me.kekemao.ui.Motion;
import me.kekemao.ui.Palette;
import me.kekemao.ui.Sheet;
import me.kekemao.ui.Spring;
import me.kekemao.ui.SpringValue;
import me.kekemao.ui.Text;
import me.kekemao.ui.TextInput;
import me.kekemao.ui.TextView;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ⌘K / Ctrl+K 命令面板：顶栏的搜索入口本身展开成面板。
 * 输入即筛选联系人和操作，上下键移动（高亮块弹过去），回车执行。
 */
final class CommandPalette {
    private static final int ROW = 44;
    private static final int MAX_CONTACTS = 6;

    private static final class Item {
        final String title;
        final String detail;
        final Glyph glyph;
        final boolean contact;
        final Runnable run;

        Item(String title, String detail, Glyph glyph, boolean contact, Runnable run) {
            this.title = title;
            this.detail = detail;
            this.glyph = glyph;
            this.contact = contact;
            this.run = run;
        }
    }

    private final ContactWindow owner;
    private final TextInput input = TextInput.search("搜索联系人，或输入“新增”“导出”等命令");
    private final ResultList list = new ResultList();
    private final List<Item> actions = new ArrayList<>();
    private Sheet sheet;

    private CommandPalette(ContactWindow owner) {
        this.owner = owner;
        actions.add(new Item("新增联系人", "Ctrl+N", Glyph.PLUS, false, () -> owner.showEditSheet(null, null, null)));
        actions.add(new Item("修改选中的联系人", "Enter", Glyph.PENCIL, false, owner::openEditSheet));
        actions.add(new Item("删除选中的联系人", "Delete", Glyph.TRASH, false, owner::deleteSelected));
        actions.add(new Item("从 CSV 导入", null, Glyph.UPLOAD, false, owner::importContacts));
        actions.add(new Item("导出为 CSV", "按当前筛选", Glyph.DOWNLOAD, false, owner::exportContacts));
        actions.add(new Item("搜索框", "Ctrl+F", Glyph.SEARCH, false, owner::focusSearch));
        actions.add(new Item("修改密码", null, Glyph.LOCK, false, () -> ChangePasswordSheet.open(owner, null)));
        actions.add(new Item("退出登录", null, Glyph.LOGOUT, false, owner::logout));
    }

    static void open(ContactWindow owner, Component from) {
        new CommandPalette(owner).show(from);
    }

    private void show(Component from) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));
        input.setFieldHeight(44);
        panel.add(input, BorderLayout.NORTH);
        panel.add(list, BorderLayout.CENTER);
        JPanel hints = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        hints.setOpaque(false);
        hints.add(new TextView("↑↓ 选择", Text.SMALL, Palette.INK_3));
        hints.add(new TextView("Enter 执行", Text.SMALL, Palette.INK_3));
        hints.add(new TextView("Esc 关闭", Text.SMALL, Palette.INK_3));
        panel.add(hints, BorderLayout.SOUTH);

        refresh();
        sheet = Sheet.open(owner, panel, new Dimension(560, 12 + 50 + 8 + ROW * 8 + 8 + 24 + 10), from, null);

        input.field().getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        bind(KeyEvent.VK_DOWN, () -> list.move(1));
        bind(KeyEvent.VK_UP, () -> list.move(-1));
        bind(KeyEvent.VK_ENTER, this::runSelected);
        SwingUtilities.invokeLater(() -> input.field().requestFocusInWindow());
    }

    private void bind(int key, Runnable r) {
        String name = "palette-" + key;
        input.field().getInputMap().put(KeyStroke.getKeyStroke(key, 0), name);
        input.field().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    private void runSelected() {
        Item item = list.selectedItem();
        if (item == null || sheet.isClosing()) return;
        sheet.onClose(() -> SwingUtilities.invokeLater(item.run));
        sheet.close();
    }

    private void refresh() {
        String q = input.getText().trim();
        List<Item> items = new ArrayList<>();
        if (!q.isEmpty()) items.addAll(searchContacts(q));
        String lower = q.toLowerCase();
        for (Item a : actions) {
            if (q.isEmpty() || a.title.toLowerCase().contains(lower)) items.add(a);
        }
        list.setItems(items);
    }

    private List<Item> searchContacts(String keyword) {
        List<Item> out = new ArrayList<>();
        Map<String, Object> filters = new HashMap<>();
        filters.put("keyword", keyword);
        try (PreparedStatement st = Utils.prepareContactListStatement(filters, 0, MAX_CONTACTS);
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> c = new HashMap<>();
                c.put("c_id", rs.getString("c_id"));
                c.put("c_name", rs.getString("c_name"));
                c.put("c_nickname", Utils.toContactCategoryDisplayValue(rs.getString("c_nickname")));
                c.put("c_phone", rs.getString("c_phone"));
                c.put("c_email", rs.getString("c_email"));
                c.put("c_address", rs.getString("c_address"));
                c.put("c_company", rs.getString("c_company"));
                c.put("c_job_title", rs.getString("c_job_title"));
                c.put("notes", rs.getString("notes"));
                String detail = join(" · ", (String) c.get("c_company"), (String) c.get("c_phone"));
                out.add(new Item(String.valueOf(c.get("c_name")), detail, Glyph.USER, true, () -> owner.openContact(c)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (Utils.isBlank(p)) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p.trim());
        }
        return sb.toString();
    }

    /** 结果列表：选中高亮是一块弹簧驱动、在行间滑动的形状。 */
    private final class ResultList extends JComponent {
        private List<Item> items = new ArrayList<>();
        private int selected;
        private double itemsAt = -100;
        private final SpringValue highlightY = new SpringValue(this, Spring.DEFAULT, 0);

        ResultList() {
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = e.getY() / ROW;
                    if (i >= 0 && i < items.size() && i != selected) select(i);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = e.getY() / ROW;
                    if (i >= 0 && i < items.size()) {
                        select(i);
                        runSelected();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void setItems(List<Item> newItems) {
            items = newItems;
            itemsAt = Motion.now();
            selected = 0;
            highlightY.set(0);
            repaint();
        }

        Item selectedItem() {
            return selected >= 0 && selected < items.size() ? items.get(selected) : null;
        }

        void move(int d) {
            if (items.isEmpty()) return;
            select((selected + d + items.size()) % items.size());
        }

        private void select(int i) {
            selected = i;
            highlightY.set(i * ROW);
            Rectangle r = new Rectangle(0, i * ROW, getWidth(), ROW);
            scrollRectToVisible(r);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Text.hints(g);
            int w = getWidth();
            if (items.isEmpty()) {
                String t = "没有匹配的结果";
                float tw = Text.width(g, t, Text.BODY);
                Text.draw(g, t, (w - tw) / 2f, 60, Text.BODY, Palette.INK_3);
                g.dispose();
                return;
            }
            double hy = highlightY.get();
            g.setColor(Palette.SURFACE_2);
            g.fill(new RoundRectangle2D.Double(0, hy, w, ROW, 20, 20));
            g.setColor(Palette.ACCENT);
            g.fill(new RoundRectangle2D.Double(0, hy + 12, 3, ROW - 24, 3, 3));

            int max = Math.min(items.size(), getHeight() / ROW + 1);
            for (int i = 0; i < max; i++) {
                Item it = items.get(i);
                double a = Motion.progress(Motion.now() - itemsAt - i * 0.015, 0, 0.14);
                if (a <= 0) continue;
                Graphics2D gi = (Graphics2D) g.create();
                gi.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
                int y = i * ROW;
                boolean sel = i == selected;
                double d = 28;
                gi.setColor(sel ? Palette.SURFACE : Palette.SURFACE_2);
                gi.fill(new RoundRectangle2D.Double(12, y + (ROW - d) / 2, d, d, 10, 10));
                gi.setColor(Palette.LINE);
                gi.draw(new RoundRectangle2D.Double(12, y + (ROW - d) / 2, d, d, 10, 10));
                it.glyph.paint(gi, 12 + (d - 16) / 2, y + (ROW - 16) / 2.0, 16, sel ? Palette.INK : Palette.INK_2);
                float tx = 52;
                float base = Text.centerBaseline(gi, Text.BODY_MEDIUM, y, ROW);
                float tw = Text.draw(gi, it.title, tx, base, Text.BODY_MEDIUM, Palette.INK);
                if (it.detail != null && !it.detail.isEmpty()) {
                    String detail = Text.ellipsize(gi, it.detail, Text.SMALL, w - tx - tw - 70);
                    if (it.contact) {
                        Text.draw(gi, detail, tx + tw + 10, base, Text.SMALL, Palette.INK_3);
                    } else {
                        float dw = Text.width(gi, detail, Text.SMALL);
                        Text.draw(gi, detail, w - dw - 44, base, Text.SMALL, Palette.INK_3);
                    }
                }
                if (sel) {
                    Glyph.ENTER.paint(gi, w - 30, y + (ROW - 16) / 2.0, 16, Palette.INK_3);
                }
                gi.dispose();
            }
            if (items.size() > max || Motion.now() - itemsAt < 0.3) {
                me.kekemao.ui.Animator.animate(this, itemsAt + 0.3);
            }
            g.dispose();
        }
    }
}
