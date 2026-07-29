package com.ulticode.modules.reconciliation;

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
 * P5-RECONCILE-001: Reconciliation jobs + cross-owner orphan detection IT.
 *
 * <p>Tests the reconciliation and orphan detection logic against a real MySQL 8.0
 * Testcontainers instance with seeded baseline data:
 * <ul>
 *   <li><b>Checksum reconciliation</b>: user_profiles count == non-deleted users count → zero drift.</li>
 *   <li><b>Orphan detection</b>: all cross-owner references resolve → zero orphans.</li>
 *   <li><b>Orphan detection (positive)</b>: introducing a dangling reference → orphan detected.</li>
 *   <li><b>Drift detection (positive)</b>: introducing extra user_profiles row → drift detected.</li>
 * </ul>
 */
@Testcontainers
@DisplayName("P5-RECONCILE-001: Reconciliation + Orphan Scanner IT")
class OwnerReconcilerIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_recon")
            .withUsername("root")
            .withPassword("root");

    private static Connection conn;
    private static OwnerReconciler reconciler;

    @BeforeAll
    static void provision() throws Exception {
        conn = DriverManager.getConnection(mysql.getJdbcUrl(), "root", "root");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE `reconciliation_runs` (
                  `run_id` varchar(40) NOT NULL,
                  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  `finished_at` datetime(3) DEFAULT NULL,
                  `owner` varchar(20) NOT NULL,
                  `status` varchar(20) NOT NULL DEFAULT 'RUNNING',
                  `divergence_count` int NOT NULL DEFAULT 0,
                  `orphan_count` int NOT NULL DEFAULT 0,
                  `detail` text,
                  PRIMARY KEY (`run_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);

            stmt.execute("""
                CREATE TABLE `users` (
                  `id` varchar(40) NOT NULL,
                  `username` varchar(120) NOT NULL,
                  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);

            stmt.execute("CREATE TABLE `refresh_tokens` (`id` varchar(40) NOT NULL, `user_id` varchar(40) NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            stmt.execute("CREATE TABLE `user_permissions` (`id` varchar(40) NOT NULL, `user_id` varchar(40) NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            stmt.execute("CREATE TABLE `audit_logs` (`id` varchar(40) NOT NULL, `performer_id` varchar(40) NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            stmt.execute("CREATE TABLE `user_profiles` (`account_id` varchar(40) NOT NULL, PRIMARY KEY (`account_id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            stmt.execute("CREATE TABLE `submissions` (`id` varchar(40) NOT NULL, `user_id` varchar(40) NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            stmt.execute("CREATE TABLE `solutions` (`id` varchar(40) NOT NULL, `user_id` varchar(40) NOT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 3 active + 1 soft-deleted users
            stmt.execute("INSERT INTO `users` (`id`, `username`, `is_deleted`) VALUES " +
                    "('u-001', 'alice', 0), ('u-002', 'bob', 0), ('u-003', 'carol', 0), ('u-del', 'deleted', 1)");

            // user_profiles: 3 matching active users
            stmt.execute("INSERT INTO `user_profiles` (`account_id`) VALUES ('u-001'), ('u-002'), ('u-003')");

            // Cross-owner refs that all resolve to active users
            stmt.execute("INSERT INTO `refresh_tokens` (`id`, `user_id`) VALUES ('rt-1', 'u-001')");
            stmt.execute("INSERT INTO `user_permissions` (`id`, `user_id`) VALUES ('up-1', 'u-002')");
            stmt.execute("INSERT INTO `audit_logs` (`id`, `performer_id`) VALUES ('al-1', 'u-001')");
            stmt.execute("INSERT INTO `submissions` (`id`, `user_id`) VALUES ('s-1', 'u-002'), ('s-2', 'u-003')");
            stmt.execute("INSERT INTO `solutions` (`id`, `user_id`) VALUES ('sol-1', 'u-001')");
        }

        var jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.SingleConnectionDataSource(conn, false));

        reconciler = new OwnerReconciler(jdbcTemplate, null, () -> "test-run-id");
    }

    @AfterAll
    static void cleanup() throws SQLException {
        if (conn != null) conn.close();
    }

    @Nested
    @DisplayName("Checksum reconciliation: baseline data")
    class Checksum {

        @Test
        @DisplayName("users → user_profiles: zero drift on baseline data")
        void zeroDriftBaseline() {
            var pair = new OwnerReconciler.ReconciliationPair("users", "user_profiles", "Auth");
            ReconciliationResult result = reconciler.reconcilePair(pair);

            assertThat(result.isDriftFree()).as(result.describe()).isTrue();
            assertThat(result.getSourceCount()).isEqualTo(3);
            assertThat(result.getTargetCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Orphan detection: baseline data")
    class OrphanScan {

        @Test
        @DisplayName("refresh_tokens.user_id → users: no orphans")
        void refreshTokensNoOrphans() {
            var ref = new OwnerReconciler.CrossOwnerRef("refresh_tokens", "user_id", "Auth", "users", "Auth");
            assertThat(reconciler.detectOrphans(ref).isOrphanFree()).as("no orphans expected").isTrue();
        }

        @Test
        @DisplayName("submissions.user_id → users: no orphans")
        void submissionsNoOrphans() {
            var ref = new OwnerReconciler.CrossOwnerRef("submissions", "user_id", "App", "users", "Auth");
            assertThat(reconciler.detectOrphans(ref).isOrphanFree()).as("no orphans expected").isTrue();
        }

        @Test
        @DisplayName("solutions.user_id → users: no orphans")
        void solutionsNoOrphans() {
            var ref = new OwnerReconciler.CrossOwnerRef("solutions", "user_id", "App", "users", "Auth");
            assertThat(reconciler.detectOrphans(ref).isOrphanFree()).as("no orphans expected").isTrue();
        }

        @Test
        @DisplayName("user_profiles.account_id → users: no orphans")
        void profilesNoOrphans() {
            var ref = new OwnerReconciler.CrossOwnerRef("user_profiles", "account_id", "App", "users", "Auth");
            assertThat(reconciler.detectOrphans(ref).isOrphanFree()).as("no orphans expected").isTrue();
        }

        @Test
        @DisplayName("audit_logs.performer_id → users: no orphans (Admin → Auth)")
        void auditLogsNoOrphans() {
            var ref = new OwnerReconciler.CrossOwnerRef("audit_logs", "performer_id", "Admin", "users", "Auth");
            assertThat(reconciler.detectOrphans(ref).isOrphanFree()).as("no orphans expected").isTrue();
        }
    }

    @Nested
    @DisplayName("Positive tests: drift and orphan detection")
    class PositiveDetection {

        @Test
        @DisplayName("Orphan detected: submission with non-existent user_id")
        void orphanDetected() throws SQLException {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO `submissions` (`id`, `user_id`) VALUES ('s-orphan', 'u-nonexistent')");
            }
            var ref = new OwnerReconciler.CrossOwnerRef("submissions", "user_id", "App", "users", "Auth");
            OrphanDetectionResult result = reconciler.detectOrphans(ref);

            assertThat(result.isOrphanFree()).isFalse();
            assertThat(result.getOrphanCount()).isEqualTo(1);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM `submissions` WHERE `id` = 's-orphan'");
            }
        }

        @Test
        @DisplayName("Drift detected: extra user_profiles row")
        void driftDetected() throws SQLException {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO `user_profiles` (`account_id`) VALUES ('u-extra')");
            }
            var pair = new OwnerReconciler.ReconciliationPair("users", "user_profiles", "Auth");
            ReconciliationResult result = reconciler.reconcilePair(pair);

            assertThat(result.isDriftFree()).isFalse();
            assertThat(result.getTargetCount()).isEqualTo(4);
            assertThat(result.getSourceCount()).isEqualTo(3);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM `user_profiles` WHERE `account_id` = 'u-extra'");
            }
        }

        @Test
        @DisplayName("Soft-deleted user referenced by child is NOT an orphan (intentional semantic)")
        void softDeletedNotOrphan() throws SQLException {
            // A submission referencing a soft-deleted user should NOT be flagged as orphan
            // because the user record still physically exists (is_deleted=1).
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO `submissions` (`id`, `user_id`) VALUES ('s-del-ref', 'u-del')");
            }
            var ref = new OwnerReconciler.CrossOwnerRef("submissions", "user_id", "App", "users", "Auth");
            OrphanDetectionResult result = reconciler.detectOrphans(ref);

            assertThat(result.isOrphanFree())
                    .as("Soft-deleted user exists physically; referencing it is NOT an orphan")
                    .isTrue();

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM `submissions` WHERE `id` = 's-del-ref'");
            }
        }
    }
}
