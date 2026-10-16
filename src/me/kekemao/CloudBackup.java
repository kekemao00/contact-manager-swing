package me.kekemao;

import me.kekemao.ui.Toast;
import org.sqlite.SQLiteConfig;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * 云端备份：把数据库快照写进用户电脑上已有的网盘同步文件夹（OneDrive、iCloud 云盘、坚果云、Dropbox、
 * Google 云端硬盘），由网盘客户端负责上传。不需要账号、地址或服务器，开启只要点一下。
 * <p>
 * 只写一致的快照（VACUUM INTO），从不让网盘同步正在使用的 txl.db；每台电脑的快照文件名带上设备名，
 * 各自只清理自己的旧快照，多台电脑共用一个网盘也不会互相删掉。
 */
public final class CloudBackup {
    /** 网盘里存放快照的子文件夹 */
    static final String FOLDER_NAME = "通讯录备份";
    private static final int MAX_BACKUPS = 10;
    /** 最后一次修改之后等这么久再备份，连续编辑只备份一次 */
    private static final long DEBOUNCE_SECONDS = 20;
    private static final int STARTUP_DELAY_MS = 1200;

    private static final String PREF_ENABLED = "cloud.enabled";
    private static final String PREF_DIR = "cloud.dir";
    private static final String PREF_LAST_BACKUP = "cloud.lastBackup";
    private static final String PREF_LAST_ERROR = "cloud.lastError";
    private static final String PREF_OFFERED = "cloud.offered";
    private static final String PREF_RESTORE_OFFERED = "cloud.restoreOffered";

