package me.kekemao;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * 应用内更新：把新版本的 jar 下载到缓存目录、校验，再换进程序里并重新启动。
 * <p>
 * 联系人数据在“文档/通讯录数据”里，和程序文件分开，更新只替换程序本身。两种运行方式：
 * <ul>
 *   <li>绿色版（jpackage 生成，内嵌 JRE）：把新 jar 放进 app 目录，再把启动配置指向它。
 *       旧 jar 留到新版本成功启动后才删除，启动配置保留一份 .bak。</li>
 *   <li>直接运行 jar：原地替换 jar 文件。Windows 上运行中的 jar 被锁住时，
 *       交给新 jar 起一个小进程，等本程序退出后再替换。</li>
 * </ul>
 * 从源码运行、目录不可写或新版本需要更高的 Java 时不做应用内安装，界面会退回到打开下载页。
 */
final class Updater {
    static final String JAR_PREFIX = "contact-manager-swing-";
    static final String MAIN_CLASS = "me.kekemao.Main";
    /** 新 jar 在本程序退出后完成替换：--finish-update <pid> <staged> <target> [relaunch] */
    static final String FINISH_ARG = "--finish-update";
    /** 重启后免登录：--resume=<一次性令牌> */
    static final String RESUME_ARG = "--resume=";

    private static final String MARKER = "installed.properties";
    private static final String RESUME_FILE = "resume.token";
    private static final long RESUME_TTL_MS = TimeUnit.MINUTES.toMillis(2);

    private Updater() {}

    // ================= 运行方式 =================

    enum Kind { APP_IMAGE, JAR, NONE }

    /** 当前程序是怎么运行的、能不能在应用内更新。 */
    static final class Install {
        final Kind kind;
        final Path jar;
        final Path cfg;
        final Path launcher;
        /** kind 为 NONE 时给用户看的原因。 */
        final String reason;

        Install(Kind kind, Path jar, Path cfg, Path launcher, String reason) {
            this.kind = kind;
            this.jar = jar;
            this.cfg = cfg;
            this.launcher = launcher;
            this.reason = reason;
        }

        boolean supported() {
            return kind != Kind.NONE;
        }

        static Install none(String reason) {
            return new Install(Kind.NONE, null, null, null, reason);
        }
    }

    private static Install detected;

    static synchronized Install install() {
        if (detected == null) detected = detect(runningJar(), System.getProperty("jpackage.app-path"));
        return detected;
    }

    static Install detect(Path jar, String appPath) {
        if (jar == null || !Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")) {
            return Install.none("当前从源码运行");
        }
        Path dir = jar.toAbsolutePath().getParent();
        if (appPath != null && !appPath.isBlank()) {
            Path launcher = Path.of(appPath);
            String name = launcher.getFileName().toString();
            if (name.toLowerCase(Locale.ROOT).endsWith(".exe")) name = name.substring(0, name.length() - 4);
            Path cfg = dir.resolve(name + ".cfg");
            if (!Files.isRegularFile(cfg)) return Install.none("找不到程序的启动配置");
            if (!canWrite(dir)) return Install.none("程序所在的文件夹没有写入权限");
            return new Install(Kind.APP_IMAGE, jar, cfg, launcher, null);
        }
        if (!canWrite(dir)) return Install.none("程序所在的文件夹没有写入权限");
        return new Install(Kind.JAR, jar, null, null, null);
    }

