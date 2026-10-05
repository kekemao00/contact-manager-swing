package me.kekemao.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;

/**
 * 表格：没有竖线网格，悬停高亮和选中高亮各是一块会在行间滑动的形状；
 * 换页、搜索后数据刷新时，各行错开一点点时间从模糊里淡入。
 */
public class StyledTable extends JTable {
    public enum CellKind { TEXT, SECONDARY, MONO, AVATAR, CHIP, INDEX }

    private final Map<Integer, CellKind> kinds = new HashMap<>();
    private final SpringValue hoverY = new SpringValue(this, Spring.DEFAULT, 0);
    private final SpringValue hoverA = new SpringValue(this, Spring.SNAPPY, 0);
    private final SpringValue selY = new SpringValue(this, Spring.DEFAULT, 0);
    private final SpringValue selA = new SpringValue(this, Spring.SNAPPY, 0);
    private int hoverRow = -1;
    private double reloadAt = -100;
    private String emptyTitle;
    private String emptyHint;

    public StyledTable(TableModel model) {
        super(model);
        setRowHeight(46);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        // 背景、悬停和选中高亮都由 paintComponent 自己画，不让 UI 再铺一层底色盖住
        setOpaque(false);
        setBackground(Palette.SURFACE);
        setForeground(Palette.INK);
        setSelectionBackground(Palette.ACCENT_SOFT);
        setSelectionForeground(Palette.INK);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFillsViewportHeight(true);
        setFont(Fonts.cjk(13, Fonts.Weight.REGULAR));
        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        JTableHeader header = getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());
        header.setPreferredSize(new Dimension(0, 40));
        header.setBackground(Palette.SURFACE);
        header.setBorder(BorderFactory.createEmptyBorder());

        Cell cell = new Cell();
        setDefaultRenderer(Object.class, cell);
        setDefaultRenderer(Number.class, cell);
        setDefaultRenderer(Integer.class, cell);