    private static final Object LOCK = new Object();
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cloud-backup");
        t.setDaemon(true);
        return t;
    });
    private static ScheduledFuture<?> pending;
    private static volatile boolean dirty;
    private static boolean shutdownHookAdded;

    private CloudBackup() {}

    /** 一个网盘同步文件夹。 */
    static final class CloudFolder {
        final String name;
        final File dir;

        CloudFolder(String name, File dir) {
            this.name = name;
            this.dir = dir;
        }

        File backupDir() {
            return new File(dir, FOLDER_NAME);
        }

        /** 放进中文句子里的名字：以英文开头或结尾时在那一侧留空格，如“备份到 OneDrive”。 */
        String inline() {
            String s = name;
            if (!s.isEmpty() && s.charAt(0) < 0x80) s = " " + s;
            if (!s.isEmpty() && s.charAt(s.length() - 1) < 0x80) s = s + " ";
            return s;
        }
    }

    /** 一份云端快照。 */
    static final class Snapshot {
        final File file;
        final long time;
        final String device;

        Snapshot(File file, long time, String device) {
            this.file = file;
            this.time = time;
            this.device = device;
        }
    }

    // ================= 探测网盘 =================

    /** 本机已有的网盘同步文件夹，按常用程度排序；同一个文件夹只出现一次。 */
    static List<CloudFolder> detectFolders() {
        String home = System.getProperty("user.home");
        Map<String, CloudFolder> found = new LinkedHashMap<>();

        // OneDrive：Windows 会设置这几个环境变量
        for (String env : new String[]{"OneDrive", "OneDriveConsumer", "OneDriveCommercial"}) {
            add(found, "OneDrive", System.getenv(env));
        }
        add(found, "OneDrive", home, "OneDrive");
        // iCloud 云盘：macOS 与 Windows 版 iCloud
        add(found, "iCloud 云盘", home, "Library", "Mobile Documents", "com~apple~CloudDocs");
        add(found, "iCloud 云盘", home, "iCloudDrive");
        // 坚果云
        add(found, "坚果云", home, "我的坚果云");
        add(found, "坚果云", home, "Nutstore Files", "我的坚果云");
        add(found, "坚果云", home, "Nutstore", "我的坚果云");
        // Dropbox
        add(found, "Dropbox", home, "Dropbox");
        // macOS 新版网盘客户端统一放在 ~/Library/CloudStorage 下
        File[] storage = new File(home, "Library" + File.separator + "CloudStorage").listFiles(File::isDirectory);
        if (storage != null) {
            Arrays.sort(storage, Comparator.comparing(File::getName));
            for (File dir : storage) {
                String n = dir.getName();
                if (n.startsWith("OneDrive")) {
                    add(found, "OneDrive", dir.getPath());
                } else if (n.startsWith("Dropbox")) {
                    add(found, "Dropbox", dir.getPath());
                } else if (n.startsWith("GoogleDrive")) {
                    add(found, "Google 云端硬盘", dir.getPath(), "My Drive");
                    add(found, "Google 云端硬盘", dir.getPath(), "我的云端硬盘");
                }
            }
        }
        add(found, "Google 云端硬盘", home, "Google Drive");
        add(found, "Google 云端硬盘", home, "My Drive");
        return new ArrayList<>(found.values());
    }

    private static void add(Map<String, CloudFolder> found, String name, String first, String... more) {
        if (first == null || first.isBlank()) return;
        File dir = new File(first, String.join(File.separator, more));
        try {
            if (!dir.isDirectory() || !dir.canWrite()) return;
        } catch (SecurityException e) {
            return;
        }
        String key;
        try {
            key = dir.getCanonicalPath();
        } catch (IOException e) {
            key = dir.getAbsolutePath();
        }
        found.putIfAbsent(key, new CloudFolder(name, dir));
    }

    /** 给一个文件夹起显示名：是已知网盘就用网盘名，否则用文件夹名。 */
    static CloudFolder describe(File dir) {
        for (CloudFolder f : detectFolders()) {
            if (sameFile(f.dir, dir)) return f;
        }
        return new CloudFolder(dir.getName(), dir);
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    // ================= 设置 =================

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(CloudBackup.class);
    }

    static boolean isEnabled() {
        try {
            return prefs().getBoolean(PREF_ENABLED, false) && prefs().get(PREF_DIR, null) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 当前选定的网盘文件夹；没有选过返回 null。 */
    static CloudFolder getFolder() {
        try {
            String path = prefs().get(PREF_DIR, null);
            return path == null ? null : describe(new File(path));
        } catch (Exception e) {
            return null;
        }
    }

    static void enable(CloudFolder folder) {
        try {
            prefs().put(PREF_DIR, folder.dir.getAbsolutePath());
            prefs().putBoolean(PREF_ENABLED, true);
            prefs().remove(PREF_LAST_ERROR);
        } catch (Exception ignored) {
        }
    }

    static void disable() {
        try {
            prefs().putBoolean(PREF_ENABLED, false);
        } catch (Exception ignored) {
        }
        synchronized (CloudBackup.class) {
            if (pending != null) pending.cancel(false);
            dirty = false;
        }
    }

    static long lastBackupTime() {
        try {
            return prefs().getLong(PREF_LAST_BACKUP, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 上一次自动备份失败的原因，成功后清空。 */
    static String lastError() {
        try {
            return prefs().get(PREF_LAST_ERROR, null);
        } catch (Exception e) {
            return null;
        }
    }

    // ================= 备份 =================

    /** 数据有改动时调用：开启了云端备份就在稍后备份一次。 */
    static void changed() {
        if (!isEnabled()) return;
        synchronized (CloudBackup.class) {
            dirty = true;
            if (!shutdownHookAdded) {
                // 改完马上关掉程序时，退出前补上这次备份
                Runtime.getRuntime().addShutdownHook(new Thread(CloudBackup::flush, "cloud-backup-exit"));
                shutdownHookAdded = true;
            }
            if (pending != null) pending.cancel(false);
            pending = EXECUTOR.schedule(CloudBackup::backupQuietly, DEBOUNCE_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static void flush() {
        if (dirty && isEnabled()) backupQuietly();
    }

    private static void backupQuietly() {
        try {
            backupNow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 立即写一份快照到网盘文件夹，返回快照文件。失败原因会记下来，显示在菜单里。 */
    static File backupNow() throws Exception {
        CloudFolder folder = getFolder();
        if (folder == null) throw new IOException("还没有选择网盘文件夹");
        synchronized (LOCK) {
            dirty = false;
            try {
                File file = writeSnapshot(folder.backupDir());
                prefs().putLong(PREF_LAST_BACKUP, System.currentTimeMillis());
                prefs().remove(PREF_LAST_ERROR);
                return file;
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                try {
                    prefs().put(PREF_LAST_ERROR, message);
                } catch (Exception ignored) {
                }
                throw e;
            }
        }
    }

    private static File writeSnapshot(File dir) throws Exception {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("无法创建文件夹 " + dir);
        }
        String device = deviceName();
        String name = "txl_" + device + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".db";
        File target = new File(dir, name);

        // 先在本地数据目录生成快照，再复制进网盘，网盘客户端不会看到写了一半的数据库
        Path local = Files.createTempFile(Path.of(Utils.getDataDir()), "cloud_", ".db");
        Files.delete(local);
        Path part = new File(dir, "." + name + ".part").toPath();
        try {
            // 单独开一个连接做快照，不和界面线程抢同一个连接
            SQLiteConfig config = new SQLiteConfig();
            config.setBusyTimeout(5000);
            try (Connection c = config.createConnection("jdbc:sqlite:" + Utils.getDbPath());
                 PreparedStatement p = c.prepareStatement("VACUUM INTO ?")) {
                p.setString(1, local.toString());
                p.execute();
            }
            Files.copy(local, part, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(part, target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(part, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(local);
            Files.deleteIfExists(part);
        }
        prune(dir, device);
        return target;
    }

    /** 只清理本机的旧快照，保留最近 MAX_BACKUPS 份。 */
    private static void prune(File dir, String device) {
        List<Snapshot> own = new ArrayList<>();
        for (Snapshot s : listSnapshots(dir)) {
            if (device.equals(s.device)) own.add(s);
        }
        for (int i = MAX_BACKUPS; i < own.size(); i++) {
            own.get(i).file.delete();
        }
    }

    /** 文件夹里的全部快照，最新的在前。 */
    static List<Snapshot> listSnapshots(File dir) {
        List<Snapshot> result = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.startsWith("txl_") && n.endsWith(".db"));
        if (files == null) return result;
        for (File f : files) {
            result.add(parse(f));
        }
        result.sort((a, b) -> Long.compare(b.time, a.time));
        return result;
    }

    /** 文件名形如 txl_设备名_20261016_120000.db；时间取文件名里的，读不出来再用修改时间。 */
    static Snapshot parse(File f) {
        String base = f.getName().substring(4, f.getName().length() - 3);
        if (base.length() > 16 && base.charAt(base.length() - 16) == '_') {
            String stamp = base.substring(base.length() - 15);
            try {
                long time = new SimpleDateFormat("yyyyMMdd_HHmmss").parse(stamp).getTime();
                return new Snapshot(f, time, base.substring(0, base.length() - 16));
            } catch (Exception ignored) {
            }
        }
        return new Snapshot(f, f.lastModified(), "");
    }

    /** 本机名，只保留文件名里安全的字符。 */
    static String deviceName() {
        String name = System.getenv("COMPUTERNAME");
        if (name == null || name.isBlank()) {
            try {
                name = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                name = null;
            }
        }
        if (name == null || name.isBlank()) name = System.getProperty("user.name", "device");
        String safe = name.replaceAll("[^\\p{L}\\p{N}-]+", "-").replaceAll("^-+|-+$", "");
        if (safe.isEmpty()) safe = "device";
        return safe.length() > 40 ? safe.substring(0, 40) : safe;
    }

    /** 当前网盘文件夹里最新的一份快照；没有返回 null。 */
    static Snapshot latestSnapshot(CloudFolder folder) {
        if (folder == null) return null;
        List<Snapshot> all = listSnapshots(folder.backupDir());
        return all.isEmpty() ? null : all.get(0);
    }

    /** 用一份快照替换本机数据，返回恢复的联系人数。本机现在的数据会先另存到本地 backup 文件夹。 */
    static int restore(Snapshot snapshot) throws Exception {
        synchronized (LOCK) {
            return Utils.replaceDatabase(snapshot.file.toPath());
        }
    }

    // ================= 界面 =================

    /**
     * 启动后：开启了备份且数据比上次备份新，就补一次备份；本机还没有联系人而网盘里有备份，
     * 提示一键恢复；发现网盘但还没开启过，提示一次一键开启。
     */
    static void onStartup(ContactWindow window) {
        if (isEnabled()) {
            File db = new File(Utils.getDbPath());
            if (db.lastModified() > lastBackupTime()) changed();
        }
        Timer delay = new Timer(STARTUP_DELAY_MS, e -> {
            if (!window.isShowing()) return;
            if (offerRestore(window)) return;
            offerEnable(window);
        });
        delay.setRepeats(false);
        delay.start();
    }

    private static boolean offerRestore(ContactWindow window) {
        try {
            if (Utils.countContacts() > 0) return false;
        } catch (Exception e) {
            return false;
        }
        List<CloudFolder> folders = new ArrayList<>();
        CloudFolder chosen = getFolder();
        if (chosen != null) folders.add(chosen);
        folders.addAll(detectFolders());
        for (CloudFolder folder : folders) {
            Snapshot latest = latestSnapshot(folder);
            if (latest == null) continue;
            String key = latest.file.getAbsolutePath();
            if (key.equals(prefs().get(PREF_RESTORE_OFFERED, null))) return false;
            prefs().put(PREF_RESTORE_OFFERED, key);
            Toast.show(window, "在" + folder.inline() + "里发现通讯录备份，点这里恢复", Toast.Kind.INFO,
                    () -> confirmRestore(window, folder, latest));
            return true;
        }
        return false;
    }

    private static void offerEnable(ContactWindow window) {
        if (isEnabled() || prefs().getBoolean(PREF_OFFERED, false)) return;
        List<CloudFolder> folders = detectFolders();
        if (folders.isEmpty()) return;
        prefs().putBoolean(PREF_OFFERED, true);
        CloudFolder folder = folders.get(0);
        Toast.show(window, "可以自动备份到" + folder.inline().stripTrailing() + "，点这里开启", Toast.Kind.INFO,
                () -> turnOn(window, folder));
    }

    /** 菜单里的开关：开启时用探测到的第一个网盘，没有网盘就让用户选一个文件夹。 */
    static void toggle(ContactWindow window, boolean on) {
        if (!on) {
            disable();
            Theme.showMessage(window, "已关闭云端备份，网盘里已有的备份不会删除", Theme.MESSAGE_SUCCESS);
            return;
        }
        CloudFolder folder = getFolder();
        if (folder == null || !folder.dir.isDirectory()) {
            List<CloudFolder> detected = detectFolders();
            folder = detected.isEmpty() ? null : detected.get(0);
        }
        if (folder == null) {
            chooseFolder(window);
            return;
        }
        turnOn(window, folder);
    }

    /** 让用户选一个文件夹（比如没被自动发现的网盘），选好后立即开启。 */
    static void chooseFolder(ContactWindow window) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择网盘同步文件夹");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        CloudFolder current = getFolder();
        List<CloudFolder> detected = detectFolders();
        if (current != null) {
            chooser.setCurrentDirectory(current.dir);
        } else if (!detected.isEmpty()) {
            chooser.setCurrentDirectory(detected.get(0).dir);
        }
        if (chooser.showDialog(window, "备份到这里") != JFileChooser.APPROVE_OPTION) return;
        File dir = chooser.getSelectedFile();
        // 选中的就是“通讯录备份”文件夹本身时，用它的上一级
        if (FOLDER_NAME.equals(dir.getName()) && dir.getParentFile() != null) dir = dir.getParentFile();
        turnOn(window, describe(dir));
    }

    private static void turnOn(ContactWindow window, CloudFolder folder) {
        enable(folder);
        runInBackground(window, "已开启云端备份，保存在" + folder.inline() + "的“" + FOLDER_NAME + "”文件夹");
    }

    /** 手动立即备份。 */
    static void backupNowWithFeedback(ContactWindow window) {
        if (!isEnabled()) {
            toggle(window, true);
            return;
        }
        runInBackground(window, "已备份到" + getFolder().inline().stripTrailing());
    }

    private static void runInBackground(ContactWindow window, String successMessage) {
        EXECUTOR.execute(() -> {
            String error = null;
            try {
                backupNow();
            } catch (Exception e) {
                e.printStackTrace();
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            String failure = error;
            SwingUtilities.invokeLater(() -> {
                if (!window.isShowing()) return;
                if (failure == null) {
                    Theme.showMessage(window, successMessage, Theme.MESSAGE_SUCCESS);
                } else {
                    Theme.showMessage(window, "云端备份失败：" + failure, Theme.MESSAGE_ERROR);
                }
            });
        });
    }

    /** 从当前网盘文件夹（没有选过就用探测到的网盘）里最新的备份恢复。 */
    static void restoreLatest(ContactWindow window) {
        List<CloudFolder> folders = new ArrayList<>();
        CloudFolder chosen = getFolder();
        if (chosen != null) folders.add(chosen);
        folders.addAll(detectFolders());
        CloudFolder bestFolder = null;
        Snapshot best = null;
        for (CloudFolder folder : folders) {
            Snapshot s = latestSnapshot(folder);
            if (s != null && (best == null || s.time > best.time)) {
                best = s;
                bestFolder = folder;
            }
        }
        if (best == null) {
            Theme.showMessage(window, "网盘里还没有通讯录备份", Theme.MESSAGE_WARNING);
            return;
        }
        confirmRestore(window, bestFolder, best);
    }

    private static void confirmRestore(ContactWindow window, CloudFolder folder, Snapshot snapshot) {
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(snapshot.time));
        String from = snapshot.device.isEmpty() ? "" : "（来自 " + snapshot.device + "）";
        String message = "将用" + folder.inline() + "里 " + when + from + " 的备份替换本机数据，登录密码也会恢复成备份时的密码。"
                + "本机现在的数据会先另存到数据目录的 backup 文件夹。";
        if (Theme.showConfirm(window, "从云端备份恢复", message, "恢复", true) != 0) return;
        try {
            int count = restore(snapshot);
            window.current = 1;
            window.showWindow();
            Theme.showMessage(window, "已恢复 " + count + " 位联系人", Theme.MESSAGE_SUCCESS);
        } catch (Exception e) {
            e.printStackTrace();
            Theme.showMessage(window, "恢复失败：" + e.getMessage(), Theme.MESSAGE_ERROR);
        }
    }

    /** 菜单里“立即备份”一项后面的说明。 */
    static String statusText() {
        String error = lastError();
        if (error != null) return "上次失败：" + error;
        long last = lastBackupTime();
        if (last <= 0) return "还没有备份";
        return "上次 " + new SimpleDateFormat("MM-dd HH:mm").format(new Date(last));
    }

    /** 在系统文件管理器里打开网盘里的备份文件夹。 */
    static void openFolder(ContactWindow window) {
        CloudFolder folder = getFolder();
        if (folder == null) return;
        try {
            File dir = folder.backupDir();
            dir.mkdirs();
            Desktop.getDesktop().open(dir);
        } catch (Exception e) {
            Theme.showMessage(window, "无法打开文件夹：" + e.getMessage(), Theme.MESSAGE_ERROR);
        }
    }
}
