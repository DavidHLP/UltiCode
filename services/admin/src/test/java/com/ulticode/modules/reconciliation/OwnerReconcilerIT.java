package com.ulticode.modules.reconciliation;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
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
                new FencedJobLeaseService(fencedJobLeaseMapper, Clock.systemUTC()));
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
}
