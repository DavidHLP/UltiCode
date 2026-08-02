package com.ulticode.modules.queue.outbox.mapper;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.submission.entity.Submission;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for {@link JudgeOutboxMapper} against a real MySQL 8.0
 * (ADR-003 M3a). Verifies the {@code FOR UPDATE SKIP LOCKED} claim, the
 * {@code uniq_dispatch} unique constraint, and the markSent transition.
 */
@Testcontainers
@DisplayName("JudgeOutbox - round-trip (MySQL)")
class JudgeOutboxRoundTripIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_test")
            .withUsername("test")
            .withPassword("test");

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession session;
    private JudgeOutboxMapper outboxMapper;

    @BeforeAll
    static void setUpSchema() throws Exception {
        DataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            // judge_outbox table matching V20260613100000__Create_Judge_Outbox.sql
            stmt.execute("""
                CREATE TABLE judge_outbox (
                  id            varchar(40)  NOT NULL,
                  submission_id varchar(40)  NOT NULL,
                  generation    bigint       NOT NULL,
                  payload       json         NOT NULL,
                  state         varchar(16)  NOT NULL DEFAULT 'PENDING',
                  is_shadow     tinyint(1)   NOT NULL DEFAULT 1,
                  attempts      int          NOT NULL DEFAULT 0,
                  last_error    text         DEFAULT NULL,
                  created_at    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  sent_at       datetime(3)  DEFAULT NULL,
                  next_retry_at datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (id),
                  UNIQUE KEY uniq_dispatch (submission_id, generation),
                  KEY idx_state_retry (state, next_retry_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setCacheEnabled(false);
        configuration.addMapper(JudgeOutboxMapper.class);
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
        outboxMapper = session.getMapper(JudgeOutboxMapper.class);
        session.getConnection().createStatement().execute("DELETE FROM judge_outbox");
        session.commit();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.rollback();
            session.close();
        }
    }

    @AfterAll
    static void tearDownAll() {
        // Testcontainers handles container teardown.
    }

    private JudgeOutboxRecord newRecord(String submissionId, long generation) {
        Submission s = new Submission();
        s.setId(submissionId);
        s.setUserId("user-1");
        s.setLanguage("java");
        s.setCode("x");
        JudgeOutboxRecord r = JudgeOutboxRecord.of(s, "1", generation, true,
                new com.ulticode.common.uuid.FixedUuidGenerator());
        return r;
    }

    @Test
    @DisplayName("claim() returns PENDING rows whose retry time has arrived")
    void claimReturnsPendingRows() throws java.sql.SQLException {
        outboxMapper.insert(newRecord("sub-1", 1L));
        outboxMapper.insert(newRecord("sub-2", 1L));
        session.commit();
        // Stamp next_retry_at to the DB clock (NOW()) via raw JDBC so the
        // claim comparison (next_retry_at <= NOW()) is not skewed by JVM-vs-
        // container timezone differences.
        try (var stmt = session.getConnection().createStatement()) {
            stmt.execute("UPDATE judge_outbox SET next_retry_at = NOW() WHERE submission_id IN ('sub-1','sub-2')");
        }
        session.commit();

        List<JudgeOutboxRecord> claimed = outboxMapper.claim(10);
        assertThat(claimed).hasSize(2);
        assertThat(claimed).allSatisfy(r -> {
            assertThat(r.getState()).isEqualTo("PENDING");
            assertThat(r.getIsShadow()).isTrue();
        });
    }

    @Test
    @DisplayName("markSent() transitions a row to SENT")
    void markSentTransitionsState() {
        JudgeOutboxRecord r = newRecord("sub-3", 1L);
        outboxMapper.insert(r);
        session.commit();

        int affected = outboxMapper.markSent(r.getId());
        assertThat(affected).isEqualTo(1);
        session.commit();

        JudgeOutboxRecord reloaded = outboxMapper.selectById(r.getId());
        assertThat(reloaded.getState()).isEqualTo("SENT");
        assertThat(reloaded.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("uniq_dispatch prevents duplicate (submissionId, generation) rows")
    void uniqueKeyPreventsDuplicate() {
        outboxMapper.insert(newRecord("sub-4", 1L));
        session.commit();

        // Inserting the same (submissionId, generation) must violate the unique key.
        assertThatThrownBy(() -> {
            outboxMapper.insert(newRecord("sub-4", 1L));
            session.commit();
        }).isInstanceOf(Exception.class);

        // Different generation for the same submission is fine.
        outboxMapper.insert(newRecord("sub-4", 2L));
        session.commit();
        assertThat(outboxMapper.countBySubmission("sub-4")).isEqualTo(2L);
    }

    @Test
    @DisplayName("markRetry() increments attempts and schedules a new retry time")
    void markRetryIncrementsAttempts() {
        JudgeOutboxRecord r = newRecord("sub-5", 1L);
        outboxMapper.insert(r);
        session.commit();

        int affected = outboxMapper.markRetry(r.getId(), LocalDateTime.now().plusSeconds(30), "boom");
        assertThat(affected).isEqualTo(1);
        session.commit();

        JudgeOutboxRecord reloaded = outboxMapper.selectById(r.getId());
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getLastError()).isEqualTo("boom");
    }

    @Test
    @DisplayName("markDead() parks a row in DEAD without deleting it")
    void markDeadParksRow() {
        JudgeOutboxRecord r = newRecord("sub-6", 1L);
        outboxMapper.insert(r);
        session.commit();

        outboxMapper.markDead(r.getId(), "exhausted");
        session.commit();

        JudgeOutboxRecord reloaded = outboxMapper.selectById(r.getId());
        assertThat(reloaded.getState()).isEqualTo("DEAD");
        assertThat(reloaded.getLastError()).isEqualTo("exhausted");
        // Row must persist (unique key must stay so future re-enqueue is deduped).
        assertThat(outboxMapper.countByState("DEAD")).isEqualTo(1L);
    }

    @Test
    @DisplayName("payload is round-tripped through the json column via JacksonTypeHandler")
    void payloadRoundTrips() {
        JudgeOutboxRecord r = newRecord("sub-7", 1L);
        outboxMapper.insert(r);
        session.commit();

        JudgeOutboxRecord reloaded = outboxMapper.selectById(r.getId());
        assertThat(reloaded.getPayload()).isNotNull();
        assertThat(reloaded.getPayload().get("submissionId")).isEqualTo("sub-7");
        assertThat(reloaded.getPayload().get("generation")).isEqualTo(1);
        assertThat(reloaded.getPayload().get("code")).isEqualTo("x");
    }
}
