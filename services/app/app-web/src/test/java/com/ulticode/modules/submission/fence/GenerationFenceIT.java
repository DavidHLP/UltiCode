package com.ulticode.modules.submission.fence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the generation fence CAS (ADR-003 M3b, F2). Verifies on a
 * real MySQL 8.0 that a stale worker holding an old generation cannot write a
 * verdict after {@code bumpGenerationAndReset} has moved the submission forward.
 */
@Testcontainers
@DisplayName("Generation fence CAS (MySQL)")
class GenerationFenceIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_test")
            .withUsername("test")
            .withPassword("test");

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private SubmissionMapper submissionMapper;

    @BeforeAll
    static void setUpSchema() throws Exception {
        DataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            // submissions table with the ADR-003 M3b columns.
            stmt.execute("""
                CREATE TABLE submissions (
                    id varchar(40) NOT NULL,
                    problem_id bigint NOT NULL,
                    user_id varchar(40) NOT NULL,
                    language varchar(50) NOT NULL,
                    code text NOT NULL,
                    status varchar(40) NOT NULL,
                    runtime int NOT NULL DEFAULT '0',
                    memory double NOT NULL DEFAULT '0',
                    notes text,
                    retry_count int NOT NULL DEFAULT '0',
                    created_at datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    runtime_percentile double DEFAULT NULL,
                    memory_percentile double DEFAULT NULL,
                    test_details json DEFAULT NULL,
                    memoryDistBinsMb json DEFAULT NULL,
                    runtimeDistBinsMs json DEFAULT NULL,
                    generation bigint NOT NULL DEFAULT 1,
                    current_attempt_id varchar(40) DEFAULT NULL,
                    judging_lease_expires_at datetime(3) DEFAULT NULL,
                    PRIMARY KEY (id),
                    KEY idx_lease_expiry (status, judging_lease_expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setCacheEnabled(false);
        configuration.addMapper(SubmissionMapper.class);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(interceptor);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @BeforeEach
    void setUp() throws java.sql.SQLException {
        session = sqlSessionFactory.openSession(false);
        submissionMapper = session.getMapper(SubmissionMapper.class);
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

    private Submission newPendingSubmission(String id) {
        Submission s = new Submission();
        s.setId(id);
        s.setProblemId(1L);
        s.setUserId("user-1");
        s.setLanguage("java");
        s.setCode("code");
        s.setStatus("Pending");
        s.setRuntime(0);
        s.setMemory(0.0);
        s.setCreatedAt(LocalDateTime.now());
        s.setTestDetails(new java.util.ArrayList<>());
        s.setGeneration(1L);
        return s;
    }

    @Test
    @DisplayName("acquireLease CAS: Pending -> Judging, records attempt + lease")
    void acquireLeaseTransitionsPendingToJudging() {
        submissionMapper.insert(newPendingSubmission("sub-fence-1"));
        session.commit();

        int affected = submissionMapper.acquireLease("sub-fence-1", "attempt-A", 1L, 60L);
        assertThat(affected).isEqualTo(1);
        session.commit();

        Submission reloaded = submissionMapper.selectById("sub-fence-1");
        assertThat(reloaded.getStatus()).isEqualTo("Judging");
        assertThat(reloaded.getCurrentAttemptId()).isEqualTo("attempt-A");
        assertThat(reloaded.getJudgingLeaseExpiresAt()).isNotNull();
        assertThat(reloaded.getGeneration()).isEqualTo(1L);
    }

    @Test
    @DisplayName("acquireLease fails when generation does not match (stale poll)")
    void acquireLeaseFailsOnGenerationMismatch() {
        submissionMapper.insert(newPendingSubmission("sub-fence-2"));
        session.commit();

        // Worker polled with generation=1 but the row is already at generation=2
        // (simulated by bumping first).
        submissionMapper.bumpGenerationAndReset("sub-fence-2", 1L, 2L);
        session.commit();

        int affected = submissionMapper.acquireLease("sub-fence-2", "attempt-B", 1L, 60L);
        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("writeVerdictFenced is rejected when generation was bumped (stale worker)")
    void writeVerdictFencedRejectsStaleGeneration() {
        // Worker acquired lease at gen=1.
        submissionMapper.insert(newPendingSubmission("sub-fence-3"));
        submissionMapper.acquireLease("sub-fence-3", "attempt-C", 1L, 60L);
        session.commit();

        // Meanwhile a rejudge/reaper bumped generation to 2 (and reset attempt).
        submissionMapper.bumpGenerationAndReset("sub-fence-3", 1L, 2L);
        session.commit();

        // Stale worker tries to write its verdict with the old gen + attempt.
        // The attempt no longer matches (bump cleared it), so the fence rejects.
        int affected = submissionMapper.writeVerdictFenced(
                "sub-fence-3", 1L, "attempt-C", "Accepted", 42, 16.0, null);
        assertThat(affected).isEqualTo(0);
        session.commit();

        // Row is still Pending at generation 2, verdict NOT applied.
        Submission reloaded = submissionMapper.selectById("sub-fence-3");
        assertThat(reloaded.getStatus()).isEqualTo("Pending");
        assertThat(reloaded.getGeneration()).isEqualTo(2L);
        assertThat(reloaded.getRuntime()).isZero();
    }

    @Test
    @DisplayName("writeVerdictFenced lands when generation + attempt match")
    void writeVerdictFencedLandsWhenFenceMatches() {
        submissionMapper.insert(newPendingSubmission("sub-fence-4"));
        submissionMapper.acquireLease("sub-fence-4", "attempt-D", 1L, 60L);
        session.commit();

        int affected = submissionMapper.writeVerdictFenced(
                "sub-fence-4", 1L, "attempt-D", "Accepted", 99, 17.5, null);
        assertThat(affected).isEqualTo(1);
        session.commit();

        Submission reloaded = submissionMapper.selectById("sub-fence-4");
        assertThat(reloaded.getStatus()).isEqualTo("Accepted");
        assertThat(reloaded.getRuntime()).isEqualTo(99);
        assertThat(reloaded.getMemory()).isEqualTo(17.5);
        // Lease fields cleared on verdict write.
        assertThat(reloaded.getCurrentAttemptId()).isNull();
        assertThat(reloaded.getJudgingLeaseExpiresAt()).isNull();
    }

    @Test
    @DisplayName("renewLease extends the lease only for the current attempt holder")
    void renewLeaseOnlyForCurrentAttempt() {
        submissionMapper.insert(newPendingSubmission("sub-fence-5"));
        submissionMapper.acquireLease("sub-fence-5", "attempt-E", 1L, 60L);
        session.commit();

        // Same attempt -> renewed.
        int renewed = submissionMapper.renewLease("sub-fence-5", "attempt-E", 60L);
        assertThat(renewed).isEqualTo(1);

        // Wrong attempt -> rejected.
        int stale = submissionMapper.renewLease("sub-fence-5", "attempt-OTHER", 60L);
        assertThat(stale).isEqualTo(0);
    }

    @Test
    @DisplayName("bumpGenerationAndReset is a CAS on expected generation")
    void bumpGenerationIsCas() {
        submissionMapper.insert(newPendingSubmission("sub-fence-6"));
        session.commit();

        // Correct expected gen -> bump succeeds.
        int bumped = submissionMapper.bumpGenerationAndReset("sub-fence-6", 1L, 2L);
        assertThat(bumped).isEqualTo(1);
        session.commit();

        // Bumping with a stale expected gen -> no-op.
        int staleBump = submissionMapper.bumpGenerationAndReset("sub-fence-6", 1L, 3L);
        assertThat(staleBump).isEqualTo(0);
    }
}
