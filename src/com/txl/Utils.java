package com.txl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Utils {
    public static String iconPath = "";
    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    // 默认密码，首次启动时自动写入数据库；生产环境请修改
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    public static final String MANUAL_CATEGORY_OPTION = "其他(用户手动输入)";
    private static final String[] DEFAULT_CONTACT_CATEGORIES = {
            "OEM",
            "ODM",
            "模具厂",
            "方案商",
            "化工厂（油墨）",
            "喇叭厂",
            "电池厂",
            "钢丝",
            "耳挂厂",
            "原厂（方案）",
            "五金厂",
            "表面处理",
            "包装厂",
            "保护套",
            "第三方认证",
            MANUAL_CATEGORY_OPTION
    };
    private static final String[] CONTACT_CSV_HEADERS = {
            "编号", "姓名", "分类", "电话", "邮箱", "地址", "公司", "岗位", "备注"
    };
    private static final Map<String, String> LEGACY_CONTACT_CATEGORY_MAP = createLegacyContactCategoryMap();
    private static final String CONTACT_CATEGORY_DISPLAY_SQL = createContactCategoryDisplaySql();
    private static final String[] CONTACT_GLOBAL_SEARCH_EXPRESSIONS = {
            "ifnull(c_name,'')",
            CONTACT_CATEGORY_DISPLAY_SQL,
            "ifnull(c_phone,'')",
            "ifnull(c_email,'')",
            "ifnull(c_address,'')",
            "ifnull(c_company,'')",
            "ifnull(c_job_title,'')",
            "ifnull(notes,'')",
            "ifnull(c_group_name,'')",
            "ifnull(c_birthday,'')"
    };
    private static final Set<String> CONTACT_LIKE_FIELDS = new HashSet<>(Arrays.asList("c_name", "c_company"));

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

        new File(DATA_DIR).mkdirs();
        new File(BACKUP_DIR).mkdirs();

        migrateOldDb();
    }

    private static Map<String, String> createLegacyContactCategoryMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("1", "伙伴");
        map.put("2", "家人");
        map.put("3", "亲戚");
        map.put("4", "朋友");
        map.put("5", "同事");
        map.put("6", "客户");
        map.put("7", "其他");
        return map;
    }

    private static String createContactCategoryDisplaySql() {
        StringBuilder builder = new StringBuilder("case");
        LEGACY_CONTACT_CATEGORY_MAP.forEach((storedValue, displayValue) -> builder
                .append(" when c_nickname='")
                .append(storedValue.replace("'", "''"))
                .append("' then '")
                .append(displayValue.replace("'", "''"))
                .append("'"));
        builder.append(" else ifnull(c_nickname,'') end");
        return builder.toString();
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
            ResultSet rs = stmt.executeQuery("SELECT COUNT(1) FROM admin WHERE username='" + DEFAULT_ADMIN_USERNAME + "'");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO admin(username,password,name,email) VALUES('"
                        + DEFAULT_ADMIN_USERNAME + "','" + DEFAULT_ADMIN_PASSWORD + "','管理员',NULL)");
            }
            rs.close();
            stmt.executeUpdate("UPDATE admin SET password='" + DEFAULT_ADMIN_PASSWORD
                    + "' WHERE username='" + DEFAULT_ADMIN_USERNAME + "'");

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

            File[] backups = new File(BACKUP_DIR).listFiles((dir, name) -> name.startsWith("txl_") && name.endsWith(".db"));
            if (backups != null && backups.length > MAX_BACKUPS) {
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

    public static String[] getDefaultContactCategories() {
        return DEFAULT_CONTACT_CATEGORIES.clone();
    }

    public static boolean isManualCategoryOption(String value) {
        return MANUAL_CATEGORY_OPTION.equals(value);
    }

    public static String normalizeCategoryValue(String value) {
        if (isBlank(value)) return "";
        String normalized = value.trim();
        if (isManualCategoryOption(normalized)) {
            return "其他";
        }
        return normalized;
    }

    public static String toContactCategoryDisplayValue(String value) {
        if (isBlank(value)) return "";
        String normalized = normalizeCategoryValue(value);
        if (LEGACY_CONTACT_CATEGORY_MAP.containsKey(normalized)) {
            return LEGACY_CONTACT_CATEGORY_MAP.get(normalized);
        }
        return normalized;
    }

    public static String toContactCategoryStoredValue(String value) {
        if (isBlank(value)) return "";
        String normalized = normalizeCategoryValue(value);
        for (Map.Entry<String, String> entry : LEGACY_CONTACT_CATEGORY_MAP.entrySet()) {
            if (normalized.equals(entry.getValue()) && !"其他".equals(normalized)) {
                return entry.getKey();
            }
        }
        return normalized;
    }

    public static int exportContactsToCsv(File file, Map<String, Object> filters) throws Exception {
        StringBuilder sql = new StringBuilder(
                "select c_id,c_name,c_nickname,c_phone,c_email,c_address,c_company,c_job_title,notes from contact where 1=1");
        List<String> params = new ArrayList<>();
        appendContactFilters(sql, params, filters);
        sql.append(" order by c_id asc");

        int count = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
             PreparedStatement statement = prepareStatement(sql.toString(), params);
             ResultSet rs = statement.executeQuery()) {
            writer.write('\uFEFF');
            writer.write(toCsvLine(Arrays.asList(CONTACT_CSV_HEADERS)));
            while (rs.next()) {
                count++;
                writer.newLine();
                writer.write(toCsvLine(Arrays.asList(
                        rs.getString("c_id"),
                        rs.getString("c_name"),
                        toContactCategoryDisplayValue(rs.getString("c_nickname")),
                        rs.getString("c_phone"),
                        rs.getString("c_email"),
                        rs.getString("c_address"),
                        rs.getString("c_company"),
                        rs.getString("c_job_title"),
                        rs.getString("notes")
                )));
            }
        }
        return count;
    }

    public static PreparedStatement prepareContactCountStatement(Map<String, Object> filters) throws Exception {
        StringBuilder sql = new StringBuilder("select count(1) as total from contact where 1=1");
        List<String> params = new ArrayList<>();
        appendContactFilters(sql, params, filters);
        return prepareStatement(sql.toString(), params);
    }

    public static PreparedStatement prepareContactListStatement(Map<String, Object> filters, int offset, int limit) throws Exception {
        StringBuilder sql = new StringBuilder(
                "select c_id,c_name,c_nickname,c_phone,c_email,c_address,c_company,c_job_title,notes from contact where 1=1");
        List<String> params = new ArrayList<>();
        appendContactFilters(sql, params, filters);
        sql.append(" limit ? offset ?");

        PreparedStatement statement = prepareStatement(sql.toString(), params);
        int parameterIndex = params.size() + 1;
        statement.setInt(parameterIndex++, Math.max(1, limit));
        statement.setInt(parameterIndex, Math.max(0, offset));
        return statement;
    }

    public static ImportResult importContactsFromCsv(File file) throws Exception {
        ImportResult result = new ImportResult();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
             PreparedStatement existsStatement = conn.prepareStatement("select count(1) from contact where c_id=?");
             PreparedStatement insertStatement = conn.prepareStatement(
                     "insert into contact(c_name,c_nickname,c_phone,c_email,c_address,c_company,c_job_title,notes) values(?,?,?,?,?,?,?,?)");
             PreparedStatement updateStatement = conn.prepareStatement(
                     "update contact set c_name=?,c_nickname=?,c_phone=?,c_email=?,c_address=?,c_company=?,c_job_title=?,notes=? where c_id=?")) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return result;
            }

            Map<String, Integer> headerIndex = buildCsvHeaderIndex(parseCsvLine(headerLine));
            if (!headerIndex.containsKey("姓名")) {
                throw new IOException("导入文件缺少“姓名”列");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (isBlank(line)) {
                    continue;
                }
                List<String> cells = parseCsvLine(line);
                String cIdValue = getCsvCell(headerIndex, cells, "编号").trim();
                String cNameValue = getCsvCell(headerIndex, cells, "姓名").trim();
                if (isBlank(cNameValue)) {
                    result.skipped++;
                    continue;
                }

                String cNicknameValue = toContactCategoryStoredValue(getCsvCell(headerIndex, cells, "分类"));
                String cPhoneValue = getCsvCell(headerIndex, cells, "电话").trim();
                String cEmailValue = getCsvCell(headerIndex, cells, "邮箱").trim();
                String cAddressValue = getCsvCell(headerIndex, cells, "地址").trim();
                String cCompanyValue = getCsvCell(headerIndex, cells, "公司").trim();
                String cJobTitleValue = getCsvCell(headerIndex, cells, "岗位").trim();
                String notesValue = getCsvCell(headerIndex, cells, "备注").trim();

                if (!isBlank(cIdValue) && contactExists(existsStatement, cIdValue)) {
                    bindContactStatement(updateStatement, cNameValue, cNicknameValue, cPhoneValue, cEmailValue,
                            cAddressValue, cCompanyValue, cJobTitleValue, notesValue);
                    updateStatement.setString(9, cIdValue);
                    updateStatement.executeUpdate();
                    result.updated++;
                } else {
                    bindContactStatement(insertStatement, cNameValue, cNicknameValue, cPhoneValue, cEmailValue,
                            cAddressValue, cCompanyValue, cJobTitleValue, notesValue);
                    insertStatement.executeUpdate();
                    result.inserted++;
                }
            }
        }
        return result;
    }

    private static void bindContactStatement(PreparedStatement statement,
                                             String cNameValue,
                                             String cNicknameValue,
                                             String cPhoneValue,
                                             String cEmailValue,
                                             String cAddressValue,
                                             String cCompanyValue,
                                             String cJobTitleValue,
                                             String notesValue) throws Exception {
        statement.setString(1, cNameValue);
        statement.setString(2, cNicknameValue);
        statement.setString(3, cPhoneValue);
        statement.setString(4, cEmailValue);
        statement.setString(5, cAddressValue);
        statement.setString(6, cCompanyValue);
        statement.setString(7, cJobTitleValue);
        statement.setString(8, notesValue);
    }

    private static boolean contactExists(PreparedStatement statement, String cIdValue) throws Exception {
        statement.setString(1, cIdValue);
        ResultSet rs = statement.executeQuery();
        boolean exists = rs.next() && rs.getInt(1) > 0;
        rs.close();
        return exists;
    }

    private static PreparedStatement prepareStatement(String sql, List<String> params) throws Exception {
        PreparedStatement statement = conn.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            statement.setString(i + 1, params.get(i));
        }
        return statement;
    }

    private static void appendContactFilters(StringBuilder sql, List<String> params, Map<String, Object> filters) {
        if (filters == null) {
            return;
        }
        String keyword = normalizeFilterValue(filters.get("keyword"));
        if (!isBlank(keyword)) {
            appendContactKeywordFilter(sql, params, keyword);
            return;
        }
        filters.forEach((key, value) -> {
            String text = normalizeFilterValue(value);
            if (!isBlank(text)) {
                if (CONTACT_LIKE_FIELDS.contains(key)) {
                    sql.append(" and ").append(key).append(" like ?");
                    params.add("%" + text + "%");
                } else {
                    sql.append(" and ").append(key).append("=?");
                    params.add(text);
                }
            }
        });
    }

    private static String normalizeFilterValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static void appendContactKeywordFilter(StringBuilder sql, List<String> params, String keyword) {
        sql.append(" and (");
        for (int i = 0; i < CONTACT_GLOBAL_SEARCH_EXPRESSIONS.length; i++) {
            if (i > 0) {
                sql.append(" or ");
            }
            sql.append(CONTACT_GLOBAL_SEARCH_EXPRESSIONS[i]).append(" like ?");
            params.add("%" + keyword + "%");
        }
        sql.append(")");
    }

    private static String toCsvLine(List<String> cells) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escapeCsv(cells.get(i)));
        }
        return builder.toString();
    }

    private static String escapeCsv(String value) {
        String text = value == null ? "" : value;
        boolean needQuote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!needQuote) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static Map<String, Integer> buildCsvHeaderIndex(List<String> headers) {
        Map<String, Integer> headerIndex = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            headerIndex.put(cleanCsvCell(headers.get(i)), i);
        }
        return headerIndex;
    }

    private static String getCsvCell(Map<String, Integer> headerIndex, List<String> cells, String headerName) {
        Integer index = headerIndex.get(headerName);
        if (index == null || index < 0 || index >= cells.size()) {
            return "";
        }
        return cleanCsvCell(cells.get(index));
    }

    private static String cleanCsvCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "").trim();
    }

    private static List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
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

    public static class ImportResult {
        public int inserted;
        public int updated;
        public int skipped;
    }
}
