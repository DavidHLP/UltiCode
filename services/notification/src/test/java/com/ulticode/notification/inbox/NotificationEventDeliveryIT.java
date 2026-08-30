package com.ulticode.notification.inbox;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import com.ulticode.modules.notification.channel.InAppNotificationChannel;
import com.ulticode.modules.notification.consumer.NotificationIntentEventConsumer;
import com.ulticode.modules.notification.consumer.SubmissionJudgedNotificationConsumer;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.ledger.mapper.NotificationDeliveryLedgerMapper;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import com.ulticode.modules.notification.service.impl.NotificationServiceImpl;
import com.ulticode.notification.event.NotificationEventIdentity;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.beans.factory.ObjectProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@DisplayName("Notification event to inbox to delivery IT")
class NotificationEventDeliveryIT {

    private static final String STREAM_KEY = "stream:integration";
    private static final String CONSUMER = "App-Notification";
    private static final String INTENT_ID = "system-alert:user-1:maintenance";
    private static final String EVENT_ID = NotificationEventIdentity.eventId(INTENT_ID);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_notification_event_it")
            .withUsername("test")
            .withPassword("test");

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static HikariDataSource dataSource;
    private static SqlSession session;
    private static JdbcTemplate jdbcTemplate;
    private static StringRedisTemplate redisTemplate;
    private static ObjectProvider<PlatformTransactionManager> transactionManagerProvider;
    private static ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider;
    private static ConsumerInboxMapper inboxMapper;
    private static NotificationIntentEventConsumer intentConsumer;
    private static SubmissionJudgedNotificationConsumer submissionConsumer;

