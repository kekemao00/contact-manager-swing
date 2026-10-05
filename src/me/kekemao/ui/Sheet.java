package me.kekemao.ui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;

/**
 * 窗口内的浮层面板。打开时，触发它的那个控件（按钮、表格行）本身变形成面板：
 * 位置、尺寸、圆角和颜色一起弹过去，面板内容在一下模糊里出现；关闭时原路收回。
 */
public final class Sheet extends JComponent {
    private static final double RADIUS = 16;
    private static WeakReference<Component> lastPressed = new WeakReference<>(null);

    static {
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() == MouseEvent.MOUSE_PRESSED && e.getSource() instanceof Component) {
                lastPressed = new WeakReference<>((Component) e.getSource());
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    private final JRootPane root;
    private final JComponent content;
    private final Dimension size;
    private Rectangle2D origin;
    private Color originColor;
    private FlatButton originButton;
    private double originRadius;
    private final SpringValue open = new SpringValue(this, Spring.SMOOTH, 0);
    private final SpringValue scrim = new SpringValue(this, Spring.SNAPPY, 0);
    private boolean closing;
    private boolean dismissOnScrim = true;
    private Runnable onClose;
    private BufferedImage snapshot;
    private final BufferedImage[] blurred = new BufferedImage[5];
    private double snapScale = 1;

    private Sheet(JRootPane root, JComponent content, Dimension size) {
        this.root = root;
        this.content = content;
        this.size = size;
        setOpaque(false);
        setLayout(null);
        setFocusCycleRoot(true);
        add(content);
        // 吃掉落在遮罩上的鼠标事件，不让它们穿到下面的窗口
        MouseAdapter block = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (dismissOnScrim && !targetBounds().contains(e.getPoint())) close();
            }
        };
        addMouseListener(block);
        addMouseMotionListener(new MouseAdapter() {});
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "sheet-close");
        getActionMap().put("sheet-close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
    }

    /** 从最近一次被点击的控件展开。 */
    public static Sheet open(Component anchor, JComponent content, Dimension size) {
        Component from = lastPressed.get();
        JRootPane root = SwingUtilities.getRootPane(anchor);
        if (from == null || SwingUtilities.getRootPane(from) != root || !from.isShowing()) from = null;
        return open(anchor, content, size, from, null);
    }

    /**
     * @param from       作为起点的控件（可为 null）
     * @param fromBounds 起点矩形（相对于 from；为 null 时用整个控件）
     */
    public static Sheet open(Component anchor, JComponent content, Dimension size, Component from, Rectangle fromBounds) {
        JRootPane root = SwingUtilities.getRootPane(anchor);
        Sheet s = new Sheet(root, content, size);
        JLayeredPane lp = root.getLayeredPane();
        s.setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.add(s, JLayeredPane.MODAL_LAYER);
        lp.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                s.setBounds(0, 0, lp.getWidth(), lp.getHeight());
                s.layoutContent();
            }
        });
        s.layoutContent();

        Rectangle2D target = s.targetBounds();
        if (from != null) {
            Rectangle r = fromBounds != null ? fromBounds : new Rectangle(0, 0, from.getWidth(), from.getHeight());
            Point p = SwingUtilities.convertPoint(from, r.x, r.y, lp);
            s.origin = new Rectangle2D.Double(p.x, p.y, r.width, r.height);
            if (from instanceof FlatButton) {
                FlatButton b = (FlatButton) from;
                s.originColor = b.fill(0, 0);
                s.originRadius = b.radius;
                s.originButton = b;
                b.morphedAway = true;
                b.repaint();
            } else {
                s.originColor = Palette.SURFACE;
                s.originRadius = 8;
            }
        } else {
            double w = target.getWidth() * 0.92, h = target.getHeight() * 0.92;
            s.origin = new Rectangle2D.Double(target.getCenterX() - w / 2, target.getCenterY() - h / 2, w, h);
            s.originColor = Palette.SURFACE;
            s.originRadius = RADIUS;
        }
        s.takeSnapshot();
        s.open.set(1);
        s.scrim.set(1);
        lp.revalidate();
        lp.repaint();
        SwingUtilities.invokeLater(() -> {
            Component first = s.getFocusTraversalPolicy().getDefaultComponent(s);
            if (first != null) first.requestFocusInWindow();
        });
        return s;
    }

    public Sheet onClose(Runnable r) {
        this.onClose = r;
        return this;
    }

    public Sheet dismissOnScrim(boolean b) {
        this.dismissOnScrim = b;
        return this;
    }

    public JComponent content() {
        return content;
    }

    /** 收回到起点后移除自己。 */
    public void close() {
        if (closing) return;
        closing = true;
        takeSnapshot();
        open.set(0);
        scrim.set(0);
        Timer t = new Timer((int) (Spring.SMOOTH.settleTime() * 1000 * 0.55), e -> {
            java.awt.Container parent = getParent();
            if (parent != null) {
                parent.remove(this);
                parent.repaint();
            }
            if (originButton != null) {
                originButton.morphedAway = false;
                originButton.repaint();
            }
            if (onClose != null) onClose.run();
        });
        t.setRepeats(false);
        t.start();
    }

    public boolean isClosing() {
        return closing;
    }

    private Rectangle targetBounds() {
        int w = Math.min(size.width, Math.max(200, getWidth() - 48));
        int h = Math.min(size.height, Math.max(160, getHeight() - 48));
        return new Rectangle((getWidth() - w) / 2, Math.max(24, (getHeight() - h) / 2 - 12), w, h);
    }

    private void layoutContent() {
        content.setBounds(targetBounds());
        content.doLayout();
        content.validate();
    }

    private void takeSnapshot() {
        Rectangle t = targetBounds();
        if (t.width <= 0 || t.height <= 0) return;
        snapScale = getGraphicsConfiguration() != null
                ? getGraphicsConfiguration().getDefaultTransform().getScaleX() : 1;
        int w = (int) Math.ceil(t.width * snapScale), h = (int) Math.ceil(t.height * snapScale);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(snapScale, snapScale);
        content.setBounds(t);
        content.validate();
        content.printAll(g);
        g.dispose();
        snapshot = img;
        for (int i = 0; i < blurred.length; i++) blurred[i] = null;
    }

    private BufferedImage blurredSnapshot(int r) {
        if (r <= 0 || snapshot == null) return snapshot;
        r = Math.min(r, blurred.length - 1);
        if (blurred[r] == null) {
            BufferedImage copy = new BufferedImage(snapshot.getWidth(), snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(snapshot, 0, 0, null);
            g.dispose();
            int rr = (int) Math.max(1, Math.round(r * snapScale));
            MorphText.boxBlur(copy, rr);
            MorphText.boxBlur(copy, rr);
            blurred[r] = copy;
        }
        return blurred[r];
    }

    private boolean settled() {
        return !closing && !open.isMoving() && open.get() > 0.99;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        double sc = scrim.get();
        g.setColor(Palette.alpha(new Color(Palette.SCRIM.getRed(), Palette.SCRIM.getGreen(), Palette.SCRIM.getBlue(), 66), sc));
        g.fillRect(0, 0, getWidth(), getHeight());

        double p = open.get();
        if (p < 0.002 && closing) {
            g.dispose();
            return;
        }
        Rectangle t = targetBounds();
        double x = Motion.lerp(origin.getX(), t.x, p);
        double y = Motion.lerp(origin.getY(), t.y, p);
        double w = Motion.lerp(origin.getWidth(), t.width, p);
        double h = Motion.lerp(origin.getHeight(), t.height, p);
        double r = Motion.lerp(originRadius, RADIUS, Motion.clamp01(p));
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
        g.setColor(Palette.mix(originColor, Palette.SURFACE, Motion.progress(p, 0.05, 0.5)));
        g.fill(shape);
        g.setColor(Palette.alpha(Palette.LINE, Motion.progress(p, 0.3, 0.8)));
        g.setStroke(new BasicStroke(1f));
        g.draw(shape);
        g.dispose();
    }

    @Override
    protected void paintChildren(Graphics g0) {
        if (settled()) {
            super.paintChildren(g0);
            return;
        }
        double p = open.get();
        double a = Motion.progress(p, 0.55, 0.97);
        if (a <= 0.01 || snapshot == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        Text.hints(g);
        Rectangle t = targetBounds();
        double x = Motion.lerp(origin.getX(), t.x, p);
        double y = Motion.lerp(origin.getY(), t.y, p);
        double w = Motion.lerp(origin.getWidth(), t.width, p);
        double h = Motion.lerp(origin.getHeight(), t.height, p);
        double r = Motion.lerp(originRadius, RADIUS, Motion.clamp01(p));
        g.clip(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) a));
        int blur = (int) Math.round((1 - a) * 4);
        BufferedImage img = blurredSnapshot(blur);
        // 内容跟着形状的中心走，稍微缩放，不被拉伸
        double cx = x + w / 2, cy = y + h / 2;
        double s = 0.96 + 0.04 * a;
        g.translate(cx, cy);
        g.scale(s / snapScale, s / snapScale);
        g.drawImage(img, (int) Math.round(-t.width * snapScale / 2), (int) Math.round(-t.height * snapScale / 2), null);
        g.dispose();
        if (!closing && open.isMoving()) Animator.animate(this, Motion.now() + 0.05);
    }

    // ---------- 阻塞式确认 ----------

    /**
     * 显示确认面板并等待用户选择（在事件线程上调用也不会卡住界面）。
     * @return true 表示确认
     */
    public static boolean confirm(Component anchor, String title, String message, String okText, boolean danger) {
        JRootPane root = anchor == null ? null : SwingUtilities.getRootPane(anchor);
        if (root == null) return false;
        boolean[] result = {false};
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        ConfirmPanel panel = new ConfirmPanel(title, message, okText, danger);
        Sheet sheet = open(anchor, panel, panel.getPreferredSize());
        panel.onChoice(ok -> {
            result[0] = ok;
            sheet.close();
        });
        sheet.onClose(loop::exit);
        loop.enter();
        return result[0];
    }
}
