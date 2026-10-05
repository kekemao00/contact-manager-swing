package me.kekemao;

import me.kekemao.ui.Fade;
import me.kekemao.ui.FlatButton;
import me.kekemao.ui.Fonts;
import me.kekemao.ui.Glyph;
import me.kekemao.ui.LogoMark;
import me.kekemao.ui.MorphText;
import me.kekemao.ui.Palette;
import me.kekemao.ui.StyledTable;
import me.kekemao.ui.TabStrip;
import me.kekemao.ui.Text;
import me.kekemao.ui.TextInput;
import me.kekemao.ui.TextView;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactWindow extends JFrame {
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int SEARCH_DEBOUNCE_DELAY_MS = 250;
    private static final String ALL_CATEGORIES = "全部";
    private static final String[] COLUMNS = {"编号", "姓名", "分类", "电话", "邮箱", "地址", "公司", "岗位", "备注"};
    /** 表格列对应的数据库字段，null 表示不可行内编辑 */
    private static final String[] COLUMN_FIELDS = {
            null, "c_name", null, "c_phone", "c_email", "c_address", "c_company", "c_job_title", "notes"
    };

    private final List<String> visibleContactIds = new ArrayList<>();
    public Integer current = 1;
    public Integer pages = 1;
    public Integer size = DEFAULT_PAGE_SIZE;
    public Integer total = 0;

    private DefaultTableModel model;
    private StyledTable table;
    private JScrollPane scrollPane;
    private TextInput searchInput;
    private TabStrip categoryTabs;
    private TextView countLabel;
    private TextView selectionLabel;
    private TextView pageLabel;
    private TextView clockLabel;
    private FlatButton addButton;
    private FlatButton editButton;
    private FlatButton deleteButton;
    private FlatButton prevButton;
    private FlatButton nextButton;
    private FlatButton commandButton;
    private JTextField jumpField;

    private TableModelListener tableEditListener;
    private Timer keywordSearchTimer;
    private boolean adjustingPageSize;
    private Timer clockTimer;
    private me.kekemao.ui.Sheet editSheet;

    public ContactWindow() {
        initWindow();
        initActionListeners();
        refreshCategories();
        initTableData();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
        Fade.in(getRootPane());
        schedulePageSizeRefresh();
    }

    // ================= 界面 =================

    private void initWindow() {
        setTitle("通讯录");
        Theme.decorate(this);
        setResizable(true);
        setMinimumSize(new Dimension(980, 620));
        setSize(1280, 800);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Palette.CANVAS);
        root.setBorder(BorderFactory.createEmptyBorder(0, 24, 20, 24));
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel main = new JPanel(new BorderLayout(0, 14));
        main.setOpaque(false);
        main.add(buildToolbar(), BorderLayout.NORTH);
        main.add(buildTableCard(), BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(16, 0, 18, 0));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(new LogoMark(32));
        left.add(Box.createHorizontalStrut(12));
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        TextView title = new TextView("通讯录", new Text.Style(16, Fonts.Weight.SEMIBOLD), Palette.INK);
        titles.add(title);
        countLabel = new TextView("0 位联系人", Text.SMALL, Palette.INK_2);
        titles.add(countLabel);
        left.add(titles);
        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        commandButton = new FlatButton("搜索或执行命令", Glyph.COMMAND, FlatButton.Kind.SECONDARY) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(238, 34);
            }

            @Override
            protected void paintContent(Graphics2D g, double w, double ht, Color fg) {
                Glyph.SEARCH.paint(g, 12, (ht - 16) / 2, 16, Palette.INK_2);
                Text.draw(g, "搜索或执行命令", 36, Text.centerBaseline(g, Text.BODY, 0, (float) ht), Text.BODY, Palette.INK_3);
                String key = isMac() ? "⌘ K" : "Ctrl K";
                float kw = Text.width(g, key, Text.CAPTION) + 12;
                double kx = w - kw - 7, ky = (ht - 20) / 2;
                g.setColor(Palette.SURFACE_2);
                g.fill(new RoundRectangle2D.Double(kx, ky, kw, 20, 10, 10));
                g.setColor(Palette.LINE);
                g.draw(new RoundRectangle2D.Double(kx, ky, kw, 20, 10, 10));
                Text.draw(g, key, (float) kx + 6, Text.centerBaseline(g, Text.CAPTION, (float) ky, 20), Text.CAPTION, Palette.INK_2);
            }
        };
        commandButton.setToolTipText("搜索联系人、执行操作");
        right.add(commandButton);

        clockLabel = new TextView("", new Text.Style(12, Fonts.Weight.REGULAR, true), Palette.INK_3);
        clockLabel.setPreferredSize(new Dimension(118, 34));
        clockLabel.align(MorphText.Align.RIGHT);
        clockTimer = new Timer(1000, e -> clockLabel.setText(new SimpleDateFormat("MM-dd HH:mm").format(new Date())));
        clockTimer.setInitialDelay(0);
        clockTimer.start();
        right.add(clockLabel);

        String currentUser = AccountService.getCurrentUser();
        FlatButton userButton = new FlatButton(currentUser == null ? "账号" : currentUser, Glyph.USER, FlatButton.Kind.GHOST);
        userButton.addActionListener(e -> showUserMenu(userButton));
        right.add(userButton);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setOpaque(false);

        JPanel left = new JPanel(new BorderLayout(12, 0));
        left.setOpaque(false);
        searchInput = TextInput.search("搜索姓名、电话、邮箱、公司…");
        searchInput.setPreferredSize(new Dimension(320, 40));
        left.add(searchInput, BorderLayout.WEST);
        categoryTabs = new TabStrip(Collections.singletonList(ALL_CATEGORIES));
        JPanel tabsWrap = new JPanel(new BorderLayout());
        tabsWrap.setOpaque(false);
        tabsWrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        tabsWrap.add(categoryTabs, BorderLayout.CENTER);
        left.add(tabsWrap, BorderLayout.CENTER);
        bar.add(left, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
        actions.setOpaque(false);
        FlatButton importButton = new FlatButton("", Glyph.UPLOAD, FlatButton.Kind.GHOST);
        importButton.setToolTipText("从 CSV 导入");
        importButton.addActionListener(e -> importContacts());
        FlatButton exportButton = new FlatButton("", Glyph.DOWNLOAD, FlatButton.Kind.GHOST);
        exportButton.setToolTipText("导出为 CSV（按当前筛选）");
        exportButton.addActionListener(e -> exportContacts());
        deleteButton = new FlatButton("", Glyph.TRASH, FlatButton.Kind.GHOST);
        deleteButton.setToolTipText("删除选中的联系人（Delete）");
        deleteButton.addActionListener(e -> deleteSelected());
        editButton = new FlatButton("修改", Glyph.PENCIL, FlatButton.Kind.SECONDARY);
        editButton.setToolTipText("修改选中的联系人（双击行）");
        editButton.addActionListener(e -> openEditSheet());
        addButton = new FlatButton("新增联系人", Glyph.PLUS, FlatButton.Kind.PRIMARY);
        addButton.setToolTipText("新增联系人（" + (isMac() ? "⌘" : "Ctrl+") + "N）");
        addButton.addActionListener(e -> showEditSheet(null, addButton, null));
        actions.add(importButton);
        actions.add(exportButton);
        actions.add(deleteButton);
        actions.add(Box.createHorizontalStrut(4));
        actions.add(editButton);
        actions.add(addButton);
        bar.add(actions, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildTableCard() {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                Text.hints(g);
                RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 28, 28);
                g.setColor(Palette.SURFACE);
                g.fill(shape);
                g.setColor(Palette.LINE);
                g.draw(shape);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));

        model = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return COLUMN_FIELDS[column] != null;
            }
        };
        model.setColumnIdentifiers(COLUMNS);
        table = new StyledTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setCellKind(0, StyledTable.CellKind.INDEX);
        table.setCellKind(1, StyledTable.CellKind.AVATAR);
        table.setCellKind(2, StyledTable.CellKind.CHIP);
        table.setCellKind(3, StyledTable.CellKind.MONO);
        table.setCellKind(4, StyledTable.CellKind.TEXT);
        table.setCellKind(5, StyledTable.CellKind.SECONDARY);
        table.setCellKind(6, StyledTable.CellKind.TEXT);
        table.setCellKind(7, StyledTable.CellKind.SECONDARY);
        table.setCellKind(8, StyledTable.CellKind.SECONDARY);
        int[] widths = {56, 150, 130, 130, 200, 260, 170, 120, 220};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(0).setMaxWidth(64);
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                Object value = row >= 0 && col >= 0 ? table.getValueAt(row, col) : null;
                table.setToolTipText(value == null || value.toString().isEmpty() ? null : value.toString());
            }
        });
        table.setComponentPopupMenu(buildTableMenu());

        scrollPane = new JScrollPane(table);
        StyledTable.styleScrollPane(scrollPane);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refreshForViewportSizeChange();
            }
        });
        card.add(scrollPane, BorderLayout.CENTER);
        card.add(buildFooter(), BorderLayout.SOUTH);
        return card;
    }

    private JPopupMenu buildTableMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("复制单元格", Glyph.COPY.icon(16, Palette.INK_2));
        copyItem.addActionListener(ev -> {
            int row = table.getSelectedRow();
            int col = table.getSelectedColumn();
            if (row >= 0 && col >= 0) {
                Object val = table.getValueAt(row, col);
                if (val != null) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(val.toString()), null);
                    Theme.showMessage(this, "已复制", Theme.MESSAGE_SUCCESS);
                }
            }
        });
        JMenuItem editItem = new JMenuItem("修改此联系人", Glyph.PENCIL.icon(16, Palette.INK_2));
        editItem.addActionListener(ev -> openEditSheet());
        JMenuItem delItem = new JMenuItem("删除此联系人", Glyph.TRASH.icon(16, Palette.DANGER));
        delItem.setForeground(Palette.DANGER);
        delItem.addActionListener(ev -> deleteSelected());
        menu.add(copyItem);
        menu.addSeparator();
        menu.add(editItem);
        menu.add(delItem);
        // 右键时先选中鼠标下的那一行
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                SwingUtilities.invokeLater(() -> {
                    Point p = table.getMousePosition();
                    if (p != null) {
                        int row = table.rowAtPoint(p);
                        int col = table.columnAtPoint(p);
                        if (row >= 0) table.changeSelection(row, Math.max(0, col), false, false);
                    }
                });
            }

            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}

            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        });
        return menu;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createCompoundBorder(
                Theme.hairline(1, 0, 0, 0), BorderFactory.createEmptyBorder(8, 10, 8, 6)));

        selectionLabel = new TextView("", Text.SMALL, Palette.INK_2);
        selectionLabel.setPreferredSize(new Dimension(320, 34));
        footer.add(selectionLabel, BorderLayout.WEST);

        JPanel pager = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        pager.setOpaque(false);
        TextView jumpLabel = new TextView("跳至", Text.SMALL, Palette.INK_3);
        jumpLabel.setPreferredSize(new Dimension(28, 34));
        jumpField = new JTextField(3);
        jumpField.setHorizontalAlignment(JTextField.CENTER);
        jumpField.setFont(Fonts.mono(12));
        jumpField.setForeground(Palette.INK);
        jumpField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Palette.LINE, 1, true), BorderFactory.createEmptyBorder(5, 4, 5, 4)));
        jumpField.setToolTipText("输入页码后按 Enter 跳转");
        prevButton = new FlatButton("", Glyph.CHEVRON_LEFT, FlatButton.Kind.SECONDARY);
        prevButton.setToolTipText("上一页");
        nextButton = new FlatButton("", Glyph.CHEVRON_RIGHT, FlatButton.Kind.SECONDARY);
        nextButton.setToolTipText("下一页");
        pageLabel = new TextView("1 / 1", new Text.Style(12.5f, Fonts.Weight.MEDIUM, true), Palette.INK);
        pageLabel.align(MorphText.Align.CENTER);
        pageLabel.setPreferredSize(new Dimension(64, 34));
        pager.add(jumpLabel);
        pager.add(jumpField);
        pager.add(Box.createHorizontalStrut(10));
        pager.add(prevButton);
        pager.add(pageLabel);
        pager.add(nextButton);
        footer.add(pager, BorderLayout.EAST);
        return footer;
    }

    private void showUserMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem change = new JMenuItem("修改密码", Glyph.LOCK.icon(16, Palette.INK_2));
        change.addActionListener(e -> ChangePasswordSheet.open(this, null));
        JMenuItem logout = new JMenuItem("退出登录", Glyph.LOGOUT.icon(16, Palette.INK_2));
        logout.addActionListener(e -> logout());
        menu.add(change);
        menu.add(logout);
        menu.show(anchor, anchor.getWidth() - menu.getPreferredSize().width, anchor.getHeight() + 6);
    }

    static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    // ================= 行为 =================

    void logout() {
        if (Theme.showConfirm(this, "退出登录", "退出后需要重新输入密码。", "退出登录", false) != 0) {
            return;
        }
        AccountService.logout();
        dispose();
        new LoginGUI();
    }

    @Override
    public void dispose() {
        if (clockTimer != null) clockTimer.stop();
        if (keywordSearchTimer != null) keywordSearchTimer.stop();
        super.dispose();
    }

    /** 同一时间只打开一个编辑面板。 */
    void showEditSheet(Map<String, Object> contact, Component from, Rectangle fromBounds) {
        if (editSheet != null && editSheet.getParent() != null && !editSheet.isClosing()) {
            return;
        }
        editSheet = ContactEditSheet.open(this, contact, from, fromBounds);
    }

    void focusSearch() {
        searchInput.field().requestFocusInWindow();
        searchInput.field().selectAll();
    }

    void openCommandPalette() {
        CommandPalette.open(this, commandButton);
    }

    private void initActionListeners() {
        keywordSearchTimer = new Timer(SEARCH_DEBOUNCE_DELAY_MS, e -> runKeywordSearch());
        keywordSearchTimer.setRepeats(false);
        searchInput.field().getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { keywordSearchTimer.restart(); }
            public void removeUpdate(DocumentEvent e) { keywordSearchTimer.restart(); }
            public void changedUpdate(DocumentEvent e) { keywordSearchTimer.restart(); }
        });
        searchInput.field().addActionListener(e -> runKeywordSearchImmediately());
        searchInput.field().getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear-search");
        searchInput.field().getActionMap().put("clear-search", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchInput.setText("");
            }
        });
        categoryTabs.addSelectionListener(i -> runKeywordSearchImmediately());
        commandButton.addActionListener(e -> openCommandPalette());

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openEditSheet();
                }
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateSelectionState();
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteRow");
        table.getActionMap().put("deleteRow", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!table.isEditing()) deleteSelected();
            }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openRow");
        table.getActionMap().put("openRow", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!table.isEditing()) openEditSheet();
            }
        });

        prevButton.addActionListener(e -> goToPage(current - 1));
        nextButton.addActionListener(e -> goToPage(current + 1));
        jumpField.addActionListener(e -> {
            try {
                int target = Integer.parseInt(jumpField.getText().trim());
                if (target >= 1 && target <= pages) {
                    goToPage(target);
                } else {
                    Theme.showMessage(this, "页码超出范围（1 ~ " + pages + "）", Theme.MESSAGE_WARNING);
                }
            } catch (NumberFormatException ex) {
                Theme.showMessage(this, "请输入有效页码", Theme.MESSAGE_ERROR);
            } finally {
                jumpField.setText("");
            }
        });

        // 全局快捷键
        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JRootPane rp = getRootPane();
        bindKey(rp, KeyStroke.getKeyStroke(KeyEvent.VK_K, menuKey), "palette", this::openCommandPalette);
        bindKey(rp, KeyStroke.getKeyStroke(KeyEvent.VK_N, menuKey), "add", () -> showEditSheet(null, addButton, null));
        bindKey(rp, KeyStroke.getKeyStroke(KeyEvent.VK_F, menuKey), "find", this::focusSearch);
        bindKey(rp, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "prev-page", () -> goToPage(current - 1));
        bindKey(rp, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "next-page", () -> goToPage(current + 1));
    }

    private static void bindKey(JRootPane rp, KeyStroke ks, String name, Runnable r) {
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        rp.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    private void goToPage(int page) {
        if (page < 1 || page > pages) {
            prevButton.setEnabled(current > 1);
            nextButton.setEnabled(current < pages);
            return;
        }
        current = page;
        initTableData();
    }

    private void updateSelectionState() {
        int row = table.getSelectedRow();
        boolean selected = row >= 0;
        editButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
        if (selected) {
            Object name = model.getValueAt(row, 1);
            selectionLabel.setText("已选中 " + (name != null ? name : "") + " · 双击或回车编辑");
        } else {
            selectionLabel.setText(total > 0 ? "第 " + ((current - 1) * size + 1) + "–"
                    + Math.min(total, current * size) + " 条，共 " + total + " 条" : "");
        }
    }

    private void runKeywordSearch() {
        current = 1;
        initTableData();
    }

    private void runKeywordSearchImmediately() {
        if (keywordSearchTimer != null && keywordSearchTimer.isRunning()) {
            keywordSearchTimer.stop();
        }
        runKeywordSearch();
    }

    /** 根据数据里实际存在的分类刷新标签条，保留当前选中的分类。 */
    private void refreshCategories() {
        String selected = categoryTabs.getSelectedTab();
        List<String> tabs = new ArrayList<>();
        tabs.add(ALL_CATEGORIES);
        try {
            tabs.addAll(Utils.listContactCategories());
        } catch (Exception e) {
            e.printStackTrace();
        }
        categoryTabs.setTabs(tabs);
        int index = tabs.indexOf(selected);
        if (index > 0) categoryTabs.setSelected(index, false);
        else if (!ALL_CATEGORIES.equals(selected)) {
            categoryTabs.setSelected(0, false);
        }
    }

    private JFileChooser createCsvFileChooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 文件 (*.csv)", "csv"));
        return chooser;
    }

    private File ensureCsvExtension(File file) {
        if (file == null) return null;
        if (file.getName().toLowerCase().endsWith(".csv")) return file;
        File parent = file.getParentFile();
        return parent == null ? new File(file.getName() + ".csv") : new File(parent, file.getName() + ".csv");
    }

    void exportContacts() {
        JFileChooser chooser = createCsvFileChooser("导出联系人");
        chooser.setSelectedFile(new File("通讯录导出_" + Utils.formTime(new Date(), "yyyyMMdd_HHmmss") + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = ensureCsvExtension(chooser.getSelectedFile());
        if (file.exists() && Theme.showConfirm(this, "覆盖文件", "“" + file.getName() + "”已存在，要覆盖它吗？",
                "覆盖", true) != 0) {
            return;
        }
        try {
            int count = Utils.exportContactsToCsv(file, getSearchMap());
            Theme.showMessage(this, "已导出 " + count + " 条到 " + file.getName(), Theme.MESSAGE_SUCCESS);
        } catch (Exception ex) {
            ex.printStackTrace();
            Theme.showMessage(this, "导出失败：" + ex.getMessage(), Theme.MESSAGE_ERROR);
        }
    }

    void importContacts() {
        if (Theme.showConfirm(this, "导入联系人", "导入会按编号更新已有记录，编号匹配不到的记录会新增。", "选择文件", false) != 0) {
            return;
        }
        JFileChooser chooser = createCsvFileChooser("导入联系人");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            Utils.ImportResult result = Utils.importContactsFromCsv(chooser.getSelectedFile());
            current = 1;
            refreshCategories();
            initTableData();
            String message = "导入完成：新增 " + result.inserted + " 条，更新 " + result.updated + " 条";
            if (result.skipped > 0) message += "，跳过 " + result.skipped + " 条";
            Theme.showMessage(this, message, result.skipped > 0 ? Theme.MESSAGE_WARNING : Theme.MESSAGE_SUCCESS);
        } catch (Exception ex) {
            ex.printStackTrace();
            Theme.showMessage(this, "导入失败：" + ex.getMessage(), Theme.MESSAGE_ERROR);
        }
    }

    private void schedulePageSizeRefresh() {
        SwingUtilities.invokeLater(this::refreshForViewportSizeChange);
    }

    private void refreshForViewportSizeChange() {
        if (adjustingPageSize || model == null) return;
        int newSize = resolvePageSize();
        if (newSize <= 0 || newSize == size) return;
        adjustingPageSize = true;
        try {
            int firstRecordIndex = Math.max(0, (current - 1) * Math.max(1, size));
            size = newSize;
            current = firstRecordIndex / size + 1;
            initTableData();
        } finally {
            adjustingPageSize = false;
        }
    }

    private int resolvePageSize() {
        if (scrollPane == null || table == null) return Math.max(1, size);
        int viewportHeight = scrollPane.getViewport().getExtentSize().height;
        if (viewportHeight <= 0) return Math.max(1, size);
        return Math.max(1, viewportHeight / Math.max(1, table.getRowHeight()));
    }

    private Map<String, Object> contactAtRow(int row) {
        Map<String, Object> map = new HashMap<>();
        map.put("c_id", getContactIdAtRow(row));
        for (int col = 1; col < COLUMNS.length; col++) {
            String field = col == 2 ? "c_nickname" : COLUMN_FIELDS[col];
            Object value = model.getValueAt(row, col);
            map.put(field, value == null ? "" : value.toString());
        }
        return map;
    }

    void openEditSheet() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        int row = table.getSelectedRow();
        if (row < 0) {
            Theme.showMessage(this, "请先选择一位联系人", Theme.MESSAGE_WARNING);
            return;
        }
        Rectangle rowBounds = table.getCellRect(row, 0, true);
        rowBounds.width = table.getVisibleRect().width;
        rowBounds.x = table.getVisibleRect().x;
        showEditSheet(contactAtRow(row), table, rowBounds);
    }

    /** 由命令面板直接打开某个联系人。 */
    void openContact(Map<String, Object> contact) {
        showEditSheet(contact, commandButton, null);
    }

    void deleteSelected() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        int row = table.getSelectedRow();
        if (row < 0) {
            Theme.showMessage(this, "请先选择一位联系人", Theme.MESSAGE_WARNING);
            return;
        }
        Object name = model.getValueAt(row, 1);
        if (Theme.showConfirm(this, "删除联系人", "确定删除“" + name + "”吗？删除后无法恢复。", "删除", true) != 0) {
            return;
        }
        String id = getContactIdAtRow(row);
        if (Utils.isBlank(id)) {
            Theme.showMessage(this, "未找到联系人编号", Theme.MESSAGE_ERROR);
            return;
        }
        try {
            Utils.deleteContact(id);
            refreshCategories();
            initTableData();
            Theme.showMessage(this, "已删除 " + name, Theme.MESSAGE_SUCCESS);
        } catch (Exception xe) {
            xe.printStackTrace();
            Theme.showMessage(this, "删除失败：" + xe.getMessage(), Theme.MESSAGE_ERROR);
        }
    }

    private void updatePagingData() {
        pageLabel.setText(current + " / " + pages);
        countLabel.setText(total + " 位联系人" + (isFiltered() ? "（已筛选）" : ""));
        prevButton.setEnabled(current > 1);
        nextButton.setEnabled(current < pages);
    }

    private boolean isFiltered() {
        return !Utils.isBlank(searchInput.getText()) || categoryTabs.getSelected() > 0;
    }

    /** 数据有变化后刷新（编辑面板保存后调用）。 */
    public void showWindow() {
        setVisible(true);
        int resolvedSize = resolvePageSize();
        if (resolvedSize > 0) size = resolvedSize;
        refreshCategories();
        initTableData();
    }

    private void initTableData() {
        if (!adjustingPageSize) {
            int resolvedSize = resolvePageSize();
            if (resolvedSize > 0 && resolvedSize != size) {
                int firstRecordIndex = Math.max(0, (current - 1) * Math.max(1, size));
                size = resolvedSize;
                current = firstRecordIndex / size + 1;
            }
        }

        if (tableEditListener != null) {
            model.removeTableModelListener(tableEditListener);
            tableEditListener = null;
        }
        visibleContactIds.clear();
        model.setRowCount(0);

        Map<String, Object> filters = getSearchMap();
        try (PreparedStatement countStatement = Utils.prepareContactCountStatement(filters);
             ResultSet rsSum = countStatement.executeQuery()) {
            total = rsSum.next() ? rsSum.getInt("total") : 0;
            pages = Math.max(1, (int) Math.ceil(total * 1.0 / Math.max(1, size)));
            if (current > pages) current = pages;
            if (current < 1) current = 1;
            updatePagingData();

            int start = Math.max(0, (current - 1) * size);
            try (PreparedStatement listStatement = Utils.prepareContactListStatement(filters, start, size);
                 ResultSet rs = listStatement.executeQuery()) {
                while (rs.next()) {
                    visibleContactIds.add(rs.getString("c_id"));
                    model.addRow(new Object[]{
                            start + model.getRowCount() + 1,
                            rs.getString("c_name"),
                            Utils.toContactCategoryDisplayValue(rs.getString("c_nickname")),
                            rs.getString("c_phone"),
                            rs.getString("c_email"),
                            rs.getString("c_address"),
                            rs.getString("c_company"),
                            rs.getString("c_job_title"),
                            rs.getString("notes")
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (total == 0) {
            if (isFiltered()) table.setEmptyText("没有找到匹配的联系人", "换个关键词，或者切换到“全部”分类");
            else table.setEmptyText("还没有联系人", "点击右上角“新增联系人”，或从 CSV 导入");
        }
        table.playReload();
        updateSelectionState();

        tableEditListener = e -> {
            if (e.getType() != TableModelEvent.UPDATE) return;
            int row = e.getFirstRow();
            int col = e.getColumn();
            if (row < 0 || col <= 0 || col >= COLUMN_FIELDS.length) return;
            String dbField = COLUMN_FIELDS[col];
            if (dbField == null) return;

            String id = getContactIdAtRow(row);
            if (Utils.isBlank(id)) return;
            Object valObj = model.getValueAt(row, col);
            String newVal = valObj == null ? "" : valObj.toString().trim();
            if ("c_name".equals(dbField) && newVal.isEmpty()) {
                Theme.showMessage(this, "姓名不能为空", Theme.MESSAGE_ERROR);
                SwingUtilities.invokeLater(this::initTableData);
                return;
            }
            try {
                Utils.updateContactField(id, dbField, newVal);
                Theme.showMessage(this, "已保存", Theme.MESSAGE_SUCCESS);
            } catch (Exception ex) {
                ex.printStackTrace();
                Theme.showMessage(this, "保存失败：" + ex.getMessage(), Theme.MESSAGE_ERROR);
            }
        };
        model.addTableModelListener(tableEditListener);
    }

    private Map<String, Object> getSearchMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("keyword", searchInput.getText());
        if (categoryTabs.getSelected() > 0) map.put("category", categoryTabs.getSelectedTab());
        return map;
    }

    private String getContactIdAtRow(int row) {
        if (row < 0 || row >= visibleContactIds.size()) return "";
        return visibleContactIds.get(row);
    }
}
