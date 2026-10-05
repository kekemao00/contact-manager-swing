package me.kekemao.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.RoundRectangle2D;

/**
 * 线性图标。全部在 24×24 网格里画，缩放的是形状而不是线宽，
 * 所以任何尺寸下描边都是同样的 1.5px，粗细统一。
 */
public enum Glyph {
    SEARCH, PLUS, PENCIL, TRASH, DOWNLOAD, UPLOAD, CHEVRON_LEFT, CHEVRON_RIGHT, CHEVRON_DOWN,
    CHECK, X, USER, LOCK, EYE, EYE_OFF, COMMAND, CONTACTS, RESET, ALERT, INFO, FOLDER,
    LOGOUT, ENTER, COPY, ARROW_UP_DOWN;

    public static final float STROKE = 1.5f;

    private Shape shape;

    public Shape shape() {
        if (shape == null) shape = build(this);
        return shape;
    }

    private static Path2D.Float p() {
        return new Path2D.Float();
    }

    private static Shape build(Glyph g) {
        Path2D.Float s = p();
        switch (g) {
            case SEARCH:
                s.append(new Ellipse2D.Float(4, 4, 14, 14), false);
                line(s, 16.2f, 16.2f, 20.5f, 20.5f);
                break;
            case PLUS:
                line(s, 12, 5, 12, 19);
                line(s, 5, 12, 19, 12);
                break;
            case PENCIL:
                s.moveTo(4, 20); s.lineTo(4.5f, 16); s.lineTo(15.5f, 5); s.lineTo(19, 8.5f);
                s.lineTo(8, 19.5f); s.closePath();
                line(s, 13, 7.5f, 16.5f, 11);
                break;
            case TRASH:
                line(s, 4, 7, 20, 7);
                s.moveTo(9, 7); s.lineTo(9, 4.5f); s.lineTo(15, 4.5f); s.lineTo(15, 7);
                s.moveTo(6.5f, 7); s.lineTo(7.5f, 19.5f); s.lineTo(16.5f, 19.5f); s.lineTo(17.5f, 7);
                line(s, 10, 11, 10, 16);
                line(s, 14, 11, 14, 16);
                break;
            case DOWNLOAD:
                line(s, 12, 4, 12, 15);
                s.moveTo(7.5f, 10.5f); s.lineTo(12, 15); s.lineTo(16.5f, 10.5f);
                line(s, 5, 19.5f, 19, 19.5f);
                break;
            case UPLOAD:
                line(s, 12, 15, 12, 4);
                s.moveTo(7.5f, 8.5f); s.lineTo(12, 4); s.lineTo(16.5f, 8.5f);
                line(s, 5, 19.5f, 19, 19.5f);
                break;
            case CHEVRON_LEFT:
                s.moveTo(14.5f, 6); s.lineTo(8.5f, 12); s.lineTo(14.5f, 18);
                break;
            case CHEVRON_RIGHT:
                s.moveTo(9.5f, 6); s.lineTo(15.5f, 12); s.lineTo(9.5f, 18);
                break;
            case CHEVRON_DOWN:
                s.moveTo(6, 9.5f); s.lineTo(12, 15.5f); s.lineTo(18, 9.5f);
                break;
            case CHECK:
                s.moveTo(5, 12.5f); s.lineTo(10, 17.5f); s.lineTo(19, 7);
                break;
            case X:
                line(s, 6.5f, 6.5f, 17.5f, 17.5f);
                line(s, 17.5f, 6.5f, 6.5f, 17.5f);
                break;
            case USER:
                s.append(new Ellipse2D.Float(8, 4, 8, 8), false);
                s.moveTo(4.5f, 20); s.curveTo(4.5f, 16.5f, 8, 14.5f, 12, 14.5f);
                s.curveTo(16, 14.5f, 19.5f, 16.5f, 19.5f, 20);
                break;
            case LOCK:
                s.append(new RoundRectangle2D.Float(5, 11, 14, 9.5f, 4, 4), false);
                s.moveTo(8.5f, 11); s.lineTo(8.5f, 8);
                s.append(new Arc2D.Float(8.5f, 4.5f, 7, 7, 180, -180, Arc2D.OPEN), true);
                s.lineTo(15.5f, 11);
                break;
            case EYE:
            case EYE_OFF:
                s.moveTo(2.5f, 12); s.curveTo(5, 7, 8.5f, 5.5f, 12, 5.5f);
                s.curveTo(15.5f, 5.5f, 19, 7, 21.5f, 12);
                s.curveTo(19, 17, 15.5f, 18.5f, 12, 18.5f);
                s.curveTo(8.5f, 18.5f, 5, 17, 2.5f, 12); s.closePath();
                s.append(new Ellipse2D.Float(9, 9, 6, 6), false);
                if (g == EYE_OFF) line(s, 4, 4, 20, 20);
                break;
            case COMMAND:
                s.append(new Arc2D.Float(3, 3, 6, 6, 0, 270, Arc2D.OPEN), false);
                s.append(new Arc2D.Float(15, 3, 6, 6, 270, 270, Arc2D.OPEN), false);
                s.append(new Arc2D.Float(15, 15, 6, 6, 180, 270, Arc2D.OPEN), false);
                s.append(new Arc2D.Float(3, 15, 6, 6, 90, 270, Arc2D.OPEN), false);
                line(s, 9, 6, 9, 18);
                line(s, 15, 6, 15, 18);
                line(s, 6, 9, 18, 9);
                line(s, 6, 15, 18, 15);
                break;
            case CONTACTS:
                s.append(new RoundRectangle2D.Float(5.5f, 3.5f, 14, 17, 5, 5), false);
                s.append(new Ellipse2D.Float(10, 7.5f, 5, 5), false);
                s.moveTo(9, 16.5f); s.curveTo(9.5f, 15, 11, 14.2f, 12.5f, 14.2f);
                s.curveTo(14, 14.2f, 15.5f, 15, 16, 16.5f);
                line(s, 3.5f, 8, 5.5f, 8);
                line(s, 3.5f, 12, 5.5f, 12);
                line(s, 3.5f, 16, 5.5f, 16);
                break;
            case RESET:
                s.append(new Arc2D.Float(4, 4, 16, 16, 150, -300, Arc2D.OPEN), false);
                s.moveTo(4.6f, 4.8f); s.lineTo(5.07f, 8f); s.lineTo(8.3f, 7.6f);
                break;
            case ALERT:
                s.append(new Ellipse2D.Float(3, 3, 18, 18), false);
                line(s, 12, 7.5f, 12, 12.5f);
                line(s, 12, 16, 12, 16.2f);
                break;
            case INFO:
                s.append(new Ellipse2D.Float(3, 3, 18, 18), false);
                line(s, 12, 11, 12, 16.5f);
                line(s, 12, 7.8f, 12, 8f);
                break;
            case FOLDER:
                s.moveTo(3.5f, 7); s.lineTo(3.5f, 18); s.quadTo(3.5f, 19.5f, 5, 19.5f);
                s.lineTo(19, 19.5f); s.quadTo(20.5f, 19.5f, 20.5f, 18); s.lineTo(20.5f, 9.5f);
                s.quadTo(20.5f, 8, 19, 8); s.lineTo(12, 8); s.lineTo(10, 5.5f); s.lineTo(5, 5.5f);
                s.quadTo(3.5f, 5.5f, 3.5f, 7); s.closePath();
                break;
            case LOGOUT:
                s.moveTo(14, 4.5f); s.lineTo(17.5f, 4.5f); s.quadTo(19.5f, 4.5f, 19.5f, 6.5f);
                s.lineTo(19.5f, 17.5f); s.quadTo(19.5f, 19.5f, 17.5f, 19.5f); s.lineTo(14, 19.5f);
                s.moveTo(9, 8); s.lineTo(5, 12); s.lineTo(9, 16);
                line(s, 5, 12, 15, 12);
                break;
            case ENTER:
                s.moveTo(19, 5.5f); s.lineTo(19, 11); s.quadTo(19, 14, 16, 14); s.lineTo(5.5f, 14);
                s.moveTo(9.5f, 10); s.lineTo(5.5f, 14); s.lineTo(9.5f, 18);
                break;
            case COPY:
                s.append(new RoundRectangle2D.Float(8.5f, 8.5f, 12, 12, 4, 4), false);
                s.moveTo(15.5f, 8.5f); s.lineTo(15.5f, 5.5f); s.quadTo(15.5f, 3.5f, 13.5f, 3.5f);
                s.lineTo(5.5f, 3.5f); s.quadTo(3.5f, 3.5f, 3.5f, 5.5f); s.lineTo(3.5f, 13.5f);
                s.quadTo(3.5f, 15.5f, 5.5f, 15.5f); s.lineTo(8.5f, 15.5f);
                break;
            case ARROW_UP_DOWN:
                s.moveTo(8, 9); s.lineTo(12, 5); s.lineTo(16, 9);
                s.moveTo(8, 15); s.lineTo(12, 19); s.lineTo(16, 15);
                break;
        }
        return s;
    }

