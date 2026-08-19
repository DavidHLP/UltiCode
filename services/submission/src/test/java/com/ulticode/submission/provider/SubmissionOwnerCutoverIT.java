package com.ulticode.submission.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.submission.api.dto.PerformanceStats;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.dto.SubmissionFactsSnapshot;
import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.config.FeatureFlagsProperties;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.reaper.JudgingLeaseReaper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxMapper;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxWriter;
import com.ulticode.modules.submission.port.DefaultSubmissionFencePort;
import com.ulticode.modules.submission.port.DefaultSubmissionWritePort;
import com.ulticode.modules.submission.projection.DefaultSubmissionProjection;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.result.SubmissionResultOutboxMapper;
import com.ulticode.modules.submission.result.SubmissionResultOutboxWriter;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import com.ulticode.submission.dubbo.provider.SubmissionFenceProvider;
import com.ulticode.submission.dubbo.provider.SubmissionWriteProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CONTRACT-007: direct Submission-owner providers against a real MySQL container.
 *
 * <p>Verifies that the {@code backend-submission} Dubbo providers delegate
 * directly to the in-process Submission-schema writer/fence: a submit lands
 * in the {@code submission} schema tables, and the owner fence
 * ({@link DefaultSubmissionFencePort}) acquires/renews the generation lease
 * via the copied {@link SubmissionMapper} CAS statements.
 */
