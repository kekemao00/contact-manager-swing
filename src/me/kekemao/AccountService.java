package me.kekemao;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Base64;

/**
 * 账号相关逻辑：登录校验、密码哈希、修改密码、失败锁定。
 * 密码以 PBKDF2 哈希存储，旧版本的明文密码会在首次登录成功后自动升级为哈希。
 */
public final class AccountService {
    public static final int MIN_PASSWORD_LENGTH = 6;
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final long LOCK_MILLIS = 30_000L;

    private static final String HASH_PREFIX = "pbkdf2_sha256";
    private static final int HASH_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static int failedAttempts;
    private static long lockedUntil;
    private static String currentUser;

    private AccountService() {
    }

    public enum Status {
        SUCCESS, INVALID_CREDENTIALS, LOCKED, ERROR
    }

    public static final class LoginResult {
        public final Status status;
        public final String message;
        /** 仍在使用默认密码，登录后应提示修改 */
        public final boolean usingDefaultPassword;
        /** 锁定剩余秒数（仅 LOCKED 时有意义） */
        public final int lockSeconds;

        private LoginResult(Status status, String message, boolean usingDefaultPassword, int lockSeconds) {
            this.status = status;
            this.message = message;
            this.usingDefaultPassword = usingDefaultPassword;
            this.lockSeconds = lockSeconds;
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }

