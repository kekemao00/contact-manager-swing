package me.kekemao;

import me.kekemao.ui.Check;
import me.kekemao.ui.FlatButton;
import me.kekemao.ui.Glyph;
import me.kekemao.ui.Palette;
import me.kekemao.ui.Sheet;
import me.kekemao.ui.SheetLayout;
import me.kekemao.ui.TabStrip;
import me.kekemao.ui.Text;
import me.kekemao.ui.TextInput;
import me.kekemao.ui.TextView;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 批量修改面板：勾选要改的字段，填一个新值，统一应用到多位联系人。
 * 没勾选的字段保持原样。适合供应商搬家、改名这类“同一家公司的人一起改”的场景。
 */
public final class BatchEditSheet {
    /** 可以批量修改的字段；姓名、电话、邮箱是每个人自己的，不放在这里。 */
    private static final String[][] FIELDS = {
            {"c_address", "地址", "新的地址"},
            {"c_company", "公司", "新的公司名称"},
            {"c_nickname", "分类", "选择或输入分类"},
            {"c_job_title", "岗位", "新的岗位"},
            {"notes", "备注", "新的备注"},
    };

    private final ContactWindow owner;
    private final List<String> selectedIds;
    private final List<Map<String, Object>> selectedContacts;
    private final int filteredTotal;
    private final Supplier<List<String>> filteredIds;
    private final Map<String, Check> checks = new LinkedHashMap<>();
    private final Map<String, TextInput> inputs = new LinkedHashMap<>();
    private TabStrip scope;
    private TextView summary;
    private FlatButton submit;
    private Sheet sheet;

    private BatchEditSheet(ContactWindow owner, List<Map<String, Object>> selectedContacts, int filteredTotal,
                           Supplier<List<String>> filteredIds) {
        this.owner = owner;
        this.selectedContacts = selectedContacts;
        this.selectedIds = new ArrayList<>();
        for (Map<String, Object> c : selectedContacts) selectedIds.add(String.valueOf(c.get("c_id")));
        this.filteredTotal = filteredTotal;
        this.filteredIds = filteredIds;
    }

    /**
     * @param selectedContacts 表格里选中的联系人（可以为空，此时直接作用于全部筛选结果）
     * @param filteredTotal    当前筛选结果的总数；没有筛选时传 0，不提供“全部筛选结果”选项
     * @param filteredIds      取全部筛选结果的编号
     */
    public static Sheet open(ContactWindow owner, List<Map<String, Object>> selectedContacts, int filteredTotal,
                             Supplier<List<String>> filteredIds, Component from) {
        return new BatchEditSheet(owner, selectedContacts, filteredTotal, filteredIds).show(from);
    }

    private boolean offerFiltered() {
        return filteredTotal > selectedIds.size();
    }

    private boolean useFiltered() {
        return selectedIds.isEmpty() || (scope != null && scope.getSelected() == 1);
    }

    private int targetCount() {
        return useFiltered() ? filteredTotal : selectedIds.size();
    }

    private Sheet show(Component from) {
        SheetLayout layout = new SheetLayout("批量修改",
                "勾选要修改的字段并填写新值，没勾选的保持不变");
        JPanel body = layout.body();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        if (offerFiltered() && !selectedIds.isEmpty()) {
            scope = new TabStrip(Arrays.asList("选中的 " + selectedIds.size() + " 位",
                    "全部筛选结果 " + filteredTotal + " 位"));
            JPanel scopeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            scopeRow.setOpaque(false);
            scopeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            scopeRow.add(scope);
            body.add(scopeRow);
            body.add(Box.createVerticalStrut(14));
            scope.addSelectionListener(i -> refreshSummary());
        }

        for (String[] f : FIELDS) {
            String key = f[0];
            Check check = new Check(f[1]);
            TextInput input = "c_nickname".equals(key) ? categoryInput(f[2]) : new TextInput(f[2], null);
            String shared = sharedValue(key);
            if (shared != null) {
                if (!shared.isEmpty()) input.setText(shared);
            } else if (selectedContacts.size() > 1) {
                input.setPlaceholder("各不相同 · " + f[2]);
            }
            checks.put(key, check);
            inputs.put(key, input);
            check.addChangeListener(on -> {
                refreshSummary();
                if (on) SwingUtilities.invokeLater(() -> {
                    input.field().requestFocusInWindow();
                    input.field().selectAll();
                });
            });
            // 一开始输入就自动勾上
            input.field().getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { touched(check); }
                public void removeUpdate(DocumentEvent e) { touched(check); }
                public void changedUpdate(DocumentEvent e) { touched(check); }
            });

