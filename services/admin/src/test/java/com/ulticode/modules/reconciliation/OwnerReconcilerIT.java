package com.ulticode.modules.reconciliation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.app.api.service.AppReconciliationReadPort;
import com.ulticode.auth.api.dto.AuthReconciliationOrphanCounts;
import com.ulticode.auth.api.service.ReconciliationQueryService;
import com.ulticode.submission.api.service.SubmissionReconciliationReadPort;
import com.ulticode.notification.api.service.NotificationReconciliationReadPort;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.lease.FencedJobLeaseMapper;
import com.ulticode.modules.lease.FencedJobLeaseService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * P7-RECON-AGGREGATOR-001: OwnerReconciler integration against a real
 * database — admin-local only.
 *
 * <p>Real SQL executes for the three admin-owned surfaces:
 * <ul>
 *   <li>{@code reconciliation_runs} persistence (ReconciliationRunMapper);</li>
 *   <li>{@code fenced_job_leases} acquisition/renewal/release (FencedJobLeaseMapper);</li>
 *   <li>{@code audit_logs.performer_id} orphan check (AuditOrphanMapper).</li>
 * </ul>
 * Auth and App facts come from faked owner ports/providers; Submission facts
 * are supplied by the owner reconciliation port and the admin audit check
 * runs against real SQL — cross-owner SQL has been removed from admin.
 *
 * <p>Bootstrap is a hand-rolled MyBatis-Plus {@link SqlSessionFactory}
 * over the container datasource so this test isolates the admin-owned
 * reconciliation mappers from unrelated service-shell infrastructure.
 */
