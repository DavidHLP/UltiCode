package com.ulticode.modules.event.inbox;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("Consumer inbox transaction boundary IT")
class InboxConsumerTransactionIT {

    private static final String CONSUMER = "App-Notification";

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_inbox_transaction_it")
            .withUsername("root")
            .withPassword("root");

    private static DataSource dataSource;
    private static SqlSessionTemplate sqlSessionTemplate;
    private static TransactionTemplate transactionTemplate;
    private JdbcTemplate jdbcTemplate;
    private ConsumerInboxMapper mapper;

    @BeforeAll
    static void setUpSchema() throws Exception {
        dataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE consumer_inbox (
                      id               varchar(40)  NOT NULL,
                      consumer         varchar(40)  NOT NULL,
                      event_id         varchar(40)  NOT NULL,
                      event_type       varchar(120) NOT NULL,
                      payload          json         NOT NULL,
                      state            varchar(16)  NOT NULL DEFAULT 'PENDING',
                      attempts         int          NOT NULL DEFAULT 0,
                      last_error       text         DEFAULT NULL,
                      lease_owner      varchar(80)  DEFAULT NULL,
                      lease_expires_at datetime(3)  DEFAULT NULL,
                      created_at       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                      processed_at     datetime(3)  DEFAULT NULL,
                      next_retry_at    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                      PRIMARY KEY (id),
                      UNIQUE KEY uniq_consumer_event (consumer, event_id),
                      KEY idx_inbox_state_retry (state, next_retry_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
            statement.execute("""
                    CREATE TABLE inbox_side_effect (
                      id        varchar(40) NOT NULL,
                      event_id  varchar(40) NOT NULL,
                      PRIMARY KEY (id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ConsumerInboxMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void cleanTables() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM inbox_side_effect");
        jdbcTemplate.update("DELETE FROM consumer_inbox");
        mapper = sqlSessionTemplate.getMapper(ConsumerInboxMapper.class);
    }

    @AfterAll
    static void tearDown() {
        // Testcontainers handles container teardown.
    }

    @Test
    @DisplayName("handler failure rolls back its DB side effect but leaves inbox retryable")
    void handlerFailureRollsBackSideEffect() {
        insertPending("failure-row", "event-failure");
        InboxConsumer consumer = new InboxConsumer(mapper, CONSUMER, transactionTemplate);
        consumer.registerHandler("TestEvent", payload -> {
            jdbcTemplate.update(
                    "INSERT INTO inbox_side_effect (id, event_id) VALUES (?, ?)",
                    "effect-failure", "event-failure");
            throw new IllegalStateException("delivery failed");
        });

        assertThat(consumer.consume()).isZero();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_side_effect WHERE event_id = ?",
                Integer.class, "event-failure")).isZero();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT state, attempts, last_error FROM consumer_inbox WHERE id = ?",
                "failure-row"))
                .containsEntry("state", "PENDING")
                .containsEntry("attempts", 1)
                .containsEntry("last_error", "IllegalStateException");
    }
    @Test
    @DisplayName("outside-transaction handler keeps side effects when inbox transition loses its lease")
    void outsideTransactionHandlerIsolatedFromInboxTransition() {
        insertPending("outside-row", "event-outside");
        InboxConsumer consumer = new InboxConsumer(mapper, CONSUMER, transactionTemplate);
        consumer.registerHandlerOutsideTransaction("TestEvent", (eventId, payload) -> {
            jdbcTemplate.update(
                    "INSERT INTO inbox_side_effect (id, event_id) VALUES (?, ?)",
                    "effect-outside", "event-outside");
            jdbcTemplate.update(
                    "UPDATE consumer_inbox SET lease_owner = ? WHERE id = ?",
                    "replacement-owner", "outside-row");
        });

        assertThat(consumer.consume()).isZero();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_side_effect WHERE event_id = ?",
                Integer.class, "event-outside")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT state, lease_owner FROM consumer_inbox WHERE id = ?",
                "outside-row"))
                .containsEntry("state", "PROCESSING")
                .containsEntry("lease_owner", "replacement-owner");
    }

    @Test
    @DisplayName("retry commits one side effect and the PROCESSED transition atomically")
    void retryProcessesOnceAfterFailure() {
        insertPending("retry-row", "event-retry");
        InboxConsumer consumer = new InboxConsumer(mapper, CONSUMER, transactionTemplate);
        consumer.registerHandler("TestEvent", payload -> {
            jdbcTemplate.update(
                    "INSERT INTO inbox_side_effect (id, event_id) VALUES (?, ?)",
                    "effect-retry", "event-retry");
        });
        jdbcTemplate.update(
                "UPDATE consumer_inbox SET next_retry_at = NOW(3) WHERE id = ?", "retry-row");

        assertThat(consumer.consume()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_side_effect WHERE event_id = ?",
                Integer.class, "event-retry")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT state, attempts, processed_at FROM consumer_inbox WHERE id = ?",
                "retry-row"))
                .containsEntry("state", "PROCESSED")
                .containsEntry("attempts", 0)
                .hasEntrySatisfying("processed_at", value -> assertThat(value).isNotNull());
    }

    @Test
    @DisplayName("transient failures remain retryable beyond the legacy five-attempt horizon")
    void transientFailuresRemainRetryableBeyondLegacyHorizon() {
        insertPending("horizon-row", "event-horizon");
        AtomicInteger attempts = new AtomicInteger();
        InboxConsumer consumer = new InboxConsumer(mapper, CONSUMER, transactionTemplate);
        consumer.registerHandler("TestEvent", payload -> {
            if (attempts.incrementAndGet() <= 7) {
                throw new IllegalStateException("temporary delivery failure");
            }
        });

        for (int attempt = 0; attempt < 7; attempt++) {
            jdbcTemplate.update(
                    "UPDATE consumer_inbox SET next_retry_at = NOW(3) WHERE id = ?",
                    "horizon-row");
            assertThat(consumer.consume()).isZero();
        }

        jdbcTemplate.update(
                "UPDATE consumer_inbox SET next_retry_at = NOW(3) WHERE id = ?",
                "horizon-row");
        assertThat(consumer.consume()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForMap(
                "SELECT state, attempts FROM consumer_inbox WHERE id = ?",
                "horizon-row"))
                .containsEntry("state", "PROCESSED")
                .containsEntry("attempts", 7);
    }

    @Test
    @DisplayName("the tenth failed attempt is the durable inbox dead-letter boundary")
    void tenthFailureReachesDeadLetterBoundary() {
        insertPending("dead-row", "event-dead");
        InboxConsumer consumer = new InboxConsumer(mapper, CONSUMER, transactionTemplate);
        consumer.registerHandler("TestEvent", payload -> {
            throw new IllegalStateException("permanent delivery failure");
        });

        for (int attempt = 0; attempt < 10; attempt++) {
            jdbcTemplate.update(
                    "UPDATE consumer_inbox SET next_retry_at = NOW(3) WHERE id = ?",
                    "dead-row");
            assertThat(consumer.consume()).isZero();
        }

        assertThat(jdbcTemplate.queryForMap(
                "SELECT state, attempts FROM consumer_inbox WHERE id = ?",
                "dead-row"))
                .containsEntry("state", "DEAD")
                .containsEntry("attempts", 10);
    }

    private void insertPending(String id, String eventId) {
        assertThat(mapper.insertIfAbsent(
                id, CONSUMER, eventId, "TestEvent", "{}"))
                .isEqualTo(1);
    }
}
