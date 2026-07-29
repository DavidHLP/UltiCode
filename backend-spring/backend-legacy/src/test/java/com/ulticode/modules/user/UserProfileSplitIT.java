package com.ulticode.modules.user;

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
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5-USERPROFILE-001: Vertical split of users into account + profile.
 *
 * <p>Integration test proving the expand/backfill/checksum/shadow pattern against
 * a real MySQL 8.0 Testcontainers instance:
 * <ul>
 *   <li><b>Expand</b>: {@code user_profiles} table created alongside {@code users}.</li>
 *   <li><b>Backfill</b>: All non-deleted users copied into {@code user_profiles}.</li>
 *   <li><b>Checksum</b>: Row count equality — users (non-deleted) == user_profiles.</li>
 *   <li><b>Shadow compare</b>: Field-by-field equality for sampled rows.</li>
 *   <li><b>Dual-write</b>: After updating a profile field, the same field
 *       is reflected in user_profiles.</li>
 *   <li><b>Partial update</b>: Updating only one field (bio) preserves other backfilled fields.</li>
 * </ul>
 */
@Testcontainers
@DisplayName("P5-USERPROFILE-001: User Profile Vertical Split IT")
class UserProfileSplitIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_p5_up")
            .withUsername("root")
            .withPassword("root");

    private static Connection rootConn;

    @BeforeAll
    static void provision() throws Exception {
        rootConn = DriverManager.getConnection(mysql.getJdbcUrl(), "root", "root");
        try (Statement stmt = rootConn.createStatement()) {
            // Create users table (account + profile columns, matching canonical schema)
            stmt.execute("""
                CREATE TABLE `users` (
                  `id` varchar(40) NOT NULL,
                  `username` varchar(120) NOT NULL,
                  `name` varchar(120) DEFAULT NULL,
                  `email` varchar(255) DEFAULT NULL,
                  `avatar` varchar(255) DEFAULT NULL,
                  `password` varchar(255) DEFAULT NULL,
                  `bio` text,
                  `company` varchar(255) DEFAULT NULL,
                  `github` varchar(255) DEFAULT NULL,
                  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  `location` varchar(255) DEFAULT NULL,
                  `twitter` varchar(255) DEFAULT NULL,
                  `website` varchar(255) DEFAULT NULL,
                  `preferred_language` varchar(50) DEFAULT NULL,
                  `role` enum('USER','MODERATOR','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'USER',
                  `is_active` tinyint(1) NOT NULL DEFAULT '1',
                  `is_banned` tinyint(1) NOT NULL DEFAULT '0',
                  `banned_until` datetime(3) DEFAULT NULL,
                  `banned_reason` text,
                  `last_login_at` datetime(3) DEFAULT NULL,
                  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_users_username` (`username`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);

            // Create user_profiles table (expand phase)
            stmt.execute("""
                CREATE TABLE `user_profiles` (
                  `account_id` varchar(40) NOT NULL,
                  `name` varchar(120) DEFAULT NULL,
                  `avatar` varchar(255) DEFAULT NULL,
                  `bio` text,
                  `company` varchar(255) DEFAULT NULL,
                  `github` varchar(255) DEFAULT NULL,
                  `location` varchar(255) DEFAULT NULL,
                  `twitter` varchar(255) DEFAULT NULL,
                  `website` varchar(255) DEFAULT NULL,
                  `preferred_language` varchar(50) DEFAULT NULL,
                  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (`account_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);

            // Insert sample users — 5 active, 1 soft-deleted
            insertUser(stmt, "u-001", "alice", "Alice Wang", "alice@example.com", "/avatars/1.png",
                    "Backend dev", "TechCorp", "https://github.com/alice", "Beijing",
                    "@alice", "https://alice.dev", "java", 0);
            insertUser(stmt, "u-002", "bob", "Bob Li", "bob@example.com", "/avatars/2.png",
                    "Full-stack", "StartupX", "https://github.com/bob", "Shanghai",
                    "@bob", null, "python", 0);
            insertUser(stmt, "u-003", "carol", "Carol Zhang", null, null,
                    null, null, null, null, null, null, null, 0);
            insertUser(stmt, "u-004", "dave", "Dave Chen", "dave@example.com", null,
                    "Competitive programmer", null, "https://github.com/dave", "Guangzhou",
                    null, null, "cpp", 0);
            insertUser(stmt, "u-005", "eve", "Eve Liu", "eve@example.com", "/avatars/5.png",
                    "Security researcher", "SecureLab", null, "Shenzhen",
                    "@eve_security", "https://eve.io", "rust", 0);
            // Soft-deleted user — should NOT be backfilled
            insertUser(stmt, "u-006", "deleted_user", "Deleted", null, null,
                    null, null, null, null, null, null, null, 1);

            // Backfill: copy profile columns from users into user_profiles (non-deleted only)
            stmt.execute("""
                INSERT INTO `user_profiles` (`account_id`, `name`, `avatar`, `bio`, `company`, `github`, `location`, `twitter`, `website`, `preferred_language`)
                SELECT `id`, `name`, `avatar`, `bio`, `company`, `github`, `location`, `twitter`, `website`, `preferred_language`
                FROM `users`
                WHERE `is_deleted` = 0
                ON DUPLICATE KEY UPDATE
                  `name` = VALUES(`name`)
                """);
        }
    }

    private static void insertUser(Statement stmt, String id, String username, String name,
            String email, String avatar, String bio, String company, String github,
            String location, String twitter, String website, String preferredLanguage,
            int isDeleted) throws SQLException {
        String sql = String.format(
                "INSERT INTO `users` (`id`, `username`, `name`, `email`, `avatar`, `bio`, `company`, `github`, `location`, `twitter`, `website`, `preferred_language`, `is_deleted`) " +
                "VALUES ('%s', '%s', %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %d)",
                id, username, quote(name), quote(email), quote(avatar), quote(bio),
                quote(company), quote(github), quote(location), quote(twitter),
                quote(website), quote(preferredLanguage), isDeleted);
        stmt.execute(sql);
    }

    private static String quote(String s) {
        return s == null ? "NULL" : "'" + s.replace("'", "''") + "'";
    }

    @AfterAll
    static void cleanup() throws SQLException {
        if (rootConn != null) rootConn.close();
    }

    @Nested
    @DisplayName("Checksum: row count reconciliation")
    class Checksum {

        @Test
        @DisplayName("user_profiles count == non-deleted users count")
        void rowCountReconciliation() throws SQLException {
            try (Statement s = rootConn.createStatement()) {
                var rs1 = s.executeQuery("SELECT COUNT(*) FROM `users` WHERE `is_deleted` = 0");
                rs1.next();
                int usersCount = rs1.getInt(1);

                var rs2 = s.executeQuery("SELECT COUNT(*) FROM `user_profiles`");
                rs2.next();
                int profilesCount = rs2.getInt(1);

                assertThat(usersCount).as("5 non-deleted users should exist").isEqualTo(5);
                assertThat(profilesCount).as("5 user_profiles should exist (matching non-deleted users)").isEqualTo(5);
                assertThat(profilesCount).as("Checksum: profile count == non-deleted users count").isEqualTo(usersCount);
            }
        }

        @Test
        @DisplayName("soft-deleted user was NOT backfilled")
        void softDeletedExcluded() throws SQLException {
            try (Statement s = rootConn.createStatement()) {
                var rs = s.executeQuery("SELECT COUNT(*) FROM `user_profiles` WHERE `account_id` = 'u-006'");
                rs.next();
                assertThat(rs.getInt(1)).as("Soft-deleted user u-006 should not have a profile row").isZero();
            }
        }
    }

    @Nested
    @DisplayName("Shadow compare: field-by-field equality")
    class ShadowCompare {

        @Test
        @DisplayName("Sampled row u-001: all profile fields match between users and user_profiles")
        void shadowCompareFullRow() throws SQLException {
            try (Statement s = rootConn.createStatement()) {
                var rs = s.executeQuery(
                    "SELECT u.`name`, u.`avatar`, u.`bio`, u.`company`, u.`github`, u.`location`, u.`twitter`, u.`website`, u.`preferred_language`, " +
                    "       p.`name`, p.`avatar`, p.`bio`, p.`company`, p.`github`, p.`location`, p.`twitter`, p.`website`, p.`preferred_language` " +
                    "FROM `users` u JOIN `user_profiles` p ON u.`id` = p.`account_id` " +
                    "WHERE u.`id` = 'u-001'");
                assertThat(rs.next()).isTrue();
                String[] labels = {"name", "avatar", "bio", "company", "github", "location", "twitter", "website", "preferred_language"};
                for (int i = 0; i < 9; i++) {
                    String userVal = rs.getString(i + 1);
                    String profileVal = rs.getString(i + 10);
                    assertThat(profileVal).as("Field %s should match (user=%s)", labels[i], userVal).isEqualTo(userVal);
                }
            }
        }

        @Test
        @DisplayName("Sampled row u-003 (all-null profile): name field matches")
        void shadowCompareNullRow() throws SQLException {
            try (Statement s = rootConn.createStatement()) {
                var rs = s.executeQuery(
                    "SELECT u.`name`, p.`name` FROM `users` u JOIN `user_profiles` p ON u.`id` = p.`account_id` " +
                    "WHERE u.`id` = 'u-003'");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).as("users.name for u-003").isEqualTo("Carol Zhang");
                assertThat(rs.getString(2)).as("user_profiles.name for u-003").isEqualTo("Carol Zhang");
            }
        }

        @Test
        @DisplayName("All non-deleted users have a matching user_profiles row")
        void allUsersHaveProfile() throws SQLException {
            try (Statement s = rootConn.createStatement()) {
                var rs = s.executeQuery(
                    "SELECT u.`id` FROM `users` u LEFT JOIN `user_profiles` p ON u.`id` = p.`account_id` " +
                    "WHERE u.`is_deleted` = 0 AND p.`account_id` IS NULL");
                assertThat(rs.next()).as("Every non-deleted user should have a user_profiles row").isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Dual-write: updating a profile field mirrors to user_profiles")
    class DualWrite {

        @Test
        @DisplayName("Dual-write name update: both tables stay in sync")
        void dualWriteNameUpdate() throws SQLException {
            // Simulate dual-write: update both tables
            try (Statement s = rootConn.createStatement()) {
                s.execute("UPDATE `users` SET `name` = 'Alice Updated' WHERE `id` = 'u-001'");
                s.execute("UPDATE `user_profiles` SET `name` = 'Alice Updated' WHERE `account_id` = 'u-001'");
            }
            // Verify both tables match
            try (Statement s = rootConn.createStatement()) {
                var rs = s.executeQuery(
                    "SELECT u.`name`, p.`name` FROM `users` u JOIN `user_profiles` p ON u.`id` = p.`account_id` " +
                    "WHERE u.`id` = 'u-001'");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("Alice Updated");
                assertThat(rs.getString(2)).isEqualTo("Alice Updated");
            }
        }

        @Test
        @DisplayName("Backfill is idempotent: re-running does not duplicate rows")
        void backfillIdempotent() throws SQLException {
            try (Statement s = rootConn.createStatement()) {
                s.execute("""
                    INSERT INTO `user_profiles` (`account_id`, `name`, `avatar`, `bio`, `company`, `github`, `location`, `twitter`, `website`, `preferred_language`)
                    SELECT `id`, `name`, `avatar`, `bio`, `company`, `github`, `location`, `twitter`, `website`, `preferred_language`
                    FROM `users`
                    WHERE `is_deleted` = 0
                    ON DUPLICATE KEY UPDATE
                      `name` = VALUES(`name`)
                    """);
                var rs = s.executeQuery("SELECT COUNT(*) FROM `user_profiles`");
                rs.next();
                assertThat(rs.getInt(1)).as("Re-running backfill should not create duplicates").isEqualTo(5);
            }
        }
    }

    @Nested
    @DisplayName("Partial update preserves backfilled fields")
    class PartialUpdate {

        @Test
        @DisplayName("Update only bio in user_profiles: name and avatar remain from backfill")
        void partialUpdatePreservesBackfill() throws SQLException {
            // u-002 was backfilled with name='Bob Li', avatar='/avatars/2.png', bio='Full-stack'
            // Simulate a partial update: only bio changes
            try (Statement s = rootConn.createStatement()) {
                s.execute("UPDATE `user_profiles` SET `bio` = 'Updated bio only' WHERE `account_id` = 'u-002'");
            }
            try (Statement s = rootConn.createStatement()) {
                var rs = s.executeQuery(
                    "SELECT `name`, `avatar`, `bio` FROM `user_profiles` WHERE `account_id` = 'u-002'");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("name")).as("name should remain 'Bob Li' from backfill").isEqualTo("Bob Li");
                assertThat(rs.getString("avatar")).as("avatar should remain '/avatars/2.png' from backfill").isEqualTo("/avatars/2.png");
                assertThat(rs.getString("bio")).as("bio should be the updated value").isEqualTo("Updated bio only");
            }
        }
    }
}