@Testcontainers
@DisplayName("P7-RECON-AGGREGATOR-001: OwnerReconciler admin-local IT")
class OwnerReconcilerIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_admin_reconciler_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static SqlSessionFactory sessionFactory;
    private static SqlSession session;
    private static ReconciliationRunMapper runMapper;
    private static FencedJobLeaseMapper fencedJobLeaseMapper;
    private static AuditOrphanMapper auditOrphanMapper;
    private static final LocalDateTime LEGACY_WATERMARK =
            LocalDateTime.of(2026, 8, 27, 0, 0, 0, 123_000_000);

    @BeforeAll
    static void provision() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE `reconciliation_runs` (
                  `run_id` varchar(40) NOT NULL,
                  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  `finished_at` datetime(3) DEFAULT NULL,
                  `owner` varchar(20) NOT NULL,
                  `fence_token` bigint NOT NULL DEFAULT 0,
                  `status` varchar(20) NOT NULL DEFAULT 'RUNNING',
                  `divergence_count` int NOT NULL DEFAULT 0,
                  `orphan_count` int NOT NULL DEFAULT 0,
                  `detail` text,
                  PRIMARY KEY (`run_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
            stmt.execute("""
                CREATE TABLE `fenced_job_leases` (
                  `lease_name` varchar(120) NOT NULL,
                  `fence_token` bigint NOT NULL,
                  `owner_token` varchar(120) DEFAULT NULL,
                  `leased_until` datetime(3) DEFAULT NULL,
                  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (`lease_name`)
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
            stmt.execute("""
                CREATE TABLE `audit_logs` (
                  `id` varchar(40) NOT NULL,
                  `performer_id` varchar(40) DEFAULT NULL,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            stmt.execute("INSERT INTO `users` (`id`, `username`, `is_deleted`) VALUES " +
                    "('u-001', 'alice', 0), ('u-002', 'bob', 0), ('u-del', 'deleted', 1)");
            stmt.execute("INSERT INTO `audit_logs` (`id`, `performer_id`) VALUES " +
                    "('al-1', 'u-001'), ('al-2', 'u-del'), ('al-ghost', 'ghost-user')");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(4);
        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("""
                INSERT INTO reconciliation_runs
                    (run_id, started_at, owner, fence_token, status,
                     divergence_count, orphan_count, detail)
                VALUES (?, ?, 'ALL', 1, 'PARTIAL', 0, 0, ?),
                       (?, ?, 'ALL', 1, 'COMPLETED', 0, 0, ?),
                       (?, ?, 'ALL', 1, 'COMPLETED', 0, 0, ?),
                       (?, ?, 'ALL', 1, 'PARTIAL', 0, 0, ?),
                       (?, ?, 'ALL', 1, 'PARTIAL', 0, 0, ?)
                """,
                "legacy-partial", LEGACY_WATERMARK.plusDays(1), checkpointDetail(LEGACY_WATERMARK),
                "legacy-completed", LEGACY_WATERMARK.plusDays(2), checkpointDetail(LEGACY_WATERMARK),
                "legacy-full-completed", LEGACY_WATERMARK.plusDays(3), fullCheckpointDetail(),
                "legacy-unknown-mode", LEGACY_WATERMARK.plusDays(4), unknownCheckpointDetail(),
                "legacy-invalid-watermark", LEGACY_WATERMARK.plusDays(5), invalidCheckpointDetail());
        try (Connection connection = dataSource.getConnection()) {
            Path migration = findRepositoryRoot().resolve(
                    "init-db/migrations/admin/V20260913140000__Add_Reconciliation_Checkpoint_Fields.sql");
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new FileSystemResource(migration.toFile())));
        }

        MybatisConfiguration mybatisConfiguration = new MybatisConfiguration();
        mybatisConfiguration.setEnvironment(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        mybatisConfiguration.addMapper(ReconciliationRunMapper.class);
        mybatisConfiguration.addMapper(FencedJobLeaseMapper.class);
        mybatisConfiguration.addMapper(AuditOrphanMapper.class);
        sessionFactory = new MybatisSqlSessionFactoryBuilder().build(mybatisConfiguration);
        session = sessionFactory.openSession(true);
        runMapper = session.getMapper(ReconciliationRunMapper.class);
        fencedJobLeaseMapper = session.getMapper(FencedJobLeaseMapper.class);
        auditOrphanMapper = session.getMapper(AuditOrphanMapper.class);
    }

    @AfterAll
    static void cleanup() throws SQLException {
        if (session != null) {
            session.close();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private OwnerReconciler newReconciler() {
        ReconciliationQueryService authService = mock(ReconciliationQueryService.class);
        when(authService.countActiveUsers()).thenReturn(RpcResult.success(2L, "t-system"));
        when(authService.countAuthOrphans())
                .thenReturn(RpcResult.success(AuthReconciliationOrphanCounts.ZERO, "t-system"));
        when(authService.existingUserIds(any()))
                .thenReturn(RpcResult.success(Set.of("u-001", "u-del"), "t-system"));

        AppReconciliationReadPort appPort = mock(AppReconciliationReadPort.class);
        when(appPort.countUserProfiles()).thenReturn(2L);
        when(appPort.countOrphans()).thenReturn(ReconciliationOrphanCounts.ZERO);

        SubmissionReconciliationReadPort submissionPort = mock(SubmissionReconciliationReadPort.class);
        when(submissionPort.findUserReferenceCounts("", null,
                SubmissionReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(List.of());
        NotificationReconciliationReadPort notificationPort = mock(NotificationReconciliationReadPort.class);
        when(notificationPort.findUserReferenceCounts("", null,
                NotificationReconciliationReadPort.MAX_PAGE_SIZE)).thenReturn(List.of());

        OwnerReconciler reconciler = new OwnerReconciler(
                runMapper, new FixedUuidGenerator("run-it-1"), appPort,
                submissionPort, notificationPort, auditOrphanMapper, null,
                new FencedJobLeaseService(fencedJobLeaseMapper, Clock.systemUTC()),
                new ObjectMapper());
        ReflectionTestUtils.setField(reconciler, "authQueryService", authService);
        return reconciler;
    }

    @Test
    @DisplayName("real run persistence + real audit orphan SQL detect ghost performer")
    void runPersistsAndDetectsAuditOrphan() {
        ReconciliationRun run = newReconciler().runReconciliation();

        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getDivergenceCount()).isZero();
        assertThat(run.getOrphanCount()).isEqualTo(1);
        assertThat(run.getDetail()).contains("\"child\":\"audit_logs\"");
        assertThat(run.getDetail()).contains("\"orphans\":1");

        Integer persisted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_runs WHERE run_id = 'run-it-1'", Integer.class);
        assertThat(persisted).isEqualTo(1);
        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM reconciliation_runs WHERE run_id = 'run-it-1'", String.class);
        assertThat(persistedStatus).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("admin mapper returns performer references in bounded pages")
    void mapperReturnsAuditPerformerCandidates() {
        assertThat(auditOrphanMapper.auditPerformerIds(0, 2))
                .extracting(AuditReferenceCount::getPerformerId)
                .containsExactly("ghost-user", "u-001");
    }

    @Test
    @DisplayName("checkpoint queries isolate incremental watermarks")
    void mapperSelectsMatchingCheckpointWatermark() {
        LocalDateTime firstWatermark = LocalDateTime.of(2026, 8, 29, 0, 0);
        LocalDateTime secondWatermark = LocalDateTime.of(2026, 8, 30, 0, 0);
        jdbcTemplate.update("""
                INSERT INTO reconciliation_runs
                    (run_id, started_at, owner, scan_mode, scan_created_since,
                     fence_token, status, divergence_count, orphan_count, detail)
                VALUES (?, ?, 'ALL', 'INCREMENTAL', ?, 1, 'PARTIAL', 0, 0, ?),
                       (?, ?, 'ALL', 'INCREMENTAL', ?, 1, 'PARTIAL', 0, 0, ?),
                       (?, ?, 'ALL', 'INCREMENTAL', ?, 1, 'COMPLETED', 0, 0, ?)
                """,
                "partial-first", firstWatermark.plusDays(1), firstWatermark, checkpointDetail(firstWatermark),
                "partial-second", secondWatermark.plusDays(1), secondWatermark, checkpointDetail(secondWatermark),
                "completed-second", secondWatermark.plusDays(2), secondWatermark, checkpointDetail(secondWatermark));

        assertThat(runMapper.findLatestPartial("INCREMENTAL", firstWatermark)
                .getRunId()).isEqualTo("partial-first");
        assertThat(runMapper.findLatestPartial("INCREMENTAL", secondWatermark)
                .getRunId()).isEqualTo("partial-second");
        assertThat(runMapper.findLatestCompleted("INCREMENTAL", firstWatermark))
                .isNull();
        assertThat(runMapper.findLatestCompleted("INCREMENTAL", secondWatermark)
                .getRunId()).isEqualTo("completed-second");
    }

    @Test
    @DisplayName("checkpoint migration backfills legacy metadata and ignores invalid values")
    void migrationBackfillsLegacyCheckpointMetadata() {
        assertThat(runMapper.findLatestPartial("INCREMENTAL", LEGACY_WATERMARK)
                .getRunId()).isEqualTo("legacy-partial");
        assertThat(runMapper.findLatestCompleted("INCREMENTAL", LEGACY_WATERMARK)
                .getRunId()).isEqualTo("legacy-completed");
        assertThat(runMapper.findLatestCompleted("FULL", null)
                .getRunId()).isEqualTo("legacy-full-completed");
        assertThat(runMapper.findLatestPartial("INCREMENTAL", LEGACY_WATERMARK.plusDays(10)))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT scan_mode FROM reconciliation_runs WHERE run_id = 'legacy-unknown-mode'",
                String.class)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT scan_created_since FROM reconciliation_runs "
                        + "WHERE run_id = 'legacy-invalid-watermark'",
                LocalDateTime.class)).isNull();
    }

    private static String checkpointDetail(LocalDateTime createdSince) {
        return "{\"mode\":\"INCREMENTAL\",\"continuation\":{"
                + "\"createdSince\":\"" + createdSince + "\","
                + "\"submission\":{\"cursor\":\"user-1\",\"missing\":0,\"complete\":false},"
                + "\"notification\":{\"cursor\":\"\",\"missing\":0,\"complete\":true},"
                + "\"audit\":{\"offset\":0,\"missing\":0,\"complete\":true}}}";
    }

    private static String fullCheckpointDetail() {
        return "{\"mode\":\"FULL\",\"continuation\":{"
                + "\"createdSince\":null,"
                + "\"submission\":{\"cursor\":\"\",\"missing\":0,\"complete\":true},"
                + "\"notification\":{\"cursor\":\"\",\"missing\":0,\"complete\":true},"
                + "\"audit\":{\"offset\":0,\"missing\":0,\"complete\":true}}}";
    }

    private static String unknownCheckpointDetail() {
        return "{\"mode\":\"UNKNOWN\",\"continuation\":{"
                + "\"createdSince\":null,"
                + "\"submission\":{\"cursor\":\"user-1\",\"missing\":0,\"complete\":false},"
                + "\"notification\":{\"cursor\":\"\",\"missing\":0,\"complete\":true},"
                + "\"audit\":{\"offset\":0,\"missing\":0,\"complete\":true}}}";
    }

    private static String invalidCheckpointDetail() {
        return "{\"mode\":\"INCREMENTAL\",\"continuation\":{"
                + "\"createdSince\":\"not-a-timestamp\","
                + "\"submission\":{\"cursor\":\"user-1\",\"missing\":0,\"complete\":false},"
                + "\"notification\":{\"cursor\":\"\",\"missing\":0,\"complete\":true},"
                + "\"audit\":{\"offset\":0,\"missing\":0,\"complete\":true}}}";
    }

    private static Path findRepositoryRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve(
                    "init-db/migrations/admin/V20260913140000__Add_Reconciliation_Checkpoint_Fields.sql"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