        JTextField editor = new JTextField();
        editor.setFont(Fonts.cjk(13, Fonts.Weight.REGULAR));
        editor.setForeground(Palette.INK);
        editor.setSelectionColor(Palette.ACCENT_SOFT);
        editor.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, Palette.ACCENT),
                BorderFactory.createEmptyBorder(0, 13, 0, 12)));
        editor.setBackground(Palette.SURFACE);
        DefaultCellEditor ce = new DefaultCellEditor(editor);
        // 双击留给“打开编辑面板”，行内编辑用直接打字或 F2 开始
        ce.setClickCountToStart(Integer.MAX_VALUE);
        setDefaultEditor(Object.class, ce);

        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                trackHover(rowAtPoint(e.getPoint()));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                trackHover(rowAtPoint(e.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                trackHover(-1);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);

        getSelectionModel().addListSelectionListener(e -> {
            int r = getSelectedRow();
            if (r < 0) {
                selA.set(0);
            } else {
                double y = (double) r * getRowHeight();
                if (selA.target() == 0) selY.snap(y);
                else selY.set(y);
                selA.set(1);
            }
        });
    }

    /** 没有数据时显示的说明。 */
    public void setEmptyText(String title, String hint) {
        this.emptyTitle = title;
        this.emptyHint = hint;
        repaint();
    }

    public void setCellKind(int column, CellKind kind) {
        kinds.put(column, kind);
    }

    /** 数据刷新后调用：让各行错开淡入。 */
    public void playReload() {
        reloadAt = Motion.now();
        Animator.animate(this, reloadAt + 0.5);
    }

    private void trackHover(int row) {
        if (row == hoverRow) return;
        hoverRow = row;
        if (row < 0) {
            hoverA.set(0);
            return;
        }
        double y = (double) row * getRowHeight();
        if (hoverA.target() == 0 && hoverA.get() < 0.05) hoverY.snap(y);
        else hoverY.set(y);
        hoverA.set(1);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        Rectangle clip = g.getClipBounds();
        g.setColor(Palette.SURFACE);
        g.fill(clip);
        double w = getWidth(), rh = getRowHeight();
        double ha = hoverA.get();
        if (ha > 0.01) {
            g.setColor(Palette.alpha(Palette.SURFACE_2, ha));
            g.fill(new Rectangle2D.Double(0, hoverY.get(), w, rh));
        }
        double sa = selA.get();
        if (sa > 0.01 && getRowCount() > 0) {
            double y = selY.get();
            g.setColor(Palette.alpha(Palette.ACCENT_SOFT, sa));
            g.fill(new Rectangle2D.Double(0, y, w, rh));
            g.setColor(Palette.alpha(Palette.ACCENT, sa));
            g.fill(new RoundRectangle2D.Double(2, y + 10, 3, rh - 20, 3, 3));
        }
        g.setColor(Palette.LINE);
        g.setStroke(new BasicStroke(1f));
        for (int r = 0; r < getRowCount(); r++) {
            double y = (r + 1) * rh - 0.5;
            if (y < clip.y - rh || y > clip.y + clip.height + rh) continue;
            g.draw(new Line2D.Double(12, y, w - 12, y));
        }
        if (getRowCount() == 0 && emptyTitle != null) paintEmpty(g);
        g.dispose();
        super.paintComponent(g0);
    }

    private void paintEmpty(Graphics2D g) {
        java.awt.Container vp = getParent();
        double vw = vp != null ? vp.getWidth() : getWidth();
        double vh = vp != null ? vp.getHeight() : getHeight();
        double a = Motion.progress(Motion.now() - reloadAt, 0.05, 0.3);
        if (a <= 0) return;
        Graphics2D ge = (Graphics2D) g.create();
        ge.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
        double cy = vh / 2 - 40;
        double d = 44;
        ge.setColor(Palette.SURFACE_2);
        ge.fill(new Ellipse2D.Double(vw / 2 - d / 2, cy - d / 2, d, d));
        Glyph.SEARCH.paint(ge, vw / 2 - 10, cy - 10, 20, Palette.INK_2);
        float tw = Text.width(ge, emptyTitle, Text.TITLE);
        Text.draw(ge, emptyTitle, (float) (vw / 2 - tw / 2), (float) (cy + d / 2 + 30), Text.TITLE, Palette.INK);
        if (emptyHint != null) {
            float hw = Text.width(ge, emptyHint, Text.BODY);
            Text.draw(ge, emptyHint, (float) (vw / 2 - hw / 2), (float) (cy + d / 2 + 52), Text.BODY, Palette.INK_2);
        }
        ge.dispose();
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (c instanceof JComponent) ((JComponent) c).setOpaque(false);
        return c;
    }

    /** 统一的单元格渲染器：按列的类型画文字、编号、头像或标签。 */
    private final class Cell extends JComponent implements TableCellRenderer {
        private String value = "";
        private CellKind kind = CellKind.TEXT;
        private int row;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object v, boolean selected, boolean focus, int r, int col) {
            value = v == null ? "" : v.toString();
            kind = kinds.getOrDefault(convertColumnIndexToModel(col), CellKind.TEXT);
            row = r;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Text.hints(g);
            double dt = Motion.now() - reloadAt - row * 0.022;
            double a = Motion.progress(dt, 0, 0.2);
            if (a < 1) {
                if (a <= 0) {
                    g.dispose();
                    return;
                }
                double e = 1 - Math.pow(1 - a, 3);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) e));
                g.translate(0, (1 - e) * 5);
            }
            int w = getWidth(), h = getHeight();
            float x = 14;
            switch (kind) {
                case INDEX: {
                    float base = Text.centerBaseline(g, Text.MONO, 0, h);
                    Text.draw(g, value, x, base, new Text.Style(12, Fonts.Weight.REGULAR, true), Palette.INK_3);
                    break;
                }
                case MONO: {
                    Text.Style s = new Text.Style(12.5f, Fonts.Weight.REGULAR, true);
                    float base = Text.centerBaseline(g, s, 0, h);
                    Text.draw(g, Text.ellipsize(g, value, s, w - x - 10), x, base, s, Palette.INK);
                    break;
                }
                case AVATAR: {
                    double d = 26;
                    double ay = (h - d) / 2;
                    g.setColor(Palette.SURFACE_3);
                    g.fill(new Ellipse2D.Double(x, ay, d, d));
                    String initial = value.isEmpty() ? "" : value.substring(0, value.offsetByCodePoints(0, 1));
                    Text.Style is = new Text.Style(11.5f, Fonts.Weight.MEDIUM);
                    float iw = Text.width(g, initial, is);
                    Text.draw(g, initial, (float) (x + (d - iw) / 2), Text.centerBaseline(g, is, (float) ay, (float) d), is, Palette.INK);
                    float tx = (float) (x + d + 10);
                    Text.draw(g, Text.ellipsize(g, value, Text.BODY_MEDIUM, w - tx - 8), tx,
                            Text.centerBaseline(g, Text.BODY_MEDIUM, 0, h), Text.BODY_MEDIUM, Palette.INK);
                    break;
                }
                case CHIP: {
                    if (value.isEmpty()) break;
                    Text.Style s = Text.CAPTION;
                    String t = Text.ellipsize(g, value, s, w - x - 30);
                    float tw = Text.width(g, t, s);
                    double ch = 22, cy = (h - ch) / 2;
                    RoundRectangle2D chip = new RoundRectangle2D.Double(x, cy, tw + 18, ch, ch, ch);
                    g.setColor(Palette.SURFACE);
                    g.fill(chip);
                    g.setColor(Palette.LINE_STRONG);
                    g.draw(chip);
                    Text.draw(g, t, x + 9, Text.centerBaseline(g, s, (float) cy, (float) ch), s, Palette.INK);
                    break;
                }
                case SECONDARY: {
                    Text.draw(g, Text.ellipsize(g, value, Text.BODY, w - x - 10), x,
                            Text.centerBaseline(g, Text.BODY, 0, h), Text.BODY, Palette.INK_2);
                    break;
                }
                default: {
                    Text.draw(g, Text.ellipsize(g, value, Text.BODY, w - x - 10), x,
                            Text.centerBaseline(g, Text.BODY, 0, h), Text.BODY, Palette.INK);
                }
            }
            g.dispose();
        }
    }

    private static final class HeaderRenderer extends JComponent implements TableCellRenderer {
        private String value = "";

        @Override
        public Component getTableCellRendererComponent(JTable table, Object v, boolean s, boolean f, int r, int c) {
            value = v == null ? "" : v.toString();
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Text.hints(g);
            g.setColor(Palette.SURFACE);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Palette.LINE);
            g.fillRect(0, getHeight() - 1, getWidth(), 1);
            Text.draw(g, value, 14, Text.centerBaseline(g, Text.CAPTION, 0, getHeight()), Text.CAPTION, Palette.INK_3);
            g.dispose();
        }
    }

    /** 配套的细滚动条。 */
    public static void styleScrollPane(javax.swing.JScrollPane sp) {
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Palette.SURFACE);
        sp.setBackground(Palette.SURFACE);
        sp.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new SlimScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 10));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(10, 10));
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getHorizontalScrollBar().setOpaque(false);
        sp.setCorner(javax.swing.ScrollPaneConstants.UPPER_RIGHT_CORNER, filler());
        sp.setCorner(javax.swing.ScrollPaneConstants.LOWER_RIGHT_CORNER, filler());
    }

    private static JComponent filler() {
        JComponent c = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Palette.SURFACE);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        return c;
    }
}