    private static void line(Path2D.Float s, float x1, float y1, float x2, float y2) {
        s.moveTo(x1, y1);
        s.lineTo(x2, y2);
    }

    /** 以 (x, y) 为左上角画一个 size×size 的图标。 */
    public void paint(Graphics2D g, double x, double y, double size, Color color) {
        paint(g, x, y, size, color, 1);
    }

    /** progress 小于 1 时只画出路径的前一段（用来做“自己画出来”的对勾等）。 */
    public void paint(Graphics2D g, double x, double y, double size, Color color, double progress) {
        if (progress <= 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        Text.hints(g2);
        AffineTransform at = new AffineTransform();
        at.translate(x, y);
        at.scale(size / 24.0, size / 24.0);
        Shape s = at.createTransformedShape(shape());
        if (progress < 1) s = trim(s, progress);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(s);
        g2.dispose();
    }

    public Icon icon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Glyph.this.paint((Graphics2D) g, x, y, size, color);
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /** 截取路径总长度的前 t（0~1）部分。 */
    public static Shape trim(Shape shape, double t) {
        double budget = length(shape) * Motion.clamp01(t);
        Path2D.Double out = new Path2D.Double();
        double[] c = new double[6];
        double px = 0, py = 0, mx = 0, my = 0;
        for (PathIterator it = new FlatteningPathIterator(shape.getPathIterator(null), 0.25); !it.isDone() && budget > 0; it.next()) {
            int type = it.currentSegment(c);
            if (type == PathIterator.SEG_MOVETO) {
                out.moveTo(c[0], c[1]);
                px = mx = c[0];
                py = my = c[1];
                continue;
            }
            double tx = type == PathIterator.SEG_CLOSE ? mx : c[0];
            double ty = type == PathIterator.SEG_CLOSE ? my : c[1];
            double len = Math.hypot(tx - px, ty - py);
            double f = len <= budget ? 1 : budget / len;
            out.lineTo(px + (tx - px) * f, py + (ty - py) * f);
            budget -= len;
            px = tx;
            py = ty;
        }
        return out;
    }

    private static double length(Shape shape) {
        double[] c = new double[6];
        double total = 0, px = 0, py = 0, mx = 0, my = 0;
        for (PathIterator it = new FlatteningPathIterator(shape.getPathIterator(null), 0.25); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            if (type == PathIterator.SEG_MOVETO) {
                px = mx = c[0];
                py = my = c[1];
                continue;
            }
            double tx = type == PathIterator.SEG_CLOSE ? mx : c[0];
            double ty = type == PathIterator.SEG_CLOSE ? my : c[1];
            total += Math.hypot(tx - px, ty - py);
            px = tx;
            py = ty;
        }
        return total;
    }
}