    /** 首次运行时创建默认管理员账号（密码以哈希存储） */
    static void ensureDefaultAdmin(Connection conn) throws Exception {
        try (PreparedStatement query = conn.prepareStatement("SELECT COUNT(1) FROM admin WHERE username=?")) {
            query.setString(1, Utils.DEFAULT_ADMIN_USERNAME);
            try (ResultSet rs = query.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO admin(username,password,name,email) VALUES(?,?,?,NULL)")) {
            insert.setString(1, Utils.DEFAULT_ADMIN_USERNAME);
            insert.setString(2, hashPassword(Utils.DEFAULT_ADMIN_PASSWORD.toCharArray()));
            insert.setString(3, "管理员");
            insert.executeUpdate();
        }
    }

    /** 将管理员密码重置为默认密码（忘记密码时通过启动参数 --reset-password 使用） */
    public static boolean resetAdminPassword() {
        Connection conn = Utils.getConn();
        if (conn == null) return false;
        try (PreparedStatement update = conn.prepareStatement("UPDATE admin SET password=? WHERE username=?")) {
            update.setString(1, hashPassword(Utils.DEFAULT_ADMIN_PASSWORD.toCharArray()));
            update.setString(2, Utils.DEFAULT_ADMIN_USERNAME);
            if (update.executeUpdate() > 0) {
                return true;
            }
            ensureDefaultAdmin(conn);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static synchronized LoginResult login(String username, char[] password) {
        long now = System.currentTimeMillis();
        if (now < lockedUntil) {
            int seconds = (int) Math.ceil((lockedUntil - now) / 1000.0);
            return new LoginResult(Status.LOCKED, "尝试次数过多，请 " + seconds + " 秒后再试", false, seconds);
        }
        String name = username == null ? "" : username.trim();
        Connection conn = Utils.getConn();
        if (conn == null) {
            return new LoginResult(Status.ERROR, "数据库不可用，无法登录", false, 0);
        }
        try (PreparedStatement query = conn.prepareStatement("SELECT password FROM admin WHERE username=?")) {
            query.setString(1, name);
            String stored = null;
            try (ResultSet rs = query.executeQuery()) {
                if (rs.next()) {
                    stored = rs.getString("password");
                }
            }
            if (stored == null || !verifyPassword(password, stored)) {
                return onFailedAttempt();
            }
            if (!isHashed(stored)) {
                // 旧版本明文密码，登录成功后升级为哈希
                updatePasswordHash(conn, name, password);
            }
            failedAttempts = 0;
            lockedUntil = 0;
            currentUser = name;
            boolean usingDefault = Utils.DEFAULT_ADMIN_PASSWORD.equals(new String(password));
            return new LoginResult(Status.SUCCESS, "登录成功", usingDefault, 0);
        } catch (Exception e) {
            e.printStackTrace();
            return new LoginResult(Status.ERROR, "登录时发生错误：" + e.getMessage(), false, 0);
        }
    }

    private static LoginResult onFailedAttempt() {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            failedAttempts = 0;
            lockedUntil = System.currentTimeMillis() + LOCK_MILLIS;
            int seconds = (int) (LOCK_MILLIS / 1000);
            return new LoginResult(Status.LOCKED, "连续输错 " + MAX_FAILED_ATTEMPTS + " 次，请 " + seconds + " 秒后再试", false, seconds);
        }
        int remaining = MAX_FAILED_ATTEMPTS - failedAttempts;
        return new LoginResult(Status.INVALID_CREDENTIALS, "账号或密码错误，还可尝试 " + remaining + " 次", false, 0);
    }

    /** 修改当前登录账号的密码，成功返回 null，失败返回错误提示 */
    public static synchronized String changePassword(char[] oldPassword, char[] newPassword, char[] confirmPassword) {
        if (currentUser == null) {
            return "当前未登录";
        }
        if (oldPassword.length == 0) {
            return "请输入原密码";
        }
        String invalid = validateNewPassword(newPassword, confirmPassword);
        if (invalid != null) {
            return invalid;
        }
        if (Arrays.equals(oldPassword, newPassword)) {
            return "新密码不能与原密码相同";
        }
        Connection conn = Utils.getConn();
        if (conn == null) {
            return "数据库不可用";
        }
        try (PreparedStatement query = conn.prepareStatement("SELECT password FROM admin WHERE username=?")) {
            query.setString(1, currentUser);
            String stored = null;
            try (ResultSet rs = query.executeQuery()) {
                if (rs.next()) {
                    stored = rs.getString("password");
                }
            }
            if (stored == null || !verifyPassword(oldPassword, stored)) {
                return "原密码不正确";
            }
            updatePasswordHash(conn, currentUser, newPassword);
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return "修改失败：" + e.getMessage();
        }
    }

    static String validateNewPassword(char[] newPassword, char[] confirmPassword) {
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            return "新密码至少 " + MIN_PASSWORD_LENGTH + " 位";
        }
        for (char c : newPassword) {
            if (Character.isWhitespace(c)) {
                return "新密码不能包含空格";
            }
        }
        if (!Arrays.equals(newPassword, confirmPassword)) {
            return "两次输入的新密码不一致";
        }
        if (Utils.DEFAULT_ADMIN_PASSWORD.equals(new String(newPassword))) {
            return "新密码不能是默认密码";
        }
        return null;
    }

    public static synchronized void logout() {
        currentUser = null;
    }

    public static synchronized String getCurrentUser() {
        return currentUser;
    }

    private static void updatePasswordHash(Connection conn, String username, char[] password) throws Exception {
        try (PreparedStatement update = conn.prepareStatement("UPDATE admin SET password=? WHERE username=?")) {
            update.setString(1, hashPassword(password));
            update.setString(2, username);
            update.executeUpdate();
        }
    }

    // ========== 密码哈希 ==========

    static String hashPassword(char[] password) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, HASH_ITERATIONS);
        Base64.Encoder encoder = Base64.getEncoder();
        return HASH_PREFIX + "$" + HASH_ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
    }

    static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(HASH_PREFIX + "$");
    }

    static boolean verifyPassword(char[] password, String stored) throws Exception {
        if (stored == null) return false;
        if (!isHashed(stored)) {
            byte[] given = new String(password).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(given, stored.getBytes(StandardCharsets.UTF_8));
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4) return false;
        int iterations = Integer.parseInt(parts[1]);
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] salt = decoder.decode(parts[2]);
        byte[] expected = decoder.decode(parts[3]);
        byte[] actual = pbkdf2(password, salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