@Testcontainers
@DisplayName("CONTRACT-007: direct Submission owner providers")
class SubmissionOwnerCutoverIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("submission")
            .withUsername("submission_rw")
            .withPassword("submission-pw");

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private SubmissionMapper submissionMapper;
    private JudgeOutboxMapper judgeOutboxMapper;
    private SubmissionResultOutboxMapper resultOutboxMapper;
    private SubmissionCreatedOutboxMapper createdOutboxMapper;

    @BeforeAll
    static void createSchema() throws Exception {
        DataSource dataSource = new HikariDataSource() {{
            setJdbcUrl(mysql.getJdbcUrl());
            setUsername(mysql.getUsername());
            setPassword(mysql.getPassword());
            setMaximumPoolSize(2);
        }};

        try (var c = dataSource.getConnection();
             var st = c.createStatement()) {
            st.execute("""
                CREATE TABLE submissions (
                    id VARCHAR(64) PRIMARY KEY,
                    problem_id BIGINT NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    language VARCHAR(32) NOT NULL,
                    code LONGTEXT NOT NULL,
                    status VARCHAR(64) NOT NULL,
                    runtime INT NULL,
                    memory DOUBLE NULL,
                    notes VARCHAR(500) NULL,
                    retry_count INT NULL DEFAULT 0,
                    generation BIGINT NOT NULL DEFAULT 1,
                    current_attempt_id VARCHAR(64) NULL,
                    judging_lease_expires_at DATETIME NULL,
                    created_at DATETIME NOT NULL,
                    runtime_percentile DOUBLE NULL,
                    memory_percentile DOUBLE NULL,
                    test_details JSON NULL,
                    runtimeDistBinsMs JSON NULL,
                    memoryDistBinsMb JSON NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
            st.execute("""
                CREATE TABLE judge_outbox (
                    id VARCHAR(64) PRIMARY KEY,
                    submission_id VARCHAR(64) NOT NULL,
                    generation BIGINT NOT NULL,
                    payload JSON NULL,
                    is_shadow TINYINT NOT NULL DEFAULT 0,
                    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    next_retry_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    sent_at DATETIME NULL,
                    last_error VARCHAR(1000) NULL,
                    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
            st.execute("""
                CREATE TABLE submission_result_outbox (
                    id VARCHAR(64) PRIMARY KEY,
                    submission_id VARCHAR(64) NOT NULL,
                    generation BIGINT NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    problem_id VARCHAR(64) NOT NULL,
                    verdict VARCHAR(64) NOT NULL,
                    runtime_ms INT NOT NULL,
                    memory_mb DOUBLE NOT NULL,
                    contest_id VARCHAR(64) NULL,
                    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    last_error VARCHAR(1000) NULL,
                    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    claimed_at DATETIME NULL,
                    claim_owner VARCHAR(128) NULL,
                    delivered_at DATETIME NULL,
                    next_retry_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
            st.execute("""
                CREATE TABLE submission_created_outbox (
                    id VARCHAR(64) PRIMARY KEY,
                    submission_id VARCHAR(64) NOT NULL,
                    generation BIGINT NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    problem_id VARCHAR(64) NOT NULL,
                    contest_id VARCHAR(64) NOT NULL,
                    virtual_session_id VARCHAR(64) NULL,
                    language VARCHAR(32) NOT NULL,
                    occurred_at DATETIME(3) NOT NULL,
                    state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    last_error VARCHAR(1000) NULL,
                    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    claimed_at DATETIME NULL,
                    claim_owner VARCHAR(128) NULL,
                    delivered_at DATETIME NULL,
                    next_retry_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    UNIQUE KEY uniq_created_sub_gen (submission_id, generation)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setCacheEnabled(false);
        configuration.addMapper(SubmissionMapper.class);
        configuration.addMapper(JudgeOutboxMapper.class);
        configuration.addMapper(SubmissionResultOutboxMapper.class);
        configuration.addMapper(SubmissionCreatedOutboxMapper.class);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @BeforeEach
    void setUp() throws Exception {
        session = sqlSessionFactory.openSession(false);
        submissionMapper = session.getMapper(SubmissionMapper.class);
        judgeOutboxMapper = session.getMapper(JudgeOutboxMapper.class);
        resultOutboxMapper = session.getMapper(SubmissionResultOutboxMapper.class);
        createdOutboxMapper = session.getMapper(SubmissionCreatedOutboxMapper.class);

        session.getConnection().createStatement().execute(
                "DELETE FROM submission_created_outbox");
        session.getConnection().createStatement().execute(
                "DELETE FROM submission_result_outbox");
        session.getConnection().createStatement().execute("DELETE FROM judge_outbox");
        session.getConnection().createStatement().execute("DELETE FROM submissions");
        session.commit();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.rollback();
            session.close();
        }
    }

    private DefaultSubmissionWritePort newLocalWriter() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        FeatureFlagsProperties flags = new FeatureFlagsProperties();
        flags.setUseJudgeOutbox(true);
        flags.getJudgeQueue().setUsePort(true);
        UuidGenerator uuid = () -> UUID.randomUUID().toString();
        SubmissionProjection projection = new DefaultSubmissionProjection(submissionMapper, null, null, new com.fasterxml.jackson.databind.ObjectMapper());
        SubmissionPerformanceStats stats = mock(SubmissionPerformanceStats.class);
        when(stats.compute(any(), anyInt(), any())).thenReturn(PerformanceStats.EMPTY);
        return new DefaultSubmissionWritePort(
                submissionMapper,
                objectMapper,
                projection,
                stats,
                mockContest(),
                judgeOutboxMapper,
                flags,
                new SimpleMeterRegistry(),
                new SubmissionResultOutboxWriter(resultOutboxMapper, uuid),
                new SubmissionCreatedOutboxWriter(createdOutboxMapper, uuid),
                mock(ApplicationEventPublisher.class),
                Clock.systemUTC(),
                uuid);
    }

    private static SubmissionFactsSnapshot facts() {
        return new SubmissionFactsSnapshot(
                "user-1", true,
                new SubmissionFactsSnapshot.ProblemFacts(
                        101L, "Two Sum", "two-sum", 2, 256, null),
                1L, SubmissionFactsSnapshot.CURRENT_SCHEMA_VERSION);
    }

    private static <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }
        };
    }

    private static ContestSubmissionPort mockContest() {
        ContestSubmissionPort p = mock(ContestSubmissionPort.class);
        when(p.findContestId(any())).thenReturn(null);
        return p;
    }

    @Test
    @DisplayName("write provider delegates to the Submission-schema writer")
    void writeProviderWritesSubmissionSchema() throws Exception {
        DefaultSubmissionWritePort localWriter = newLocalWriter();
        SubmissionWriteProvider provider = new SubmissionWriteProvider(localWriter);

        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("python");
        dto.setCode("print(1)");
        SubmissionVO vo = provider.submit("user-1", dto, facts());
        session.commit();

        assertThat(vo.getId()).isNotBlank();
        Submission row = submissionMapper.selectById(vo.getId());
        assertThat(row).isNotNull();
        assertThat(row.getStatus()).isEqualTo("Pending");
        assertThat(judgeOutboxMapper.countBySubmission(vo.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("fence provider acquires and renews the generation lease")
    void fenceProviderAcquiresAndRenewsLease() throws Exception {
        DefaultSubmissionWritePort localWriter = newLocalWriter();
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("java");
        dto.setCode("class A{}");
        String submissionId = localWriter.submit("user-1", dto, facts()).getId();
        session.commit();

        DefaultSubmissionFencePort localFence = new DefaultSubmissionFencePort(submissionMapper);
        SubmissionFenceProvider provider = new SubmissionFenceProvider(localFence);

        String attemptId = UUID.randomUUID().toString();
        boolean acquired = provider.acquireLease(submissionId, attemptId, 1L, 60);
        boolean renewed = provider.renewLease(submissionId, attemptId, 60);
        session.commit();

        assertThat(acquired).isTrue();
        assertThat(renewed).isTrue();
        assertThat(provider.currentGeneration(submissionId)).isEqualTo(Optional.of(1L));
        Submission row = submissionMapper.selectById(submissionId);
        assertThat(row.getCurrentAttemptId()).isEqualTo(attemptId);
    }

    @Test
    @DisplayName("fence CAS rejects a stale generation")
    void fenceProviderRejectsStaleGeneration() throws Exception {
        DefaultSubmissionWritePort localWriter = newLocalWriter();
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("cpp");
        dto.setCode("#include <cstdio>");
        String submissionId = localWriter.submit("user-1", dto, facts()).getId();
        session.commit();

        DefaultSubmissionFencePort localFence = new DefaultSubmissionFencePort(submissionMapper);
        SubmissionFenceProvider provider = new SubmissionFenceProvider(localFence);

        boolean acquired = provider.acquireLease(submissionId, "stale-attempt", 99L, 60);
        session.commit();

        assertThat(acquired).isFalse();
        Submission row = submissionMapper.selectById(submissionId);
        assertThat(row.getStatus()).isEqualTo("Pending");
    }

    @Test
    @DisplayName("owner reaper resets an expired lease and records a real outbox row")
    void ownerReaperRecoversExpiredLeaseInSubmissionSchema() throws Exception {
        DefaultSubmissionWritePort localWriter = newLocalWriter();
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("cpp");
        dto.setCode("#include <cstdio>");
        String submissionId = localWriter.submit("user-1", dto, facts()).getId();
        session.commit();

        try (var statement = session.getConnection().createStatement()) {
            statement.execute("UPDATE submissions SET status = 'Judging', "
                    + "current_attempt_id = 'attempt-1', "
                    + "judging_lease_expires_at = DATE_ADD(NOW(), INTERVAL -5 MINUTE) "
                    + "WHERE id = '" + submissionId + "'");
        }
        session.commit();

        FeatureFlagsProperties flags = new FeatureFlagsProperties();
        flags.setUseGenerationFence(true);
        flags.setUseJudgeOutbox(true);
        flags.getJudgeQueue().setUsePort(true);
        ObjectProvider<PlatformTransactionManager> noTransactionManager = providerOf(null);
        JudgingLeaseReaper reaper = new JudgingLeaseReaper(
                submissionMapper,
                judgeOutboxMapper,
                flags,
                () -> "reaper-outbox-" + UUID.randomUUID(),
                new SimpleMeterRegistry(),
                noTransactionManager);

        assertThat(reaper.recoverExpiredLeases()).isEqualTo(1);
        session.commit();

        Submission recovered = submissionMapper.selectById(submissionId);
        assertThat(recovered.getStatus()).isEqualTo("Pending");
        assertThat(recovered.getGeneration()).isEqualTo(2L);
        assertThat(recovered.getCurrentAttemptId()).isNull();
        assertThat(recovered.getJudgingLeaseExpiresAt()).isNull();

        List<JudgeOutboxRecord> outboxes = judgeOutboxMapper.selectList(
                new LambdaQueryWrapper<JudgeOutboxRecord>()
                        .eq(JudgeOutboxRecord::getSubmissionId, submissionId));
        assertThat(outboxes).hasSize(2);
        JudgeOutboxRecord recovery = outboxes.stream()
                .filter(row -> row.getGeneration().equals(2L))
                .findFirst()
                .orElseThrow();
        assertThat(recovery.getIsShadow()).isFalse();
    }

    @Test
    @DisplayName("disposable cutover failure rollback cleans up target rows and restores pre-cutover state")
    void cutoverFailureRollbackCleansUpTargetState() throws Exception {
        DefaultSubmissionWritePort localWriter = newLocalWriter();
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("java");
        dto.setCode("class Solution {}");

        SubmissionVO vo = localWriter.submit("user-1", dto, facts());
        session.commit();

        assertThat(submissionMapper.selectById(vo.getId())).isNotNull();

        // Simulate cutover failure cleanup / rollback on disposable schema:
        try (var statement = session.getConnection().createStatement()) {
            statement.execute("DELETE FROM judge_outbox WHERE submission_id = '" + vo.getId() + "'");
            statement.execute("DELETE FROM submissions WHERE id = '" + vo.getId() + "'");
        }
        session.commit();

        assertThat(submissionMapper.selectList(null)).isEmpty();
        assertThat(judgeOutboxMapper.selectList(null)).isEmpty();
    }
}
