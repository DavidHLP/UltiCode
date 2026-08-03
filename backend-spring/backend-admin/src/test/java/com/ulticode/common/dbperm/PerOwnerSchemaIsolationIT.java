package com.ulticode.common.dbperm;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P5-SCHEMA-001: Per-Owner schema isolation integration test.
 *
 * <p>Starts a real MySQL 8.0 container, creates three schemas ({@code auth}, {@code admin},
 * {@code app}) with representative tables, provisions per-owner DB users with strict
 * schema-scoped grants, then verifies via actual JDBC connections as each shadow user:
 * <ul>
 *   <li>Owned-schema SELECT/INSERT/UPDATE/DELETE succeed.</li>
 *   <li>Cross-schema access returns MySQL error 1142 or 1044.</li>
 *   <li>{@code audit_outbox} append-only seam allows INSERT from auth_rw and app_rw.</li>
 *   <li>{@code information_schema} SCHEMA_PRIVILEGES + TABLE_PRIVILEGES match expected grant sets.</li>
 * </ul>
 */
@Testcontainers
@DisplayName("P5-SCHEMA-001: Per-Owner Schema Isolation (MySQL Testcontainers IT)")
class PerOwnerSchemaIsolationIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_p5")
            .withUsername("root")
            .withPassword("root");

    private static final String AUTH_RW_PW = "it_auth_rw_pw";
    private static final String ADMIN_RW_PW = "it_admin_rw_pw";
    private static final String APP_RW_PW = "it_app_rw_pw";

    private static String noDbJdbcUrl() {
        String url = mysql.getJdbcUrl();
        int slashIdx = url.indexOf("/", "jdbc:mysql://".length());
        int qIdx = url.indexOf("?", slashIdx);
        if (qIdx > 0) {
            return url.substring(0, slashIdx + 1) + "?" + url.substring(qIdx + 1);
        }
        return url.substring(0, slashIdx + 1);
    }

    private static Connection connectAs(String user, String password) throws SQLException {
        return DriverManager.getConnection(noDbJdbcUrl(), user, password);
    }

    /**
     * Queries information_schema for both SCHEMA_PRIVILEGES and TABLE_PRIVILEGES,
     * sums per-schema grant count, and returns a 3-element array:
     * {@code [authCount, adminCount, appCount]}.
     */
    private static int[] countGrants(Connection c, String user) throws SQLException {
        int[] counts = new int[3]; // auth=0, admin=1, app=2
        try (Statement s = c.createStatement()) {
            var rs = s.executeQuery(
                "SELECT TABLE_SCHEMA FROM information_schema.SCHEMA_PRIVILEGES " +
                "WHERE GRANTEE LIKE '%" + user + "%' AND TABLE_SCHEMA IN ('auth','admin','app')");
            while (rs.next()) {
                counts[schemaIndex(rs.getString("TABLE_SCHEMA"))]++;
            }
            rs = s.executeQuery(
                "SELECT TABLE_SCHEMA FROM information_schema.TABLE_PRIVILEGES " +
                "WHERE GRANTEE LIKE '%" + user + "%' AND TABLE_SCHEMA IN ('auth','admin','app')");
            while (rs.next()) {
                counts[schemaIndex(rs.getString("TABLE_SCHEMA"))]++;
            }
        }
        return counts;
    }

    private static int schemaIndex(String schema) {
        return switch (schema) {
            case "auth" -> 0;
            case "admin" -> 1;
            case "app" -> 2;
            default -> -1;
        };
    }

    @BeforeAll
    static void provisionSchemas() throws Exception {
        try (Connection conn = DriverManager.getConnection(mysql.getJdbcUrl(), "root", "root");
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE SCHEMA IF NOT EXISTS `auth`");
            stmt.execute("CREATE SCHEMA IF NOT EXISTS `admin`");
            stmt.execute("CREATE SCHEMA IF NOT EXISTS `app`");

            stmt.execute("CREATE TABLE `auth`.`users` (id VARCHAR(40) PRIMARY KEY, username VARCHAR(120))");
            stmt.execute("CREATE TABLE `auth`.`refresh_tokens` (id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40))");

            stmt.execute("CREATE TABLE `admin`.`audit_logs` (id VARCHAR(40) PRIMARY KEY, action VARCHAR(60))");
            stmt.execute("CREATE TABLE `admin`.`audit_outbox` (id VARCHAR(40) PRIMARY KEY, performer_id VARCHAR(40))");
            stmt.execute("CREATE TABLE `admin`.`system_settings` (`key` VARCHAR(50) PRIMARY KEY, `value` TEXT)");

            stmt.execute("CREATE TABLE `app`.`problems` (id BIGINT PRIMARY KEY, title VARCHAR(255))");
            stmt.execute("CREATE TABLE `app`.`submissions` (id VARCHAR(40) PRIMARY KEY, user_id VARCHAR(40))");

            stmt.execute("CREATE USER IF NOT EXISTS 'auth_rw'@'%'");
            stmt.execute("CREATE USER IF NOT EXISTS 'admin_rw'@'%'");
            stmt.execute("CREATE USER IF NOT EXISTS 'app_rw'@'%'");
            stmt.execute("ALTER USER 'auth_rw'@'%' IDENTIFIED BY '" + AUTH_RW_PW + "'");
            stmt.execute("ALTER USER 'admin_rw'@'%' IDENTIFIED BY '" + ADMIN_RW_PW + "'");
            stmt.execute("ALTER USER 'app_rw'@'%' IDENTIFIED BY '" + APP_RW_PW + "'");

            // Clean slate
            stmt.execute("REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'auth_rw'@'%'");
            stmt.execute("REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'admin_rw'@'%'");
            stmt.execute("REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'app_rw'@'%'");

            // USAGE allows connection to the server (zero table access)
            stmt.execute("GRANT USAGE ON *.* TO 'auth_rw'@'%'");
            stmt.execute("GRANT USAGE ON *.* TO 'admin_rw'@'%'");
            stmt.execute("GRANT USAGE ON *.* TO 'app_rw'@'%'");

            // Strict per-schema grants
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `auth`.* TO 'auth_rw'@'%'");
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `admin`.* TO 'admin_rw'@'%'");
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `app`.* TO 'app_rw'@'%'");

            // Cross-domain outbox seam: append-only INSERT on audit_outbox ONLY
            stmt.execute("GRANT INSERT ON `admin`.`audit_outbox` TO 'auth_rw'@'%'");
            stmt.execute("GRANT INSERT ON `admin`.`audit_outbox` TO 'app_rw'@'%'");

            stmt.execute("FLUSH PRIVILEGES");
        }
    }

    @AfterAll
    static void cleanup() {
        mysql.stop();
    }

    // ==================== auth_rw ====================

    @Nested
    @DisplayName("auth_rw: owns auth schema")
    class AuthRw {

        @Test
        @DisplayName("PERMITTED: INSERT/SELECT on auth.users")
        void canInsertSelectOwnSchema() throws Exception {
            try (Connection c = connectAs("auth_rw", AUTH_RW_PW);
                 Statement s = c.createStatement()) {
                s.execute("INSERT INTO `auth`.`users` (id, username) VALUES ('u-it-1', 'testuser')");
                var rs = s.executeQuery("SELECT COUNT(*) FROM `auth`.`users`");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
            }
        }

        @Test
        @DisplayName("PERMITTED: UPDATE/DELETE on auth.refresh_tokens")
        void canUpdateDeleteOwnSchema() throws Exception {
            try (Connection c = connectAs("auth_rw", AUTH_RW_PW);
                 Statement s = c.createStatement()) {
                s.execute("INSERT INTO `auth`.`refresh_tokens` (id, user_id) VALUES ('rt-1', 'u-it-1')");
                s.execute("UPDATE `auth`.`refresh_tokens` SET user_id = 'u-it-2' WHERE id = 'rt-1'");
                s.execute("DELETE FROM `auth`.`refresh_tokens` WHERE id = 'rt-1'");
            }
        }

        @Test
        @DisplayName("DENIED: SELECT on app.problems (cross-schema)")
        void deniedSelectOnAppSchema() {
            assertThatThrownBy(() -> {
                try (Connection c = connectAs("auth_rw", AUTH_RW_PW);
                     Statement s = c.createStatement()) {
                    s.executeQuery("SELECT * FROM `app`.`problems`");
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isIn(1142, 1044));
        }

        @Test
        @DisplayName("DENIED: INSERT on app.submissions (cross-schema)")
        void deniedInsertOnAppSchema() {
            assertThatThrownBy(() -> {
                try (Connection c = connectAs("auth_rw", AUTH_RW_PW);
                     PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO `app`.`submissions` (id, user_id) VALUES (?, ?)")) {
                    ps.setString(1, "s-1");
                    ps.setString(2, "u-1");
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isIn(1142, 1044));
        }

        @Test
        @DisplayName("DENIED: SELECT on admin.system_settings (cross-schema)")
        void deniedSelectOnAdminSchema() {
            assertThatThrownBy(() -> {
                try (Connection c = connectAs("auth_rw", AUTH_RW_PW);
                     Statement s = c.createStatement()) {
                    s.executeQuery("SELECT * FROM `admin`.`system_settings`");
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isIn(1142, 1044));
        }

        @Test
        @DisplayName("PERMITTED: INSERT on admin.audit_outbox (append-only outbox seam)")
        void canAppendAuditOutbox() throws Exception {
            try (Connection c = connectAs("auth_rw", AUTH_RW_PW);
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO `admin`.`audit_outbox` (id, performer_id) VALUES (?, ?)")) {
                ps.setString(1, "ao-1");
                ps.setString(2, "u-it-1");
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("DENIED: SELECT on admin.audit_outbox (append-only, no read)")
        void deniedSelectOnAuditOutbox() {
            assertThatThrownBy(() -> {
                try (Connection c = connectAs("auth_rw", AUTH_RW_PW);
                     Statement s = c.createStatement()) {
                    s.executeQuery("SELECT * FROM `admin`.`audit_outbox`");
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isIn(1142, 1044));
        }

        @Test
        @DisplayName("information_schema: auth_rw has 4 auth grants, 1 admin audit_outbox INSERT, 0 app")
        void authRw_schemaPrivilegeVerification() throws Exception {
            try (Connection c = connectAs("auth_rw", AUTH_RW_PW)) {
                int[] grants = countGrants(c, "auth_rw");
                assertThat(grants[0]).as("auth_rw should have SELECT/INSERT/UPDATE/DELETE on auth schema (4 rows)").isEqualTo(4);
                assertThat(grants[1]).as("auth_rw should have only INSERT on admin.audit_outbox (1 row)").isEqualTo(1);
                assertThat(grants[2]).as("auth_rw should have ZERO privileges on app schema").isEqualTo(0);
            }
        }
    }

    // ==================== admin_rw ====================

    @Nested
    @DisplayName("admin_rw: owns admin schema")
    class AdminRw {

        @Test
        @DisplayName("PERMITTED: INSERT/SELECT on admin.audit_logs")
        void canInsertSelectOwnSchema() throws Exception {
            try (Connection c = connectAs("admin_rw", ADMIN_RW_PW);
                 Statement s = c.createStatement()) {
                s.execute("INSERT INTO `admin`.`audit_logs` (id, action) VALUES ('al-1', 'TEST')");
                var rs = s.executeQuery("SELECT COUNT(*) FROM `admin`.`audit_logs`");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
            }
        }

        @Test
        @DisplayName("DENIED: SELECT on auth.users (cross-schema)")
        void deniedSelectOnAuthSchema() {
            assertThatThrownBy(() -> {
                try (Connection c = connectAs("admin_rw", ADMIN_RW_PW);
                     Statement s = c.createStatement()) {
                    s.executeQuery("SELECT * FROM `auth`.`users`");
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isIn(1142, 1044));
        }

        @Test
        @DisplayName("DENIED: INSERT on app.problems (cross-schema)")
        void deniedInsertOnAppSchema() {
            assertThatThrownBy(() -> {
                try (Connection c = connectAs("admin_rw", ADMIN_RW_PW);
                     PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO `app`.`problems` (id, title) VALUES (?, ?)")) {
                    ps.setLong(1, 999);
                    ps.setString(2, "Hacked");
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isIn(1142, 1044));
        }

        @Test
        @DisplayName("information_schema: admin_rw has 0 auth, 4 admin, 0 app")
        void adminRw_schemaPrivilegeVerification() throws Exception {
            try (Connection c = connectAs("admin_rw", ADMIN_RW_PW)) {
                int[] grants = countGrants(c, "admin_rw");
                assertThat(grants[0]).as("admin_rw should have ZERO privileges on auth schema").isEqualTo(0);
                assertThat(grants[1]).as("admin_rw should have SELECT/INSERT/UPDATE/DELETE on admin schema (4 rows)").isEqualTo(4);
                assertThat(grants[2]).as("admin_rw should have ZERO privileges on app schema").isEqualTo(0);
            }
        }
    }

    // ==================== app_rw ====================

    @Nested
    @DisplayName("app_rw: owns app schema")
    class AppRw {

        @Test
        @DisplayName("PERMITTED: INSERT/SELECT on app.problems")
        void canInsertSelectOwnSchema() throws Exception {
            try (Connection c = connectAs("app_rw", APP_RW_PW);
                 Statement s = c.createStatement()) {
                s.execute("INSERT INTO `app`.`problems` (id, title) VALUES (1, 'Test Problem')");
                var rs = s.executeQuery("SELECT COUNT(*) FROM `app`.`problems`");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
            }
        }

        @Test
        @DisplayName("DENIED: SELECT on auth.users (cross-schema)")
        void deniedSelectOnAuthSchema() {
            assertThatThrownBy(() -> {
                try (Connection c = connectAs("app_rw", APP_RW_PW);
                     Statement s = c.createStatement()) {
                    s.executeQuery("SELECT * FROM `auth`.`users`");
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isIn(1142, 1044));
        }

        @Test
        @DisplayName("DENIED: UPDATE on auth.users (cross-schema)")
        void deniedUpdateOnAuthSchema() {
            assertThatThrownBy(() -> {
                try (Connection c = connectAs("app_rw", APP_RW_PW);
                     PreparedStatement ps = c.prepareStatement(
                         "UPDATE `auth`.`users` SET username = 'hacked' WHERE id = 'u-1'")) {
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isIn(1142, 1044));
        }

        @Test
        @DisplayName("DENIED: SELECT on admin.system_settings (cross-schema)")
        void deniedSelectOnAdminSchema() {
            assertThatThrownBy(() -> {
                try (Connection c = connectAs("app_rw", APP_RW_PW);
                     Statement s = c.createStatement()) {
                    s.executeQuery("SELECT * FROM `admin`.`system_settings`");
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isIn(1142, 1044));
        }

        @Test
        @DisplayName("PERMITTED: INSERT on admin.audit_outbox (append-only outbox seam)")
        void canAppendAuditOutbox() throws Exception {
            try (Connection c = connectAs("app_rw", APP_RW_PW);
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO `admin`.`audit_outbox` (id, performer_id) VALUES (?, ?)")) {
                ps.setString(1, "ao-2");
                ps.setString(2, "u-it-app");
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("DENIED: SELECT on admin.audit_outbox (append-only, no read)")
        void deniedSelectOnAuditOutbox() {
            assertThatThrownBy(() -> {
                try (Connection c = connectAs("app_rw", APP_RW_PW);
                     Statement s = c.createStatement()) {
                    s.executeQuery("SELECT * FROM `admin`.`audit_outbox`");
                }
            }).isInstanceOf(SQLException.class)
              .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isIn(1142, 1044));
        }

        @Test
        @DisplayName("information_schema: app_rw has 0 auth, 1 admin audit_outbox INSERT, 4 app")
        void appRw_schemaPrivilegeVerification() throws Exception {
            try (Connection c = connectAs("app_rw", APP_RW_PW)) {
                int[] grants = countGrants(c, "app_rw");
                assertThat(grants[0]).as("app_rw should have ZERO privileges on auth schema").isEqualTo(0);
                assertThat(grants[1]).as("app_rw should have only INSERT on admin.audit_outbox (1 row)").isEqualTo(1);
                assertThat(grants[2]).as("app_rw should have SELECT/INSERT/UPDATE/DELETE on app schema (4 rows)").isEqualTo(4);
            }
        }
    }
}
