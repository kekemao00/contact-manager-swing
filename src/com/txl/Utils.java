package com.txl;

import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Utils {
    public static String iconPath = "";

    // 固定数据目录：用户文档/通讯录数据/
    private static final String DATA_DIR;
    private static final String BACKUP_DIR;
    private static final String DB_PATH;
    private static final int MAX_BACKUPS = 5;

    static {
        String userHome = System.getProperty("user.home");
        DATA_DIR = userHome + File.separator + "Documents" + File.separator + "通讯录数据";
        BACKUP_DIR = DATA_DIR + File.separator + "backup";
        DB_PATH = DATA_DIR + File.separator + "txl.db";

        // 确保目录存在
        new File(DATA_DIR).mkdirs();
        new File(BACKUP_DIR).mkdirs();

        // 如果程序目录下有旧数据库，迁移过来
        migrateOldDb();
    }

    /** 迁移旧数据库（从程序目录到 Documents） */
    private static void migrateOldDb() {
        String oldPath = System.getProperty("user.dir") + File.separator + "txl.db";
        File oldDb = new File(oldPath);
        File newDb = new File(DB_PATH);
        if (oldDb.exists() && !newDb.exists()) {
            try {
                Files.copy(oldDb.toPath(), newDb.toPath(), StandardCopyOption.REPLACE_EXISTING);
                oldDb.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 数据库连接
    private static String url = "jdbc:sqlite:" + DB_PATH;
    private static Connection conn;
    private static Statement stmt;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(url);
            stmt = conn.createStatement();

            // 建表（如果不存在）
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS admin (" +
                "  adminID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  username TEXT NOT NULL," +
                "  password TEXT NOT NULL," +
                "  name TEXT NOT NULL," +
                "  email TEXT" +
                ")"
            );
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS contact (" +
                "  c_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  c_name TEXT NOT NULL," +
                "  c_nickname TEXT," +
                "  c_phone TEXT," +
                "  c_email TEXT," +
                "  c_address TEXT," +
                "  c_birthday TEXT," +
                "  c_company TEXT," +
                "  c_job_title TEXT," +
                "  c_group_name TEXT," +
                "  notes TEXT" +
                ")"
            );
            // 初始化默认管理员（如果不存在）
            ResultSet rs = stmt.executeQuery("SELECT COUNT(1) FROM admin WHERE username='admin'");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO admin(username,password,name,email) VALUES('admin','123456','管理员',NULL)");
            }
            rs.close();

            // 启动时自动备份
            backup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 自动备份，保留最近 MAX_BACKUPS 份 */
    public static void backup() {
        File dbFile = new File(DB_PATH);
        if (!dbFile.exists()) return;

        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String backupName = "txl_" + timestamp + ".db";
            File backupFile = new File(BACKUP_DIR, backupName);
            Files.copy(dbFile.toPath(), backupFile.toPath());

            // 清理旧备份，只保留最近的
            File[] backups = new File(BACKUP_DIR).listFiles((dir, name) -> name.startsWith("txl_") && name.endsWith(".db"));
            if (backups != null && backups.length > MAX_BACKUPS) {
                // 按修改时间排序，删最旧的
                java.util.Arrays.sort(backups, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < backups.length - MAX_BACKUPS; i++) {
                    backups[i].delete();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getDataDir() {
        return DATA_DIR;
    }

    public static Statement getStatement() {
        return stmt;
    }

    public static Connection getConn() {
        return conn;
    }

    public static Long formTime(String dateTime) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            return simpleDateFormat.parse(dateTime).getTime();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return System.currentTimeMillis();
    }

    public static String formTime(Date date, String form) {
        if ("".equals(form) || form == null) {
            form = "yyyy-MM-dd HH:mm:ss";
        }
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(form);
        return simpleDateFormat.format(date);
    }

    public static boolean isBlank(String str) {
        if (str == null) return true;
        return "".equals(str.trim());
    }

    public static boolean isNull(Object o) {
        if (o == null) return true;
        if (o instanceof String) return isBlank((String) o);
        return false;
    }

    public static Map<String, Object> login(String name, String pass) {
        Map<String, Object> map = new HashMap<>();
        String sql = "select * from admin where username = '" + name + "'";
        Statement statement = getStatement();
        try {
            ResultSet resultSet = statement.executeQuery(sql);
            if (resultSet.next()) {
                String password = resultSet.getString("password");
                if (pass.equals(password)) {
                    map.put("status", 200);
                    map.put("mess", "登录成功");
                } else {
                    map.put("status", 201);
                    map.put("data", null);
                    map.put("mess", "密码错误");
                }
            } else {
                map.put("status", 202);
                map.put("data", null);
                    map.put("mess", "账号不存在");
            }
        } catch (Exception e) {
            map.put("status", 500);
            map.put("data", null);
            map.put("mess", "发生错误");
        }
        return map;
    }
}
