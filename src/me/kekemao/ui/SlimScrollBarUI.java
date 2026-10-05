package me.kekemao.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** 细滚动条：没有箭头按钮，滑块悬停时变粗变深。 */
public class SlimScrollBarUI extends BasicScrollBarUI {
    private SpringValue hot;

    @Override
    protected void installListeners() {
        super.installListeners();
        hot = new SpringValue(scrollbar, Spring.SNAPPY, 0);
        scrollbar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hot.set(1);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isDragging) hot.set(0);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!scrollbar.contains(e.getPoint())) hot.set(0);
            }
        });
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return zero();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return zero();
    }

    private static JButton zero() {
        JButton b = new JButton();
        Dimension d = new Dimension(0, 0);
        b.setPreferredSize(d);
        b.setMinimumSize(d);
        b.setMaximumSize(d);
        return b;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
        // 轨道透明
    }

    @Override
    protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        double h = hot == null ? 0 : hot.get();
        double thick = 5 + 3 * h;
        boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
        RoundRectangle2D s = vertical
                ? new RoundRectangle2D.Double(r.x + (r.width - thick) / 2, r.y + 2, thick, r.height - 4, thick, thick)
                : new RoundRectangle2D.Double(r.x + 2, r.y + (r.height - thick) / 2, r.width - 4, thick, thick, thick);
        g.setColor(Palette.alpha(Palette.INK, 0.22 + 0.2 * h));
        g.fill(s);
        g.dispose();
    }
}
