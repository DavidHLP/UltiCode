package com.ulticode.modules.submission.port;

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
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxMapper;
import com.ulticode.modules.submission.created.SubmissionCreatedOutboxWriter;
import com.ulticode.modules.submission.projection.DefaultSubmissionProjection;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.result.SubmissionResultOutboxMapper;
import com.ulticode.modules.submission.result.SubmissionResultOutboxWriter;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import com.ulticode.domain.submission.enums.SubmissionStatus;
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
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SPLIT-003 slice-2: local storage writer against a real MySQL container.
 *
 * <p>Verifies that {@code backend-submission}'s own writer persists the
 * {@code submission} schema tables ({@code submissions}, {@code judge_outbox},
 * {@code submission_result_outbox}) in one local transaction, and that the
 * verdict fence CAS rejects stale generations. External ports (problem facts,
 * user existence, contest association) are mocked — their Dubbo wiring is
 * covered by the compat contract test.
 */
@Testcontainers
@DisplayName("SPLIT-003 slice-2: backend-submission local storage writer")
class DefaultSubmissionWritePortIT {

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
    private DefaultSubmissionWritePort writer;

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
                    claimed_at DATETIME(3) NULL,
                    claim_owner VARCHAR(128) NULL,
                    delivered_at DATETIME(3) NULL,
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

        writer = newWriter(submissionMapper, judgeOutboxMapper, resultOutboxMapper,
                createdOutboxMapper);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.rollback();
            session.close();
        }
    }

    private DefaultSubmissionWritePort newWriter(SubmissionMapper sm,
                                                 JudgeOutboxMapper jom,
                                                 SubmissionResultOutboxMapper rom,
                                                 SubmissionCreatedOutboxMapper com) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        FeatureFlagsProperties flags = new FeatureFlagsProperties();
        flags.setUseJudgeOutbox(true);
        flags.getJudgeQueue().setUsePort(true);

        UuidGenerator uuid = () -> UUID.randomUUID().toString();
        SubmissionProjection projection = new DefaultSubmissionProjection(submissionMapper, null, null, new com.fasterxml.jackson.databind.ObjectMapper());
        SubmissionPerformanceStats stats = mock(SubmissionPerformanceStats.class);
        when(stats.compute(any(), anyInt(), any())).thenReturn(PerformanceStats.EMPTY);

        return new DefaultSubmissionWritePort(
                sm,
                objectMapper,
                projection,
                stats,
                mockContest(),
                jom,
                flags,
                new SimpleMeterRegistry(),
                new SubmissionResultOutboxWriter(rom, uuid),
                new SubmissionCreatedOutboxWriter(com, uuid),
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

    private static ContestSubmissionPort mockContest() {
        ContestSubmissionPort p = mock(ContestSubmissionPort.class);
        when(p.findContestId(any())).thenReturn(null);
        return p;
    }

    private void acquireLease(String submissionId, String attemptId) throws Exception {
        try (var ps = session.getConnection().prepareStatement(
                "UPDATE submissions SET current_attempt_id = ?, "
                + "judging_lease_expires_at = DATE_ADD(NOW(), INTERVAL 60 SECOND) "
                + "WHERE id = ?")) {
            ps.setString(1, attemptId);
            ps.setString(2, submissionId);
            ps.executeUpdate();
        }
        session.commit();
    }

    private long countResultOutbox(String submissionId) throws Exception {
        try (var ps = session.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM submission_result_outbox WHERE submission_id = ?")) {
            ps.setString(1, submissionId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    @DisplayName("submit persists submission + judge_outbox and returns VO")
    void submitPersistsSubmissionAndOutbox() {
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("python");
        dto.setCode("print(1)");

        SubmissionVO vo = writer.submit("user-1", dto, facts());
        session.commit();

        assertThat(vo.getId()).isNotBlank();
        Submission row = submissionMapper.selectById(vo.getId());
        assertThat(row).isNotNull();
        assertThat(row.getStatus()).isEqualTo("Pending");
        assertThat(row.getProblemId()).isEqualTo(101L);

        long outboxCount = judgeOutboxMapper.countBySubmission(vo.getId());
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    @DisplayName("fenced verdict CAS accepts current generation and writes result outbox")
    void fencedVerdictAcceptsCurrentGeneration() throws Exception {
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("java");
        dto.setCode("class A{}");
        SubmissionVO vo = writer.submit("user-1", dto, facts());

        String attemptId = UUID.randomUUID().toString();
        acquireLease(vo.getId(), attemptId);
        boolean written = writer.updateSubmissionResultFenced(
                vo.getId(), SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 1L, attemptId);
        session.commit();

        assertThat(written).isTrue();
        Submission row = submissionMapper.selectById(vo.getId());
        assertThat(row.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED.wireValue());

        long outboxCount = countResultOutbox(vo.getId());
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    @DisplayName("fenced verdict CAS rejects stale generation and records nothing")
    void fencedVerdictRejectsStaleGeneration() throws Exception {
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("cpp");
        dto.setCode("#include <cstdio>");
        SubmissionVO vo = writer.submit("user-1", dto, facts());

        acquireLease(vo.getId(), "real-attempt");
        boolean written = writer.updateSubmissionResultFenced(
                vo.getId(), SubmissionStatus.ACCEPTED, 12, 1.5, "[]", 99L, "real-attempt");
        session.commit();

        assertThat(written).isFalse();
        Submission row = submissionMapper.selectById(vo.getId());
        assertThat(row.getStatus()).isEqualTo("Pending");

        long outboxCount = countResultOutbox(vo.getId());
        assertThat(outboxCount).isZero();
    }

    @Test
    @DisplayName("generic owner intake rejects contest context")
    void contestSubmissionsAreRejected() {
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("python");
        dto.setCode("print(1)");
        dto.setContestId("contest-1");

        assertThatThrownBy(() -> writer.submit("user-1", dto, facts()))
                .hasMessageContaining("contest command");
    }

    @Test
    @DisplayName("generic owner intake rejects virtual-session-only context")
    void virtualSessionOnlyContextIsRejected() {
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("python");
        dto.setCode("print(1)");
        dto.setVirtualSessionId("session-1");

        assertThatThrownBy(() -> writer.submit("user-1", dto, facts()))
                .hasMessageContaining("contest command");
    }

    @Test
    @DisplayName("explicit contest command writes the durable association outbox")
    void explicitContestCommandWritesAssociationOutbox() {
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(101L);
        dto.setLanguage("java");
        dto.setCode("class Main{}");
        dto.setContestId("contest-1");
        dto.setVirtualSessionId("session-1");

        SubmissionVO vo = writer.submitContest("user-1", dto, facts());
        session.commit();

        assertThat(createdOutboxMapper.selectByMap(java.util.Map.of(
                "submission_id", vo.getId()))).hasSize(1);
        assertThat(createdOutboxMapper.selectByMap(java.util.Map.of(
                "contest_id", "contest-1"))).hasSize(1);
    }
}
