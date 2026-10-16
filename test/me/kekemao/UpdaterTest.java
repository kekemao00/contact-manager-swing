package me.kekemao;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class UpdaterTest {
    private static HttpServer server;
    private static final AtomicInteger cutAfter = new AtomicInteger(-1);
    private static final List<String> ranges = new ArrayList<>();
    private static byte[] served;
    private static Path cache;

    @BeforeAll
    static void start() throws IOException {
        cache = Files.createTempDirectory("cm-update-cache");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/releases/download/", ex -> {
            String range = ex.getRequestHeaders().getFirst("Range");
            ranges.add(range);
            int from = range == null ? 0 : Integer.parseInt(range.replaceAll("\\D", ""));
            int cut = cutAfter.getAndSet(-1);
            int end = cut >= 0 ? cut : served.length;
            ex.getResponseHeaders().add("Content-Type", "application/octet-stream");
            // 中途断开时用分块传输，连接干净地提前结束
            if (range != null) {
                ex.sendResponseHeaders(206, cut >= 0 ? 0 : served.length - from);
            } else {
                ex.sendResponseHeaders(200, cut >= 0 ? 0 : served.length);
            }
            try (OutputStream out = ex.getResponseBody()) {
                out.write(served, from, end - from);
            } catch (IOException ignored) {
            }
            ex.close();
        });
        server.start();
        // UpdateChecker 在类加载时读取这些设置，必须在第一次用到它之前设好
        System.setProperty("contactmanager.updateDownloads", base());
        System.setProperty("contactmanager.updateDir", cache.toString());
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/releases/download/";
    }

    // ================= 版本与 Release 解析 =================

    @Test
    void comparesVersions() {
        assertTrue(UpdateChecker.compareVersions("2.4.0", "2.3.9") > 0);
        assertTrue(UpdateChecker.compareVersions("v2.10.0", "2.9.1") > 0);
        assertTrue(UpdateChecker.compareVersions("2.4.0-beta.1", "2.4.0") < 0);
        assertEquals(0, UpdateChecker.compareVersions("2.4.0", "v2.4.0"));
    }

    @Test
    void picksTheJarAssetWithDigest() {
        String sha = "a".repeat(64);
        Object assets = List.of(
                Map.of("name", "ContactManager-2.4.0-windows-x64.zip",
                        "browser_download_url", base() + "v2.4.0/ContactManager-2.4.0-windows-x64.zip", "size", 70.0),
                Map.of("name", "contact-manager-swing-2.4.0.jar",
                        "browser_download_url", base() + "v2.4.0/contact-manager-swing-2.4.0.jar",
                        "size", 1234.0, "digest", "sha256:" + sha));
        UpdateChecker.Asset a = UpdateChecker.jarAsset(assets, "2.4.0");
        assertNotNull(a);
        assertEquals(1234, a.size);
        assertEquals(sha, a.sha256);
        assertNull(UpdateChecker.jarAsset(assets, "2.5.0"));
    }

    @Test
    void ignoresAssetsHostedElsewhere() {
        Object assets = List.of(Map.of("name", "contact-manager-swing-2.4.0.jar",
                "browser_download_url", "https://example.com/contact-manager-swing-2.4.0.jar", "size", 10.0));
        assertNull(UpdateChecker.jarAsset(assets, "2.4.0"));
    }

    // ================= 启动配置 =================

    @Test
    void rewritesWindowsCfg() throws Exception {
        String cfg = "[Application]\r\napp.classpath=$APPDIR\\contact-manager-swing-2.3.0.jar\r\n"
                + "app.mainclass=me.kekemao.Main\r\n\r\n[JavaOptions]\r\njava-options=-Djpackage.app-version=2.3.0\r\n";
        String out = Updater.rewriteCfg(cfg, "contact-manager-swing-2.3.0.jar", "contact-manager-swing-2.4.0.jar", "2.4.0");
        assertTrue(out.contains("app.classpath=$APPDIR\\contact-manager-swing-2.4.0.jar\r\n"));
        assertTrue(out.contains("-Djpackage.app-version=2.4.0\r\n"));
        assertTrue(out.contains("app.mainclass=me.kekemao.Main"));
        assertFalse(out.contains("2.3.0"));
    }

    @Test
    void rewritesUnixCfgAndStripsPrerelease() throws Exception {
        String cfg = "[Application]\napp.classpath=$APPDIR/contact-manager-swing-2.3.0.jar\n"
                + "[JavaOptions]\njava-options=-Djpackage.app-version=2.3.0\n";
        String out = Updater.rewriteCfg(cfg, "contact-manager-swing-2.3.0.jar", "contact-manager-swing-2.4.0-beta.1.jar",
                "2.4.0-beta.1");
        assertTrue(out.contains("app.classpath=$APPDIR/contact-manager-swing-2.4.0-beta.1.jar\n"));
        assertTrue(out.contains("-Djpackage.app-version=2.4.0\n"));
    }

    @Test
    void refusesUnknownCfg() {
        String cfg = "[Application]\napp.classpath=$APPDIR/other.jar\n";
        assertThrows(Updater.UpdateFailed.class,
                () -> Updater.rewriteCfg(cfg, "contact-manager-swing-2.3.0.jar", "contact-manager-swing-2.4.0.jar", "2.4.0"));
    }

    // ================= 运行方式 =================

    @Test
    void detectsAppImageAndPlainJar(@TempDir Path dir) throws Exception {
        Path app = Files.createDirectories(dir.resolve("app"));
        Path jar = app.resolve("contact-manager-swing-2.3.0.jar");
        Files.write(jar, new byte[]{1});
        Files.writeString(app.resolve("ContactManager.cfg"), "app.classpath=$APPDIR/contact-manager-swing-2.3.0.jar\n");
        Updater.Install img = Updater.detect(jar, dir.resolve("ContactManager.exe").toString());
        assertEquals(Updater.Kind.APP_IMAGE, img.kind);
        assertEquals(app.resolve("ContactManager.cfg"), img.cfg);

        assertEquals(Updater.Kind.JAR, Updater.detect(jar, null).kind);
        assertEquals(Updater.Kind.NONE, Updater.detect(dir.resolve("classes"), null).kind);
        Files.delete(app.resolve("ContactManager.cfg"));
        assertEquals(Updater.Kind.NONE, Updater.detect(jar, dir.resolve("ContactManager").toString()).kind);
    }

    // ================= 校验 =================

    @Test
    void checksJarVersionAndJavaLevel(@TempDir Path dir) throws Exception {
        Path ok = fakeJar(dir.resolve("a.jar"), "2.4.0", 65);
        Updater.checkJar(ok, "2.4.0", 21);
        assertThrows(Updater.UpdateFailed.class, () -> Updater.checkJar(ok, "2.5.0", 21));
        Path newer = fakeJar(dir.resolve("b.jar"), "2.4.0", 69);
        assertThrows(Updater.NeedsNewerJava.class, () -> Updater.checkJar(newer, "2.4.0", 21));
        Path junk = dir.resolve("c.jar");
        Files.writeString(junk, "not a jar");
        assertThrows(Updater.UpdateFailed.class, () -> Updater.checkJar(junk, "2.4.0", 21));
    }

    @Test
    void downloadsResumesAndVerifies(@TempDir Path dir) throws Exception {
        String version = "9.1.0";
        Path jar = fakeJar(dir.resolve("src.jar"), version, 65);
        served = Files.readAllBytes(jar);
        UpdateChecker.Release r = release(version, Updater.sha256(jar));

        // 第一次下载到一半断开，留下 .part
        cutAfter.set(served.length / 2);
        ranges.clear();
        assertThrows(Updater.UpdateFailed.class, () -> Updater.download(r, (d, t) -> {}, () -> false));
        Path part = cache.resolve(Updater.JAR_PREFIX + version + ".jar.part");
        assertTrue(Files.isRegularFile(part));

        // 第二次从断点继续，并通过校验
        long[] last = {0};
        Path got = Updater.download(r, (d, t) -> last[0] = d, () -> false);
        assertEquals("bytes=" + Files.size(dir.resolve("src.jar")) / 2 + "-", ranges.get(ranges.size() - 1));
        assertArrayEquals(served, Files.readAllBytes(got));
        assertEquals(served.length, last[0]);
        assertFalse(Files.exists(part));

        // 已经下载过的直接返回，不再请求
        int before = ranges.size();
        assertEquals(got, Updater.download(r, (d, t) -> {}, () -> false));
        assertEquals(before, ranges.size());
    }

    @Test
    void rejectsTamperedDownload(@TempDir Path dir) throws Exception {
        String version = "9.2.0";
        Path jar = fakeJar(dir.resolve("src.jar"), version, 65);
        served = Files.readAllBytes(jar);
        UpdateChecker.Release r = release(version, "0".repeat(64));
        Updater.UpdateFailed e = assertThrows(Updater.UpdateFailed.class,
                () -> Updater.download(r, (d, t) -> {}, () -> false));
        assertTrue(e.getMessage().contains("校验"));
        assertFalse(Files.exists(Updater.stagedJar(version)));
        assertFalse(Files.exists(cache.resolve(Updater.JAR_PREFIX + version + ".jar.part")));
    }

    @Test
    void cancelsDownload(@TempDir Path dir) throws Exception {
        String version = "9.3.0";
        served = Files.readAllBytes(fakeJar(dir.resolve("src.jar"), version, 65));
        UpdateChecker.Release r = release(version, null);
        assertThrows(Updater.Cancelled.class, () -> Updater.download(r, (d, t) -> {}, () -> true));
    }

    // ================= 安装 =================

    @Test
    void installsIntoAppImageAndKeepsOldJarUntilNextStart(@TempDir Path dir) throws Exception {
        Path app = Files.createDirectories(dir.resolve("app"));
        Path old = fakeJar(app.resolve("contact-manager-swing-2.3.0.jar"), "2.3.0", 65);
        Path cfg = app.resolve("ContactManager.cfg");
        Files.writeString(cfg, "[Application]\napp.classpath=$APPDIR/contact-manager-swing-2.3.0.jar\n");
        Path staged = fakeJar(dir.resolve("staged.jar"), "2.4.0", 65);
        Updater.Install in = Updater.detect(old, dir.resolve("ContactManager").toString());

        assertFalse(Updater.apply(in, staged, release("2.4.0", null), false));
        assertTrue(Files.readString(cfg).contains("contact-manager-swing-2.4.0.jar"));
        assertTrue(Files.isRegularFile(app.resolve("contact-manager-swing-2.4.0.jar")));
        assertTrue(Files.isRegularFile(old), "旧 jar 留到新版本启动成功后再删");
        assertTrue(Files.isRegularFile(app.resolve("ContactManager.cfg.bak")));
    }

    @Test
    void replacesPlainJarInPlace(@TempDir Path dir) throws Exception {
        Path old = fakeJar(dir.resolve("contact-manager-swing.jar"), "2.3.0", 65);
        Path staged = fakeJar(dir.resolve("staged.jar"), "2.4.0", 65);
        Updater.Install in = Updater.detect(old, null);
        assertFalse(Updater.apply(in, staged, release("2.4.0", null), false));
        Updater.checkJar(old, "2.4.0", 21);
    }

    private static UpdateChecker.Release release(String version, String sha) throws IOException {
        String name = Updater.JAR_PREFIX + version + ".jar";
        return new UpdateChecker.Release(version, "v" + version, "notes", "https://example.com", "2026-10-16T00:00:00Z",
                new UpdateChecker.Asset(name, base() + "v" + version + "/" + name, served == null ? -1 : served.length, sha));
    }

    /** 造一个带清单和 Main.class 头的最小 jar。 */
    static Path fakeJar(Path path, String version, int classMajor) throws IOException {
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "me.kekemao.Main");
        mf.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path), mf)) {
            out.putNextEntry(new JarEntry("me/kekemao/Main.class"));
            out.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, (byte) classMajor});
            out.closeEntry();
            out.putNextEntry(new JarEntry("pad.txt"));
            out.write(("padding " + version).repeat(4000).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return path;
    }
}
