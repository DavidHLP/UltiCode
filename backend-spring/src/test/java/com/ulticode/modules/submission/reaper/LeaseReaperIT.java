package com.ulticode.modules.submission.reaper;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the lease reaper recovery SQL (ADR-003 M3b, F2). Verifies
 * on a real MySQL 8.0 that {@code selectExpiredJudgingForUpdate} finds only
 * lapsed JUDGING rows and that {@code bumpGenerationAndReset} resets them to
 * Pending at a new generation.
 */
@Testcontainers
@DisplayName("Lease reaper recovery (MySQL)")
class LeaseReaperIT {

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

    private void insertJudging(String id, long generation, String attemptId, boolean expired) {
        // Insert the row, then set the lease expiry via raw JDBC using the DB
        // clock (NOW()) so the comparison in selectExpiredJudgingForUpdate is
        // not skewed by JVM-vs-container timezone differences.
        Submission s = new Submission();
        s.setId(id);
        s.setProblemId(1L);
        s.setUserId("user-1");
        s.setLanguage("java");
        s.setCode("code");
        s.setStatus("Judging");
        s.setRuntime(0);
        s.setMemory(0.0);
        s.setCreatedAt(LocalDateTime.now());
        s.setTestDetails(new java.util.ArrayList<>());
        s.setGeneration(generation);
        s.setCurrentAttemptId(attemptId);
        // Temporary placeholder; overwritten via JDBC below.
        s.setJudgingLeaseExpiresAt(LocalDateTime.now());
        submissionMapper.insert(s);
        try (var stmt = session.getConnection().createStatement()) {
            // expired=true -> 5 min in the past (reaper should find it);
            // expired=false -> 5 min in the future (reaper should skip it).
            String delta = expired ? "MINUTE" : "MINUTE";
            String direction = expired ? "- 5" : "+ 5";
            stmt.execute("UPDATE submissions SET judging_lease_expires_at = DATE_ADD(NOW(), INTERVAL "
                    + direction + " " + delta + ") WHERE id = '" + id + "'");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("selectExpiredJudgingForUpdate returns only lapsed JUDGING rows")
    void returnsOnlyLapsedJudging() {
        // Expired (lease well in the past — generous margin so JVM-vs-container
        // clock skew cannot flip the comparison).
        insertJudging("sub-expired", 1L, "att-1", true);
        // Active (lease well in the future).
        insertJudging("sub-active", 1L, "att-2", false);
        // Pending (no lease at all).
        Submission pending = new Submission();
        pending.setId("sub-pending");
        pending.setProblemId(1L);
        pending.setUserId("user-1");
        pending.setLanguage("java");
        pending.setCode("code");
        pending.setStatus("Pending");
        pending.setRuntime(0);
        pending.setMemory(0.0);
        pending.setCreatedAt(LocalDateTime.now());
        pending.setTestDetails(new java.util.ArrayList<>());
        pending.setGeneration(1L);
        submissionMapper.insert(pending);
        session.commit();

        List<Submission> expired = submissionMapper.selectExpiredJudgingForUpdate(20);
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getId()).isEqualTo("sub-expired");
    }

    @Test
    @DisplayName("reaper bump recovers an expired JUDGING row to Pending at generation+1")
    void reaperBumpRecoversRow() {
        insertJudging("sub-recover", 5L, "att-dead", true);
        session.commit();

        List<Submission> expired = submissionMapper.selectExpiredJudgingForUpdate(20);
        assertThat(expired).hasSize(1);

        Submission dead = expired.get(0);
        long observedGen = dead.getGeneration();
        int bumped = submissionMapper.bumpGenerationAndReset(dead.getId(), observedGen, observedGen + 1);
        assertThat(bumped).isEqualTo(1);
        session.commit();

        Submission recovered = submissionMapper.selectById("sub-recover");
        assertThat(recovered.getStatus()).isEqualTo("Pending");
        assertThat(recovered.getGeneration()).isEqualTo(6L);
        assertThat(recovered.getCurrentAttemptId()).isNull();
        assertThat(recovered.getJudgingLeaseExpiresAt()).isNull();
    }

    @Test
    @DisplayName("a recovered row is re-acquireable by a new worker at the bumped generation")
    void recoveredRowCanBeReacquired() {
        insertJudging("sub-reacquire", 1L, "att-old", true);
        session.commit();

        // Reaper recovers it.
        List<Submission> expired = submissionMapper.selectExpiredJudgingForUpdate(20);
        Submission dead = expired.get(0);
        submissionMapper.bumpGenerationAndReset(dead.getId(), dead.getGeneration(), dead.getGeneration() + 1);
        session.commit();

        // New worker acquires at the new generation.
        int acquired = submissionMapper.acquireLease("sub-reacquire", "att-new", 2L, 60L);
        assertThat(acquired).isEqualTo(1);
        session.commit();

        Submission reclaimed = submissionMapper.selectById("sub-reacquire");
        assertThat(reclaimed.getStatus()).isEqualTo("Judging");
        assertThat(reclaimed.getCurrentAttemptId()).isEqualTo("att-new");
        assertThat(reclaimed.getGeneration()).isEqualTo(2L);
    }

    @Test
    @DisplayName("selectExpiredJudgingForUpdate respects the batch size limit")
    void respectsBatchSize() {
        for (int i = 0; i < 5; i++) {
            insertJudging("sub-batch-" + i, 1L, "att-" + i, true);
        }
        session.commit();

        List<Submission> expired = submissionMapper.selectExpiredJudgingForUpdate(2);
        assertThat(expired).hasSize(2);
    }
}
