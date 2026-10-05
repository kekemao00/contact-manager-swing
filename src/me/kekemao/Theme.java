package me.kekemao;

import me.kekemao.ui.Fonts;
import me.kekemao.ui.Glyph;
import me.kekemao.ui.Palette;
import me.kekemao.ui.Sheet;
import me.kekemao.ui.Toast;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 全局主题入口：初始化 Swing 默认样式，并提供统一的提示与确认。
 * 颜色、字体、动效和组件本身在 {@link me.kekemao.ui} 包里。
 */
public class Theme {
    public static final int MESSAGE_SUCCESS = 0;
    public static final int MESSAGE_WARNING = 1;
    public static final int MESSAGE_ERROR = -1;

    private Theme() {}

    /** 需要在创建任何窗口之前调用。 */
    public static void init() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        FontUIResource body = new FontUIResource(Fonts.cjk(13, Fonts.Weight.REGULAR));
        for (String key : new String[]{"Label.font", "Button.font", "TextField.font", "PasswordField.font",
                "TextArea.font", "ComboBox.font", "List.font", "Table.font", "TableHeader.font",
                "MenuItem.font", "Menu.font", "PopupMenu.font", "CheckBoxMenuItem.font", "ToolTip.font",
                "OptionPane.messageFont", "OptionPane.buttonFont"}) {
            UIManager.put(key, body);
        }
        UIManager.put("Panel.background", new ColorUIResource(Palette.CANVAS));
        UIManager.put("OptionPane.background", new ColorUIResource(Palette.SURFACE));

        // 右键菜单、下拉菜单
        UIManager.put("PopupMenu.background", new ColorUIResource(Palette.SURFACE));
        UIManager.put("PopupMenu.border", new BorderUIResource(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Palette.LINE_STRONG), BorderFactory.createEmptyBorder(4, 0, 4, 0))));
        UIManager.put("MenuItem.background", new ColorUIResource(Palette.SURFACE));
        UIManager.put("MenuItem.foreground", new ColorUIResource(Palette.INK));
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(Palette.SURFACE_3));
        UIManager.put("MenuItem.selectionForeground", new ColorUIResource(Palette.INK));
        UIManager.put("MenuItem.border", new BorderUIResource(BorderFactory.createEmptyBorder(7, 12, 7, 16)));
        UIManager.put("Separator.foreground", new ColorUIResource(Palette.LINE));

        // 悬停提示：墨黑底白字
        UIManager.put("ToolTip.background", new ColorUIResource(Palette.INK));
        UIManager.put("ToolTip.foreground", new ColorUIResource(Color.WHITE));
        UIManager.put("ToolTip.border", new BorderUIResource(BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        ToolTipManager.sharedInstance().setInitialDelay(450);
    }

    /** 程序图标：墨黑圆角方块里一个通讯录图标。 */
    public static List<Image> appIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128}) {
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Palette.INK);
            double r = size * 0.28;
            g.fill(new RoundRectangle2D.Double(0, 0, size, size, r * 2, r * 2));
            double inner = size * 0.64;
            AffineScale.paint(g, Glyph.CONTACTS, (size - inner) / 2, (size - inner) / 2, inner, size / 20.0);
            g.dispose();
            icons.add(img);
        }
        return icons;
    }

    /** 图标的小尺寸版本描边会太细，这里按尺寸放大线宽。 */
    private static final class AffineScale {
        static void paint(Graphics2D g, Glyph glyph, double x, double y, double size, double strokeScale) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(x, y);
            g2.scale(Math.max(1, strokeScale), Math.max(1, strokeScale));
            double s = size / Math.max(1, strokeScale);
            glyph.paint(g2, 0, 0, s, Color.WHITE);
            g2.dispose();
        }
    }

    /** 统一的窗口外观设置。 */
    public static void decorate(Window w) {
        w.setIconImages(appIcons());
        w.setBackground(Palette.CANVAS);
    }

    /** 细描边，用于分隔区域。 */
    public static Border hairline(int top, int left, int bottom, int right) {
        return BorderFactory.createMatteBorder(top, left, bottom, right, Palette.LINE);
    }

    /**
     * 显示提示。有窗口时用顶部通知胶囊，没有窗口时（例如启动失败）退回系统对话框。
     * @param type {@link #MESSAGE_SUCCESS} / {@link #MESSAGE_WARNING} / {@link #MESSAGE_ERROR}
     */
    public static void showMessage(Component parent, String message, int type) {
        Toast.Kind kind = type == MESSAGE_ERROR ? Toast.Kind.ERROR
                : type == MESSAGE_WARNING ? Toast.Kind.WARNING : Toast.Kind.SUCCESS;
        if (parent != null && parent.isShowing() && SwingUtilities.getRootPane(parent) != null) {
            Toast.show(parent, message == null ? "" : message.replace('\n', ' '), kind);
            return;
        }
        JOptionPane.showMessageDialog(parent, message,
                type == MESSAGE_ERROR ? "出错了" : "提示",
                type == MESSAGE_ERROR ? JOptionPane.ERROR_MESSAGE
                        : type == MESSAGE_WARNING ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    /** 确认操作。返回 0 表示确认（与 JOptionPane.YES_OPTION 一致）。 */
    public static int showConfirm(Component parent, String message) {
        return showConfirm(parent, "确认操作", message, "确认", false);
    }

    public static int showConfirm(Component parent, String title, String message, String okText, boolean danger) {
        if (parent == null || !parent.isShowing()) {
            return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION);
        }
        return Sheet.confirm(parent, title, message, okText, danger) ? 0 : 1;
    }
}