    @BeforeAll
    static void provision() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE consumer_inbox (
                  id VARCHAR(40) NOT NULL,
                  consumer VARCHAR(40) NOT NULL,
                  event_id VARCHAR(40) NOT NULL,
                  event_type VARCHAR(120) NOT NULL,
                  payload JSON NOT NULL,
                  state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                  attempts INT NOT NULL DEFAULT 0,
                  last_error TEXT,
                  lease_owner VARCHAR(80),
                  lease_expires_at DATETIME(3),
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  processed_at DATETIME(3),
                  next_retry_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_notification_inbox_consumer_event (consumer, event_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            statement.execute("""
                CREATE TABLE notifications (
                  id VARCHAR(40) NOT NULL,
                  user_id VARCHAR(40) NOT NULL,
                  type VARCHAR(64) NOT NULL,
                  category VARCHAR(64) NOT NULL,
                  title VARCHAR(255) NOT NULL,
                  body TEXT,
                  link VARCHAR(500),
                  announcement_id VARCHAR(40),
                  metadata JSON,
                  is_read TINYINT(1) NOT NULL DEFAULT 0,
                  read_at DATETIME(3),
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                  PRIMARY KEY (id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            statement.execute("""
                CREATE TABLE notification_preferences (
                  id VARCHAR(40) NOT NULL,
                  user_id VARCHAR(40) NOT NULL,
                  communication TINYINT(1) NOT NULL DEFAULT 1,
                  marketing TINYINT(1) NOT NULL DEFAULT 0,
                  security TINYINT(1) NOT NULL DEFAULT 1,
                  system_enabled TINYINT(1) NOT NULL DEFAULT 1,
                  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_notification_preferences_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            statement.execute("""
                CREATE TABLE notification_delivery_ledger (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  intent_id VARCHAR(255) NOT NULL,
                  channel_id VARCHAR(32) NOT NULL,
                  user_id VARCHAR(40) NOT NULL,
                  intent_type VARCHAR(64) NOT NULL,
                  delivery_state VARCHAR(16) NOT NULL,
                  failure_reason VARCHAR(500),
                  reclaim_attempts INT NOT NULL DEFAULT 0,
                  delivered_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  claimed_at DATETIME(3),
                  claim_owner VARCHAR(80),
                  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_notification_delivery (intent_id, channel_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        config.setMaximumPoolSize(4);
        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory redisFactory = new LettuceConnectionFactory(redisConfig);
        redisFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(redisFactory);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ConsumerInboxMapper.class);
        configuration.addMapper(NotificationMapper.class);
        configuration.addMapper(NotificationPreferenceMapper.class);
        configuration.addMapper(NotificationDeliveryLedgerMapper.class);
        SqlSessionFactory sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        session = sessionFactory.openSession(true);
        inboxMapper = session.getMapper(ConsumerInboxMapper.class);
        NotificationMapper notificationMapper = session.getMapper(NotificationMapper.class);
        NotificationPreferenceMapper preferenceMapper = session.getMapper(NotificationPreferenceMapper.class);
        NotificationDeliveryLedgerMapper ledgerMapper = session.getMapper(NotificationDeliveryLedgerMapper.class);

        NotificationServiceImpl notificationService = new NotificationServiceImpl(
                Clock.systemUTC(), notificationMapper, preferenceMapper);
        NotificationDispatcher dispatcher = new NotificationDispatcher(
                List.of(new InAppNotificationChannel(notificationService)),
                ledgerMapper, preferenceMapper, new SimpleMeterRegistry());
        intentConsumer = new NotificationIntentEventConsumer(dispatcher);
        submissionConsumer = new SubmissionJudgedNotificationConsumer(dispatcher);

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transactionManagerProvider = Mockito.mock(ObjectProvider.class);
        when(transactionManagerProvider.getIfAvailable()).thenReturn(transactionManager);
        meterRegistryProvider = Mockito.mock(ObjectProvider.class);
        when(meterRegistryProvider.getIfAvailable()).thenReturn(null);
    }

    @AfterAll
    static void cleanup() {
        if (session != null) {
            session.close();
        }
        if (redisTemplate != null
                && redisTemplate.getConnectionFactory() instanceof LettuceConnectionFactory factory) {
            factory.destroy();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void resetState() {
        redisTemplate.delete(STREAM_KEY);
        jdbcTemplate.update("DELETE FROM consumer_inbox");
        jdbcTemplate.update("DELETE FROM notification_delivery_ledger");
        jdbcTemplate.update("DELETE FROM notifications");
    }

    private NotificationIntegrationInboxBridge newBridge() {
        UuidGenerator uuidGenerator = () -> UUID.randomUUID().toString();
        return new NotificationIntegrationInboxBridge(
                redisTemplate,
                inboxMapper,
                new ObjectMapper(),
                uuidGenerator,
                transactionManagerProvider,
                meterRegistryProvider,
                submissionConsumer,
                intentConsumer);
    }

    private RecordId addIntentEvent() {
        Map<String, String> fields = Map.of(
                "eventId", EVENT_ID,
                "owner", "App",
                "aggregateId", INTENT_ID,
                "aggregateVersion", "0",
                "eventType", "NotificationIntentCreated",
                "schemaVersion", "1",
                "payload", "{\"intentType\":\"SYSTEM\",\"intentId\":\"" + INTENT_ID
                        + "\",\"userId\":\"user-1\",\"category\":\"SYSTEM\","
                        + "\"alertKey\":\"maintenance\",\"title\":\"Maintenance\","
                        + "\"body\":\"Scheduled maintenance\",\"link\":\"/status\"}");
        return redisTemplate.opsForStream().add(StreamRecords.mapBacked(fields)
                .withStreamKey(STREAM_KEY));
    }

    @Test
    @DisplayName("NotificationIntentCreated flows through Redis, inbox, ledger, and Notification row")
    void intentFlowsThroughDurableDelivery() {
        RecordId streamId = addIntentEvent();

        assertThat(newBridge().consume()).isGreaterThan(0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT state FROM consumer_inbox WHERE consumer = ? AND event_id = ?",
                String.class, CONSUMER, EVENT_ID)).isEqualTo("PROCESSED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = 'user-1'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT delivery_state FROM notification_delivery_ledger WHERE intent_id = ? AND channel_id = 'in_app'",
                String.class, INTENT_ID)).isEqualTo("DELIVERED");
        assertThat(redisTemplate.opsForStream().pending(
                STREAM_KEY, CONSUMER, Range.unbounded(), 10)).isEmpty();

        redisTemplate.opsForStream().delete(STREAM_KEY, streamId);
    }

    @Test
    @DisplayName("duplicate stream events are absorbed by the consumer inbox and notification idempotency fences")
    void duplicateIntentIsDeduplicated() {
        RecordId first = addIntentEvent();
        RecordId second = addIntentEvent();

        newBridge().consume();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consumer_inbox WHERE consumer = ? AND event_id = ?",
                Integer.class, CONSUMER, EVENT_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = 'user-1'", Integer.class))
                .isEqualTo(1);
        assertThat(redisTemplate.opsForStream().pending(
                STREAM_KEY, CONSUMER, Range.unbounded(), 10)).isEmpty();

        redisTemplate.opsForStream().delete(STREAM_KEY, first, second);
    }
}
