package me.kekemao.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 分段标签条。黑色指示条的左右两条边各用一个弹簧：前沿先走、后沿后跟，
 * 移动时指示条会被拉长。指示条可以直接拖动，松手后弹到最近的标签。
 */
public class TabStrip extends JComponent {
    private static final int PAD = 3;
    private static final int GAP = 2;
    private static final int H = 34;
    private static final int TAB_PAD = 13;

    private final List<String> tabs = new ArrayList<>();
    private final List<IntConsumer> listeners = new ArrayList<>();
    private float[] xs = new float[0];
    private float[] ws = new float[0];
    private int selected;
    private int hovered = -1;

    private final SpringValue left = new SpringValue(this, Spring.LEAD, 0);
    private final SpringValue right = new SpringValue(this, Spring.LEAD, 0);
    private final SpringValue hoverX = new SpringValue(this, Spring.DEFAULT, 0);
    private final SpringValue hoverW = new SpringValue(this, Spring.DEFAULT, 0);
    private final SpringValue hoverA = new SpringValue(this, Spring.SNAPPY, 0);
    private final SpringValue scroll = new SpringValue(this, Spring.DEFAULT, 0);

    private boolean dragging;
    private boolean dragMoved;
    private double grab;
    private double lastX, lastT, velocity;

    public TabStrip(List<String> items) {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setTabs(items);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                double x = e.getX() + scroll.get();
                double l = left.get(), r = right.get();
                dragMoved = false;
                if (x >= l && x <= r) {
                    dragging = true;
                    grab = x - l;
                    lastX = x;
                    lastT = Motion.now();
                    velocity = 0;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging) return;
                double x = e.getX() + scroll.get();
                if (Math.abs(x - lastX) > 0.5) dragMoved = true;
                double now = Motion.now();
                double dt = Math.max(1e-3, now - lastT);
                velocity = velocity * 0.6 + ((x - lastX) / dt) * 0.4;
                lastX = x;
                lastT = now;
                double total = contentWidth();
                double nl = x - grab;
                double center = nl + (right.get() - left.get()) / 2;
                double w = widthAt(center);
                nl = Math.max(PAD, Math.min(total - PAD - w, center - w / 2));
                left.snap(nl);
                right.snap(nl + w);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragging && dragMoved) {
                    dragging = false;
                    double center = (left.get() + right.get()) / 2;
                    int i = nearest(center);
                    double l = left.get(), r = right.get();
                    double v = Math.max(-2500, Math.min(2500, velocity));
                    left.release(l, v, xs[i]);
                    right.release(r, v, xs[i] + ws[i]);
                    if (i != selected) {
                        selected = i;
                        fire();
                    }
                    return;
                }
                dragging = false;
                int i = indexAt(e.getX() + scroll.get());
                if (i >= 0) setSelected(i, true);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int i = indexAt(e.getX() + scroll.get());
                if (i != hovered) {
                    if (i >= 0) {
                        boolean first = hoverA.target() == 0;
                        if (first) {
                            hoverX.snap(xs[i]);
                            hoverW.snap(ws[i]);
                        } else {
                            hoverX.set(xs[i]);
                            hoverW.set(ws[i]);
                        }
                        hoverA.set(1);
                    } else {
                        hoverA.set(0);
                    }
                    hovered = i;
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = -1;
                hoverA.set(0);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                scrollBy(e.getPreciseWheelRotation() * 48);
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
    }

    public void setTabs(List<String> items) {
        tabs.clear();
        tabs.addAll(items);
        xs = new float[tabs.size()];
        ws = new float[tabs.size()];
        float x = PAD;
        for (int i = 0; i < tabs.size(); i++) {
            float w = MorphText.measure(tabs.get(i), Text.LABEL) + TAB_PAD * 2;
            xs[i] = x;
            ws[i] = w;
            x += w + GAP;
        }
        selected = Math.min(selected, Math.max(0, tabs.size() - 1));
        if (!tabs.isEmpty()) {
            left.snap(xs[selected]);
            right.snap(xs[selected] + ws[selected]);
        }
        revalidate();
        repaint();
    }

    public void addSelectionListener(IntConsumer l) {
        listeners.add(l);
    }

    public int getSelected() {
        return selected;
    }