            JPanel label = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            label.setOpaque(false);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.add(check);
            Dimension labelSize = new Dimension(Integer.MAX_VALUE, check.getPreferredSize().height);
            label.setMinimumSize(new Dimension(0, labelSize.height));
            label.setMaximumSize(labelSize);
            body.add(label);
            body.add(Box.createVerticalStrut(4));
            input.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(input);
            body.add(Box.createVerticalStrut(10));
        }
        body.add(Box.createVerticalGlue());

        FlatButton cancel = new FlatButton("取消", FlatButton.Kind.SECONDARY);
        submit = new FlatButton("应用", Glyph.CHECK, FlatButton.Kind.PRIMARY);
        cancel.setFixedHeight(36);
        submit.setFixedHeight(36);
        layout.addFooter(cancel);
        layout.addFooter(submit);
        summary = new TextView("", Text.SMALL, Palette.INK_2);
        layout.addFooterLeft(summary);
        refreshSummary();

        int height = 600 + (scope != null ? 50 : 0);
        sheet = Sheet.open(owner, layout, new Dimension(520, height), from, null);
        layout.onClose(sheet::close);
        cancel.addActionListener(e -> sheet.close());
        submit.addActionListener(e -> submit());
        for (TextInput input : inputs.values()) {
            input.field().addActionListener(e -> submit());
        }
        return sheet;
    }

    private void touched(Check check) {
        if (!check.isSelected()) check.setSelected(true);
    }

    /** 选中的联系人在某个字段上都一样时返回这个值，方便在原值上改；各不相同时返回 null。 */
    private String sharedValue(String key) {
        if (selectedContacts.isEmpty()) return null;
        String first = null;
        for (Map<String, Object> c : selectedContacts) {
            Object v = c.get(key);
            String s = v == null ? "" : v.toString();
            if (first == null) first = s;
            else if (!first.equals(s)) return null;
        }
        return first;
    }

    private List<String> checkedLabels() {
        List<String> labels = new ArrayList<>();
        for (String[] f : FIELDS) {
            if (checks.get(f[0]).isSelected()) labels.add(f[1]);
        }
        return labels;
    }

    private void refreshSummary() {
        List<String> labels = checkedLabels();
        int n = targetCount();
        if (labels.isEmpty()) {
            summary.setText("共 " + n + " 位联系人 · 请勾选要修改的字段");
        } else {
            summary.setText("将修改 " + n + " 位联系人的" + String.join("、", labels));
        }
        submit.setText("应用到 " + n + " 位");
        submit.setEnabled(!labels.isEmpty() && n > 0);
    }

    private void submit() {
        if (sheet.isClosing()) return;
        Map<String, String> values = new LinkedHashMap<>();
        for (String[] f : FIELDS) {
            String key = f[0];
            if (!checks.get(key).isSelected()) continue;
            String value = inputs.get(key).getText().trim();
            values.put(key, "c_nickname".equals(key) ? Utils.toContactCategoryStoredValue(value) : value);
        }
        if (values.isEmpty()) {
            Theme.showMessage(owner, "请勾选要修改的字段", Theme.MESSAGE_WARNING);
            return;
        }
        try {
            List<String> ids = useFiltered() ? filteredIds.get() : selectedIds;
            int affected = Utils.batchUpdateContacts(ids, values);
            sheet.close();
            owner.showWindow();
            Theme.showMessage(owner, "已修改 " + affected + " 位联系人", Theme.MESSAGE_SUCCESS);
        } catch (Exception ex) {
            ex.printStackTrace();
            Theme.showMessage(owner, "批量修改失败：" + ex.getMessage(), Theme.MESSAGE_ERROR);
        }
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
}
