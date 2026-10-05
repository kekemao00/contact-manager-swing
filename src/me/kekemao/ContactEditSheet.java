package me.kekemao;

import me.kekemao.ui.FlatButton;
import me.kekemao.ui.Fonts;
import me.kekemao.ui.Glyph;
import me.kekemao.ui.Palette;
import me.kekemao.ui.Sheet;
import me.kekemao.ui.SheetLayout;
import me.kekemao.ui.Text;
import me.kekemao.ui.TextInput;
import me.kekemao.ui.TextView;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 新增 / 编辑联系人面板。新增时由“新增联系人”按钮变形展开，
 * 编辑时由被编辑的那一行变形展开。
 */
public final class ContactEditSheet {
    private static final String[][] FIELDS = {
            {"c_name", "姓名", "必填"},
            {"c_nickname", "分类", "选择或输入分类"},
            {"c_phone", "电话", "手机或座机"},
            {"c_email", "邮箱", "name@company.com"},
            {"c_company", "公司", "公司名称"},
            {"c_job_title", "岗位", "例如 采购经理"},
            {"c_address", "地址", "省 / 市 / 区 / 详细地址"},
            {"notes", "备注", "合作情况、提醒事项等"},
    };

    private final ContactWindow owner;
    private final Map<String, Object> contact;
    private final Map<String, TextInput> inputs = new LinkedHashMap<>();
    private Sheet sheet;

    private ContactEditSheet(ContactWindow owner, Map<String, Object> contact) {
        this.owner = owner;
        this.contact = contact;
    }

    /**
     * @param contact    为 null 时新增
     * @param from       作为展开起点的控件
     * @param fromBounds 起点矩形（相对 from，可为 null）
     */
    public static Sheet open(ContactWindow owner, Map<String, Object> contact, Component from, Rectangle fromBounds) {
        return new ContactEditSheet(owner, contact).show(from, fromBounds);
    }

    private boolean isEdit() {
        return contact != null;
    }

    private Sheet show(Component from, Rectangle fromBounds) {
        SheetLayout layout = new SheetLayout(isEdit() ? "编辑联系人" : "新增联系人",
                isEdit() ? "修改后点击保存，按 Esc 放弃" : "只有姓名是必填的");
        JPanel body = layout.body();
        body.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.weightx = 1;

        int i = 0;
        for (String[] f : FIELDS) {
            String key = f[0];
            boolean wide = "c_address".equals(key) || "notes".equals(key);
            TextInput input = "c_nickname".equals(key) ? categoryInput(f[2]) : new TextInput(f[2], null);
            inputs.put(key, input);

            int col = wide ? 0 : i % 2;
            // 前六个字段两列排布，地址和备注各占一整行
            int row = wide ? 6 + ("notes".equals(key) ? 2 : 0) : i / 2 * 2;
            c.gridx = col;
            c.gridy = row;
            c.gridwidth = wide ? 2 : 1;
            c.insets = new Insets(0, col == 1 ? 6 : 0, 4, col == 0 && !wide ? 6 : 0);
            JPanel label = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            label.setOpaque(false);
            label.add(new TextView(f[1], new Text.Style(12, Fonts.Weight.MEDIUM), Palette.INK_2));
            if ("c_name".equals(key)) {
                label.add(Box.createHorizontalStrut(4));
                label.add(new TextView("*", new Text.Style(12, Fonts.Weight.MEDIUM), Palette.ACCENT));
            }
            body.add(label, c);
            c.gridy = row + 1;
            c.insets = new Insets(0, col == 1 ? 3 : -3, 10, col == 0 && !wide ? 3 : -3);
            body.add(input, c);
            i++;
        }
        c.gridy = 100;
        c.weighty = 1;
        body.add(Box.createGlue(), c);

        if (isEdit()) {
            for (Map.Entry<String, TextInput> e : inputs.entrySet()) {
                Object v = contact.get(e.getKey());
                if (!Utils.isNull(v)) e.getValue().setText(v.toString());
            }
        }

        FlatButton cancel = new FlatButton("取消", FlatButton.Kind.SECONDARY);
        FlatButton submit = new FlatButton(isEdit() ? "保存" : "添加联系人", isEdit() ? Glyph.CHECK : Glyph.PLUS,
                FlatButton.Kind.PRIMARY);
        cancel.setFixedHeight(36);
        submit.setFixedHeight(36);
        layout.addFooter(cancel);
        layout.addFooter(submit);
        layout.addFooterLeft(new TextView("Enter 保存 · Esc 关闭", Text.SMALL, Palette.INK_3));

        sheet = Sheet.open(owner, layout, new Dimension(580, 560), from, fromBounds);
        layout.onClose(sheet::close);
        cancel.addActionListener(e -> sheet.close());
        submit.addActionListener(e -> submit());
        for (TextInput input : inputs.values()) {
            input.field().addActionListener(e -> submit());
        }
        SwingUtilities.invokeLater(() -> inputs.get("c_name").field().requestFocusInWindow());
        return sheet;
    }

    private TextInput categoryInput(String placeholder) {
        TextInput[] holder = new TextInput[1];
        holder[0] = new TextInput(placeholder, null).withAction(Glyph.CHEVRON_DOWN, () -> {
            JPopupMenu menu = new JPopupMenu();
            for (String category : Utils.getDefaultContactCategories()) {
                if (Utils.isManualCategoryOption(category)) continue;
                JMenuItem item = new JMenuItem(category);
                item.addActionListener(ev -> holder[0].setText(category));
                menu.add(item);
            }
            menu.show(holder[0], 0, holder[0].getHeight());
        });
        return holder[0];
    }

    private String value(String key) {
        TextInput input = inputs.get(key);
        return input == null ? "" : input.getText();
    }

    private void submit() {
        if (sheet.isClosing()) return;
        String name = value("c_name").trim();
        if (name.isEmpty()) {
            inputs.get("c_name").flagError();
            inputs.get("c_name").field().requestFocusInWindow();
            Theme.showMessage(owner, "请输入姓名", Theme.MESSAGE_ERROR);
            return;
        }
        String category = Utils.toContactCategoryStoredValue(value("c_nickname"));
        try {
            int affected = isEdit()
                    ? Utils.updateContact(String.valueOf(contact.get("c_id")), name, category, value("c_phone"),
                            value("c_email"), value("c_address"), value("c_company"), value("c_job_title"), value("notes"))
                    : Utils.insertContact(name, category, value("c_phone"), value("c_email"), value("c_address"),
                            value("c_company"), value("c_job_title"), value("notes"));
            if (affected > 0) {
                sheet.close();
                owner.showWindow();
                Theme.showMessage(owner, (isEdit() ? "已保存 " : "已添加 ") + name, Theme.MESSAGE_SUCCESS);
            } else {
                Theme.showMessage(owner, isEdit() ? "修改失败" : "添加失败", Theme.MESSAGE_ERROR);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Theme.showMessage(owner, "操作失败：" + ex.getMessage(), Theme.MESSAGE_ERROR);
        }
    }
}
