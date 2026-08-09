package com.ulticode.modules.submission.result;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.event.outbox.IntegrationEventPublisher;
import com.ulticode.modules.event.outbox.IntegrationOutboxMapper;
import com.ulticode.modules.event.outbox.IntegrationOutboxRecord;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
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
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@DisplayName("Submission result dispatcher - durable publication (MySQL)")
class SubmissionResultDispatcherIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_submission_result_it")
            .withUsername("root")
            .withPassword("root");

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;
    private static SqlSessionTemplate sqlSessionTemplate;
    private static PlatformTransactionManager transactionManager;
    private SqlSession session;
    private SubmissionResultOutboxMapper resultMapper;
    private IntegrationOutboxMapper integrationOutboxMapper;

    @BeforeAll
    static void setUpSchema() throws Exception {
        dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());

        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE submission_result_outbox (
                  id            varchar(40)  NOT NULL,
                  submission_id varchar(40)  NOT NULL,
                  generation    bigint       NOT NULL DEFAULT 0,
                  user_id       varchar(40)  NOT NULL,
                  problem_id    varchar(120) NOT NULL,
                  verdict       varchar(30)  NOT NULL,
                  runtime_ms    int          NOT NULL DEFAULT 0,
                  memory_mb     double       NOT NULL DEFAULT 0,
                  contest_id    varchar(40)  DEFAULT NULL,
                  state         varchar(16)  NOT NULL DEFAULT 'PENDING',
                  attempts      int          NOT NULL DEFAULT 0,
                  last_error    text         DEFAULT NULL,
                  created_at   datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  delivered_at datetime(3)  DEFAULT NULL,
                  next_retry_at datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (id),
                  UNIQUE KEY uniq_result_sub_gen (submission_id, generation),
                  KEY idx_result_state_retry (state, next_retry_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
            statement.execute("""
                CREATE TABLE integration_outbox (
                  event_id          varchar(40)  NOT NULL,
                  owner             varchar(20)  NOT NULL,
                  aggregate_id      varchar(120) NOT NULL,
                  aggregate_version bigint       NOT NULL DEFAULT 0,
                  causation_id      varchar(40)  DEFAULT NULL,
                  trace_id          varchar(40)  DEFAULT NULL,
                  event_type        varchar(120) NOT NULL,
                  schema_version    int          NOT NULL DEFAULT 1,
                  payload           json         NOT NULL,
                  state             varchar(16)  NOT NULL DEFAULT 'PENDING',
                  attempts          int          NOT NULL DEFAULT 0,
                  last_error        text         DEFAULT NULL,
                  stream_id         varchar(80)  DEFAULT NULL,
                  created_at        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  claimed_at        datetime(3)  DEFAULT NULL,
                  delivered_at      datetime(3)  DEFAULT NULL,
                  next_retry_at     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (event_id),
                  KEY idx_outbox_state_retry (state, next_retry_at),
                  KEY idx_outbox_aggregate (aggregate_id, aggregate_version),
                  KEY idx_outbox_owner_type (owner, event_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
            statement.execute("""
                INSERT INTO submission_result_outbox
                    (id, submission_id, generation, user_id, problem_id, verdict, state)
                VALUES
                    ('legacy-result', 'legacy-submission', 4, 'user-legacy',
                     'problem-legacy', 'Accepted', 'DELIVERED')
                """);
            statement.execute("""
                INSERT INTO submission_result_outbox
                    (id, submission_id, generation, user_id, problem_id, verdict, state)
                VALUES
                    ('legacy-nonterminal', 'legacy-nonterminal-submission', 2, 'user-legacy',
                     'problem-legacy', 'Pending', 'PENDING')
                """);
            statement.execute("""
                INSERT INTO integration_outbox
                    (event_id, owner, aggregate_id, aggregate_version, event_type,
                     schema_version, payload, state, attempts, last_error, next_retry_at)
                VALUES
                    ('legacy-dead-event', 'App', 'legacy-submission', 4, 'SubmissionJudged',
                     1, '{}', 'DEAD', 5, 'old Redis failure', NOW(3))
                """);
            executeMigration(statement, "V20260809110000__Add_Claimed_At_To_Submission_Result_Outbox.sql");
            executeMigration(statement, "V20260809120000__Reconcile_Submission_Result_Events.sql");
            executeMigration(statement, "V20260809130000__Add_Claim_Owner_To_Submission_Result_Outbox.sql");
            executeMigration(statement, "V20260809140000__Add_Claim_Owner_To_Integration_Outbox.sql");

            try (var resultSet = statement.executeQuery("""
                    SELECT event_id, state, attempts, last_error
                    FROM integration_outbox
                    WHERE aggregate_id = 'legacy-submission'
                      AND aggregate_version = 4
                      AND event_type = 'SubmissionJudged'
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("event_id")).isEqualTo("legacy-result");
                assertThat(resultSet.getString("state")).isEqualTo("PENDING");
                assertThat(resultSet.getInt("attempts")).isZero();
                assertThat(resultSet.getString("last_error")).isNull();
                assertThat(resultSet.next()).isFalse();
            }

            try (var resultSet = statement.executeQuery("""
                    SELECT state, last_error
                    FROM submission_result_outbox
                    WHERE id = 'legacy-nonterminal'
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("state")).isEqualTo("DEAD");
                assertThat(resultSet.getString("last_error"))
                        .isEqualTo("Retired non-terminal result row during TASK-028 cutover");
            }
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setCacheEnabled(false);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(SubmissionResultOutboxMapper.class);
        configuration.addMapper(IntegrationOutboxMapper.class);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(interceptor);

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
        sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        transactionManager = new DataSourceTransactionManager(dataSource);
    }
    private static void executeMigration(java.sql.Statement statement, String filename)
            throws Exception {
        Path migrationPath = null;
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("init-db/migrations/" + filename);
            if (Files.exists(candidate)) {
                migrationPath = candidate;
                break;
            }
            current = current.getParent();
        }
        if (migrationPath == null) {
            throw new IllegalStateException("Migration not found: " + filename);
        }
        String script = Files.readString(migrationPath);
        ScriptUtils.executeSqlScript(statement.getConnection(),
                new EncodedResource(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8))));
    }

    @BeforeEach
    void setUp() throws Exception {
        session = sqlSessionFactory.openSession(false);
        resultMapper = session.getMapper(SubmissionResultOutboxMapper.class);
        integrationOutboxMapper = session.getMapper(IntegrationOutboxMapper.class);
        session.getConnection().createStatement().execute("DELETE FROM integration_outbox");
        session.getConnection().createStatement().execute("DELETE FROM submission_result_outbox");
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

    @Test
    @DisplayName("writes integration outbox before marking the result DELIVERED")
    void publishesResultThroughDurableOutbox() throws Exception {
        SubmissionResultOutboxRecord result = resultRecord("result-1", "submission-1", 7L);
        resultMapper.insert(result);
        session.commit();
        session.getConnection().createStatement().execute(
                "UPDATE submission_result_outbox SET next_retry_at = NOW(3) WHERE id = 'result-1'");
        session.commit();

        IntegrationOutboxMapper transactionalMapper =
                sqlSessionTemplate.getMapper(IntegrationOutboxMapper.class);
        IntegrationEventPublisher publisher = transactionalPublisher(transactionalMapper);
        SubmissionResultDispatcher dispatcher = new SubmissionResultDispatcher(resultMapper, publisher);

        assertThat(dispatcher.dispatch()).isEqualTo(1);
        session.commit();

        SubmissionResultOutboxRecord delivered = resultMapper.selectById("result-1");
        assertThat(delivered.getState()).isEqualTo("DELIVERED");
        assertThat(delivered.getDeliveredAt()).isNotNull();

        IntegrationOutboxRecord event = integrationOutboxMapper.selectList(null).get(0);
        assertThat(event.getEventId()).isEqualTo("result-1");
        assertThat(event.getOwner()).isEqualTo("App");
        assertThat(event.getEventType()).isEqualTo("SubmissionJudged");
        assertThat(event.getAggregateId()).isEqualTo("submission-1");
        assertThat(event.getAggregateVersion()).isEqualTo(7L);
        assertThat(event.getState()).isEqualTo("PENDING");
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getPayload())
                .containsEntry("submissionId", "submission-1")
                .containsEntry("generation", 7)
                .containsEntry("verdict", "ACCEPTED")
                .containsEntry("runtimeMs", 120)
                .containsEntry("memoryMb", 4.5)
                .containsEntry("contestId", "contest-1");
    }

    @Test
    @DisplayName("requeues a DEAD integration event on source retry")
    void requeuesDeadIntegrationEventOnSourceRetry() throws Exception {
        SubmissionResultOutboxRecord result = resultRecord("result-dead", "submission-dead", 4L);
        resultMapper.insert(result);
        session.getConnection().createStatement().execute(
                "UPDATE submission_result_outbox SET next_retry_at = NOW(3) WHERE id = 'result-dead'");

        IntegrationOutboxRecord dead = new IntegrationOutboxRecord();
        dead.setEventId("result-dead");
        dead.setOwner("App");
        dead.setAggregateId("submission-dead");
        dead.setAggregateVersion(4L);
        dead.setEventType("SubmissionJudged");
        dead.setSchemaVersion(1);
        dead.setPayload(Map.of("submissionId", "submission-dead"));
        dead.setState("DEAD");
        dead.setAttempts(5);
        dead.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        dead.setNextRetryAt(LocalDateTime.now());
        integrationOutboxMapper.insert(dead);
        session.commit();

        IntegrationOutboxMapper transactionalMapper =
                sqlSessionTemplate.getMapper(IntegrationOutboxMapper.class);
        SubmissionResultDispatcher dispatcher = new SubmissionResultDispatcher(
                resultMapper, transactionalPublisher(transactionalMapper));

        assertThat(dispatcher.dispatch()).isEqualTo(1);
        session.commit();

        SubmissionResultOutboxRecord delivered = resultMapper.selectById("result-dead");
        assertThat(delivered.getState()).isEqualTo("DELIVERED");
        IntegrationOutboxRecord requeued = integrationOutboxMapper.selectById("result-dead");
        assertThat(requeued.getState()).isEqualTo("PENDING");
        assertThat(requeued.getAttempts()).isZero();
        assertThat(requeued.getLastError()).isNull();
    }

    @Test
    @DisplayName("does not publish historical non-terminal result rows")
    void skipsNonTerminalResultRows() throws Exception {
        SubmissionResultOutboxRecord result = resultRecord(
                "result-pending", "submission-pending", 2L);
        result.setVerdict("Pending");
        resultMapper.insert(result);
        session.commit();

        SubmissionResultDispatcher dispatcher =
                new SubmissionResultDispatcher(resultMapper, mock(IntegrationEventPublisher.class));

        assertThat(dispatcher.dispatch()).isZero();
        session.commit();

        SubmissionResultOutboxRecord retained = resultMapper.selectById("result-pending");
        assertThat(retained.getState()).isEqualTo("PENDING");
        assertThat(integrationOutboxMapper.selectList(null)).isEmpty();
    }

    @Test
    @DisplayName("keeps the result retryable when durable publication fails")
    void publicationFailureRemainsRetryable() throws Exception {
        SubmissionResultOutboxRecord result = resultRecord("result-2", "submission-2", 3L);
        resultMapper.insert(result);
        session.commit();
        session.getConnection().createStatement().execute(
                "UPDATE submission_result_outbox SET next_retry_at = NOW(3) WHERE id = 'result-2'");
        session.commit();

        IntegrationEventPublisher publisher = mock(IntegrationEventPublisher.class);
        when(publisher.publishWithId(
                anyString(), anyString(), anyString(), anyString(), anyLong(),
                isNull(), isNull(), anyMap()))
                .thenThrow(new IllegalStateException("integration outbox unavailable"));
        SubmissionResultDispatcher dispatcher = new SubmissionResultDispatcher(resultMapper, publisher);

        assertThat(dispatcher.dispatch()).isZero();
        session.commit();

        SubmissionResultOutboxRecord retryable = resultMapper.selectById("result-2");
        assertThat(retryable.getState()).isEqualTo("PENDING");
        assertThat(retryable.getAttempts()).isEqualTo(1);
        assertThat(retryable.getLastError()).isEqualTo("integration outbox unavailable");
        assertThat(retryable.getDeliveredAt()).isNull();
    }

    private static IntegrationEventPublisher transactionalPublisher(IntegrationOutboxMapper mapper) {
        IntegrationEventPublisher target =
                new IntegrationEventPublisher(mapper, new ObjectMapper(), Clock.systemUTC());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource()));
        return (IntegrationEventPublisher) proxyFactory.getProxy();
    }


    private static SubmissionResultOutboxRecord resultRecord(String id, String submissionId,
                                                               long generation) {
        SubmissionResultOutboxRecord record = new SubmissionResultOutboxRecord();
        record.setId(id);
        record.setSubmissionId(submissionId);
        record.setGeneration(generation);
        record.setUserId("user-1");
        record.setProblemId("problem-1");
        record.setVerdict("ACCEPTED");
        record.setRuntimeMs(120);
        record.setMemoryMb(4.5);
        record.setContestId("contest-1");
        record.setState("PENDING");
        record.setAttempts(0);
        record.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        return record;
    }
}