    private static Path runningJar() {
        try {
            var src = Updater.class.getProtectionDomain().getCodeSource();
            return src == null ? null : Path.of(src.getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }

    /** Windows 上 Files.isWritable 对文件夹不可靠，直接试着建一个文件。 */
    private static boolean canWrite(Path dir) {
        try {
            Path probe = Files.createTempFile(dir, ".update-", ".tmp");
            Files.delete(probe);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ================= 缓存目录 =================

    /** 下载的新版本放在系统缓存目录里，不放进会被网盘同步的“文档”。 */
    static Path cacheDir() {
        String override = System.getProperty("contactmanager.updateDir");
        if (override != null && !override.isBlank()) return Path.of(override);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            Path base = local != null && !local.isBlank() ? Path.of(local) : Path.of(home, "AppData", "Local");
            return base.resolve("ContactManager").resolve("updates");
        }
        if (os.contains("mac")) return Path.of(home, "Library", "Caches", "ContactManager", "updates");
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".cache");
        return base.resolve("contact-manager").resolve("updates");
    }

    static Path stagedJar(String version) {
        return cacheDir().resolve(JAR_PREFIX + version + ".jar");
    }

    // ================= 下载与校验 =================

    interface Progress {
        void update(long done, long total);
    }

    /** 可以直接给用户看的失败原因。 */
    static class UpdateFailed extends IOException {
        UpdateFailed(String message) {
            super(message);
        }
    }

    /**
     * 下载 release 的 jar 到缓存目录并校验，返回校验通过的文件。已经下载过的直接返回；
     * 下载中断时保留 .part，下次从断点继续。
     */
    static Path download(UpdateChecker.Release release, Progress progress, BooleanSupplier cancelled) throws IOException {
        UpdateChecker.Asset asset = release.jar;
        if (asset == null) throw new UpdateFailed("这个版本没有可以直接安装的文件");
        if (!asset.url.startsWith(UpdateChecker.DOWNLOAD_PREFIX)) throw new UpdateFailed("下载地址不可信");
        Path dir = cacheDir();
        Files.createDirectories(dir);
        Path dest = stagedJar(release.version);
        if (Files.isRegularFile(dest)) {
            try {
                verify(dest, release);
                progress.update(asset.size, asset.size);
                return dest;
            } catch (IOException e) {
                Files.deleteIfExists(dest);
            }
        }
        Path part = dir.resolve(dest.getFileName() + ".part");
        long have = Files.isRegularFile(part) ? Files.size(part) : 0;
        if (asset.size > 0 && have >= asset.size) {
            Files.deleteIfExists(part);
            have = 0;
        }
        fetch(asset, part, have, progress, cancelled);
        try {
            verify(part, release);
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        moveReplacing(part, dest);
        cleanCache(release.version);
        return dest;
    }

    private static void fetch(UpdateChecker.Asset asset, Path part, long have, Progress progress,
                              BooleanSupplier cancelled) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(asset.url).toURL().openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept", "application/octet-stream");
        conn.setRequestProperty("User-Agent", "ContactManager/" + versionOr("dev"));
        if (have > 0) conn.setRequestProperty("Range", "bytes=" + have + "-");
        try {
            int code = conn.getResponseCode();
            boolean resume = code == 206 && have > 0;
            if (code != 200 && !resume) throw new UpdateFailed("下载服务器返回 " + code);
            if (!resume) have = 0;
            long length = conn.getContentLengthLong();
            long total = asset.size > 0 ? asset.size : (length > 0 ? have + length : -1);
            StandardOpenOption mode = resume ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING;
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.WRITE, mode)) {
                byte[] buf = new byte[64 * 1024];
                long done = have;
                progress.update(done, total);
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled.getAsBoolean()) throw new Cancelled();
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0 && done > total) throw new UpdateFailed("下载的文件大小不对");
                    progress.update(done, total);
                }
                // 连接提前断开：保留 .part，下次接着下
                if (total > 0 && done < total) throw new UpdateFailed("下载中断，请重试");
            }
        } finally {
            conn.disconnect();
        }
    }

    /** 用户取消了下载。 */
    static final class Cancelled extends IOException {
        Cancelled() {
            super("已取消");
        }
    }

    /** 大小、SHA-256、jar 内的版本号和所需的 Java 版本都对得上才算通过。 */
    static void verify(Path file, UpdateChecker.Release release) throws IOException {
        UpdateChecker.Asset asset = release.jar;
        if (asset.size > 0 && Files.size(file) != asset.size) throw new UpdateFailed("下载不完整，请重试");
        if (asset.sha256 != null && !asset.sha256.equalsIgnoreCase(sha256(file))) {
            throw new UpdateFailed("文件校验失败，已丢弃");
        }
        checkJar(file, release.version, Runtime.version().feature());
    }

    /** 检查 jar 是这个程序的指定版本，并且能在 javaFeature 版本的 Java 上运行。 */
    static void checkJar(Path file, String version, int javaFeature) throws IOException {
        try (JarFile jar = new JarFile(file.toFile(), false)) {
            Manifest mf = jar.getManifest();
            Attributes a = mf == null ? null : mf.getMainAttributes();
            if (a == null || !MAIN_CLASS.equals(a.getValue(Attributes.Name.MAIN_CLASS))) {
                throw new UpdateFailed("下载的文件不是本程序");
            }
            if (!version.equals(a.getValue(Attributes.Name.IMPLEMENTATION_VERSION))) {
                throw new UpdateFailed("下载的文件版本不对");
            }
            ZipEntry main = jar.getEntry(MAIN_CLASS.replace('.', '/') + ".class");
            if (main == null) throw new UpdateFailed("下载的文件不是本程序");
            byte[] head;
            try (InputStream in = jar.getInputStream(main)) {
                head = in.readNBytes(8);
            }
            if (head.length < 8 || (head[0] & 0xFF) != 0xCA || (head[1] & 0xFF) != 0xFE) {
                throw new UpdateFailed("下载的文件已损坏");
            }
            int major = ((head[6] & 0xFF) << 8) | (head[7] & 0xFF);
            if (major - 44 > javaFeature) throw new NeedsNewerJava(major - 44);
        } catch (java.util.zip.ZipException e) {
            throw new UpdateFailed("下载的文件已损坏");
        }
    }

    /** 新版本要求更高的 Java，只换 jar 不够，需要下载完整安装包。 */
    static final class NeedsNewerJava extends UpdateFailed {
        NeedsNewerJava(int feature) {
            super("新版本需要 Java " + feature + "，请下载完整安装包");
        }
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** 删掉缓存里除 keep 以外的旧下载。 */
    static void cleanCache(String keep) {
        Path dir = cacheDir();
        if (!Files.isDirectory(dir)) return;
        String keepName = keep == null ? null : JAR_PREFIX + keep + ".jar";
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, JAR_PREFIX + "*")) {
            for (Path f : files) {
                String name = f.getFileName().toString();
                if (keepName != null && (name.equals(keepName) || name.equals(keepName + ".part"))) continue;
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // Windows 上可能正被辅助进程使用，下次再删
                }
            }
        } catch (IOException ignored) {
        }
    }

    // ================= 安装 =================

    /**
     * 把校验过的 staged 换进程序。relaunch 为 true 时随后启动新版本（调用方负责退出本程序），
     * 并带上一次性令牌，让新版本直接回到当前账号。
     *
     * @return 新版本是否已经直接启动（为 false 时由辅助进程在本程序退出后完成替换）
     */
    static boolean apply(Install in, Path staged, UpdateChecker.Release release, boolean relaunch) throws IOException {
        if (!in.supported()) throw new UpdateFailed(in.reason);
        String token = relaunch ? issueResume() : null;
        switch (in.kind) {
            case APP_IMAGE: {
                Path target = in.jar.getParent().resolve(JAR_PREFIX + release.version + ".jar");
                copyReplacing(staged, target);
                String cfg = Files.readString(in.cfg, StandardCharsets.UTF_8);
                String updated = rewriteCfg(cfg, in.jar.getFileName().toString(), target.getFileName().toString(),
                        release.version);
                Files.copy(in.cfg, in.cfg.resolveSibling(in.cfg.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                Path tmp = in.cfg.resolveSibling(in.cfg.getFileName() + ".new");
                Files.writeString(tmp, updated, StandardCharsets.UTF_8);
                moveReplacing(tmp, in.cfg);
                writeMarker(release);
                if (relaunch) launch(List.of(in.launcher.toString()), token);
                return relaunch;
            }
            case JAR: {
                Path tmp = in.jar.resolveSibling("." + in.jar.getFileName() + ".new");
                boolean replaced;
                try {
                    copyReplacing(staged, tmp);
                    moveReplacing(tmp, in.jar);
                    replaced = true;
                } catch (IOException e) {
                    // Windows 上运行中的 jar 被锁住，等本程序退出后再换
                    Files.deleteIfExists(tmp);
                    replaced = false;
                }
                writeMarker(release);
                if (replaced) {
                    if (relaunch) launch(List.of(javaCommand(), "-jar", in.jar.toString()), token);
                    return relaunch;
                } else {
                    List<String> cmd = new ArrayList<>(List.of(javaCommand(), "-cp", staged.toString(), MAIN_CLASS,
                            FINISH_ARG, String.valueOf(ProcessHandle.current().pid()), staged.toString(), in.jar.toString()));
                    if (relaunch) cmd.add("relaunch");
                    launch(cmd, token);
                    return false;
                }
            }
            default:
                throw new UpdateFailed(in.reason);
        }
    }

    /**
     * 把 jpackage 启动配置里的 jar 名换成新版本。只改 app.classpath 和 app-version 两处，
     * 找不到旧 jar 名时拒绝修改，宁可退回到下载页也不写出一个打不开的程序。
     */
    static String rewriteCfg(String cfg, String oldJar, String newJar, String version) throws UpdateFailed {
        String nl = cfg.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = cfg.split("\r?\n", -1);
        boolean found = false;
        String appVersion = version.split("[-+]")[0];
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("app.classpath=") && line.endsWith(oldJar)) {
                String prefix = line.substring(0, line.length() - oldJar.length());
                char sep = prefix.isEmpty() ? '/' : prefix.charAt(prefix.length() - 1);
                if (sep == '/' || sep == '\\' || sep == '=') {
                    lines[i] = prefix + newJar;
                    found = true;
                }
            } else if (line.contains("-Djpackage.app-version=")) {
                lines[i] = line.replaceAll("(-Djpackage\\.app-version=)[^\\s\"]+", "$1" + appVersion);
            }
        }
        if (!found) throw new UpdateFailed("无法识别程序的启动配置");
        return String.join(nl, lines);
    }

    private static void copyReplacing(Path from, Path to) throws IOException {
        Path tmp = to.resolveSibling(to.getFileName() + ".tmp");
        Files.copy(from, tmp, StandardCopyOption.REPLACE_EXISTING);
        moveReplacing(tmp, to);
    }

    private static void moveReplacing(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 当前 JVM 的 java；Windows 上用 javaw，不弹黑色命令行窗口。 */
    private static String javaCommand() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path java = bin.resolve(win ? "javaw.exe" : "java");
        if (Files.isRegularFile(java)) return java.toString();
        return ProcessHandle.current().info().command().orElse("java");
    }

    private static void launch(List<String> cmd, String token) throws IOException {
        List<String> full = new ArrayList<>(cmd);
        if (token != null) full.add(RESUME_ARG + token);
        ProcessBuilder pb = new ProcessBuilder(full);
        // jpackage 启动器给自己的 JVM 设了 _JPACKAGE_LAUNCHER，子进程继承后会把参数当成 JVM 选项而启动失败
        pb.environment().keySet().removeIf(k -> k.startsWith("_JPACKAGE"));
        pb.redirectErrorStream(true);
        // 新进程的输出留一份，启动不起来时方便排查
        Files.createDirectories(cacheDir());
        pb.redirectOutput(cacheDir().resolve("launch.log").toFile());
        pb.start();
    }

    /**
     * 辅助进程：等旧程序退出，把新 jar 复制到原位置，需要时再启动它。
     * 由新版本的 jar 执行，所以以后的版本也要保留这个入口。
     */
    static void finishUpdate(String[] args) {
        try {
            int at = java.util.Arrays.asList(args).indexOf(FINISH_ARG);
            long pid = Long.parseLong(args[at + 1]);
            Path staged = Path.of(args[at + 2]);
            Path target = Path.of(args[at + 3]);
            boolean relaunch = args.length > at + 4 && "relaunch".equals(args[at + 4]);
            String token = null;
            for (String a : args) if (a.startsWith(RESUME_ARG)) token = a.substring(RESUME_ARG.length());
            ProcessHandle.of(pid).ifPresent(p -> {
                try {
                    p.onExit().get(60, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            });
            IOException last = null;
            for (int i = 0; i < 20; i++) {
                try {
                    copyReplacing(staged, target);
                    last = null;
                    break;
                } catch (IOException e) {
                    last = e;
                    Thread.sleep(500);
                }
            }
            if (last != null) return;
            if (relaunch) launch(List.of(javaCommand(), "-jar", target.toString()), token);
        } catch (Exception ignored) {
        }
    }

    // ================= 更新完成后 =================

    /** 刚从 from 更新到 to；notes 是更新说明。 */
    static final class Installed {
        final String from;
        final String to;
        final String notes;
        final String publishedAt;

        Installed(String from, String to, String notes, String publishedAt) {
            this.from = from;
            this.to = to;
            this.notes = notes;
            this.publishedAt = publishedAt;
        }
    }

    private static void writeMarker(UpdateChecker.Release release) throws IOException {
        Properties p = new Properties();
        p.setProperty("from", versionOr(""));
        p.setProperty("to", release.version);
        p.setProperty("notes", release.notes == null ? "" : release.notes);
        p.setProperty("publishedAt", release.publishedAt == null ? "" : release.publishedAt);
        Files.createDirectories(cacheDir());
        try (Writer w = Files.newBufferedWriter(cacheDir().resolve(MARKER), StandardCharsets.UTF_8)) {
            p.store(w, null);
        }
    }

    /**
     * 启动时调用一次：新版本跑起来了，就收尾（删掉旧 jar 和缓存）并返回更新记录，用于提示“已更新”。
     */
    static Installed afterLaunch() {
        String current = UpdateChecker.currentVersion();
        Path marker = cacheDir().resolve(MARKER);
        if (current == null || !Files.isRegularFile(marker)) return null;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(marker, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return null;
        }
        String to = p.getProperty("to", "");
        if (!current.equals(to)) {
            // 还没换过来（例如选了“退出时安装”但还没退出过），留着
            if (UpdateChecker.compareVersions(current, to) > 0) deleteQuietly(marker);
            return null;
        }
        deleteQuietly(marker);
        Install in = install();
        if (in.kind == Kind.APP_IMAGE) removeOldJars(in.jar);
        cleanCache(null);
        return new Installed(p.getProperty("from", ""), to, p.getProperty("notes", ""), p.getProperty("publishedAt", ""));
    }

    /** 绿色版 app 目录里除正在运行的以外的旧 jar。 */
    private static void removeOldJars(Path running) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(running.getParent(), JAR_PREFIX + "*.jar")) {
            for (Path f : files) {
                if (!f.getFileName().equals(running.getFileName())) deleteQuietly(f);
            }
        } catch (IOException ignored) {
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    // ================= 重启后免登录 =================

    private static String issueResume() {
        String user = AccountService.getCurrentUser();
        if (user == null) return null;
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        String token = HexFormat.of().formatHex(b);
        try {
            Files.createDirectories(cacheDir());
            Path f = cacheDir().resolve(RESUME_FILE);
            Files.writeString(f, token + "\n" + user + "\n" + (System.currentTimeMillis() + RESUME_TTL_MS),
                    StandardCharsets.UTF_8);
            return token;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 等新版本启动：它读走令牌就说明已经起来了，再稍等一下让窗口出现，旧窗口才退出。
     */
    static void awaitHandoff(long timeoutMs) {
        Path f = cacheDir().resolve(RESUME_FILE);
        long end = System.currentTimeMillis() + timeoutMs;
        try {
            if (!Files.exists(f)) {
                Thread.sleep(1500);
                return;
            }
            while (Files.exists(f) && System.currentTimeMillis() < end) Thread.sleep(80);
            Thread.sleep(700);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 令牌对得上且没过期时返回用户名。令牌只能用一次。 */
    static String consumeResume(String[] args) {
        String token = null;
        for (String a : args) if (a.startsWith(RESUME_ARG)) token = a.substring(RESUME_ARG.length());
        if (token == null) return null;
        Path f = cacheDir().resolve(RESUME_FILE);
        try {
            if (!Files.isRegularFile(f)) return null;
            String[] parts = Files.readString(f, StandardCharsets.UTF_8).split("\n");
            Files.deleteIfExists(f);
            if (parts.length < 3 || !MessageDigest.isEqual(parts[0].getBytes(StandardCharsets.UTF_8),
                    token.getBytes(StandardCharsets.UTF_8))) return null;
            if (System.currentTimeMillis() > Long.parseLong(parts[2].trim())) return null;
            return parts[1];
        } catch (Exception e) {
            return null;
        }
    }

    private static String versionOr(String fallback) {
        String v = UpdateChecker.currentVersion();
        return v == null ? fallback : v;
    }
}