    public String getSelectedTab() {
        return tabs.isEmpty() ? "" : tabs.get(selected);
    }

    public void setSelected(int i, boolean notify) {
        if (i < 0 || i >= tabs.size()) return;
        double l1 = xs[i], r1 = xs[i] + ws[i];
        boolean movingRight = l1 > left.target();
        // 前沿用快弹簧、后沿用慢弹簧：移动时指示条会被拉长
        if (movingRight) {
            right.set(r1, Spring.LEAD);
            left.set(l1, Spring.TRAIL);
        } else {
            left.set(l1, Spring.LEAD);
            right.set(r1, Spring.TRAIL);
        }
        boolean changed = i != selected;
        selected = i;
        ensureVisible(i);
        if (changed && notify) fire();
    }

    private void fire() {
        for (IntConsumer l : listeners) l.accept(selected);
    }

    private double contentWidth() {
        if (xs.length == 0) return PAD * 2;
        return xs[xs.length - 1] + ws[ws.length - 1] + PAD;
    }

    private int indexAt(double x) {
        for (int i = 0; i < xs.length; i++) {
            if (x >= xs[i] - GAP / 2.0 && x <= xs[i] + ws[i] + GAP / 2.0) return i;
        }
        return -1;
    }

    private int nearest(double center) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            double d = Math.abs(xs[i] + ws[i] / 2 - center);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** 拖动时指示条宽度在相邻标签的宽度之间平滑过渡。 */
    private double widthAt(double center) {
        if (xs.length == 0) return 0;
        for (int i = 0; i < xs.length - 1; i++) {
            double c0 = xs[i] + ws[i] / 2, c1 = xs[i + 1] + ws[i + 1] / 2;
            if (center <= c0) return ws[i];
            if (center <= c1) return Motion.lerp(ws[i], ws[i + 1], (center - c0) / (c1 - c0));
        }
        return ws[ws.length - 1];
    }

    private void scrollBy(double dx) {
        double max = Math.max(0, contentWidth() - getWidth());
        scroll.set(Math.max(0, Math.min(max, scroll.target() + dx)));
    }

    private void ensureVisible(int i) {
        if (getWidth() <= 0) return;
        double max = Math.max(0, contentWidth() - getWidth());
        double s = scroll.target();
        if (xs[i] - 24 < s) s = xs[i] - 24;
        if (xs[i] + ws[i] + 24 > s + getWidth()) s = xs[i] + ws[i] + 24 - getWidth();
        scroll.set(Math.max(0, Math.min(max, s)));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension((int) Math.ceil(contentWidth()), H + PAD * 2);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, H + PAD * 2);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(120, H + PAD * 2);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        double w = getWidth(), h = getHeight();
        double trackW = Math.min(w, contentWidth());
        RoundRectangle2D track = new RoundRectangle2D.Double(0.5, 0.5, trackW - 1, h - 1, h, h);
        g.setColor(Palette.SURFACE);
        g.fill(track);
        g.setColor(Palette.LINE);
        g.draw(track);
        g.clip(track);
        g.translate(-scroll.get(), 0);

        double ha = hoverA.get();
        if (ha > 0.01) {
            g.setColor(Palette.alpha(Palette.SURFACE_3, ha * 0.9));
            g.fill(pill(hoverX.get(), hoverW.get()));
        }

        float ty = PAD;
        for (int i = 0; i < tabs.size(); i++) {
            paintLabel(g, i, ty, Palette.INK_2);
        }

        double l = left.get(), r = right.get();
        Shape ind = pill(l, Math.max(H, r - l));
        g.setColor(Palette.INK);
        g.fill(ind);
        Graphics2D gi = (Graphics2D) g.create();
        gi.clip(ind);
        for (int i = 0; i < tabs.size(); i++) {
            paintLabel(gi, i, ty, Color.WHITE);
        }
        gi.dispose();
        g.dispose();
    }

    private Shape pill(double x, double w) {
        return new RoundRectangle2D.Double(x, PAD, w, H, H, H);
    }

    private void paintLabel(Graphics2D g, int i, float ty, Color c) {
        float base = Text.centerBaseline(g, Text.LABEL, ty, H);
        Text.draw(g, tabs.get(i), xs[i] + TAB_PAD, base, Text.LABEL, c);
    }
}
