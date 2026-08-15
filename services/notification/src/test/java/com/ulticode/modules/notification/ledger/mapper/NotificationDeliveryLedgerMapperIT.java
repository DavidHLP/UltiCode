package com.ulticode.modules.notification.ledger.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ulticode.modules.notification.ledger.DeliveryState;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("Notification delivery ledger mapper IT")
class NotificationDeliveryLedgerMapperIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_notification_ledger_it")
            .withUsername("root")
            .withPassword("root");

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpSchema() throws Exception {
        dataSource = new DriverManagerDataSource(
                mysql.getJdbcUrl() + "?useAffectedRows=true",
                mysql.getUsername(), mysql.getPassword());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE notification_delivery_ledger (
                      id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                      intent_id       VARCHAR(255) NOT NULL,
                      channel_id      VARCHAR(32) NOT NULL,
                      user_id         VARCHAR(36) NOT NULL,
                      intent_type     VARCHAR(64) NOT NULL,
                      delivery_state  VARCHAR(16) NOT NULL,
                      failure_reason  VARCHAR(500) NULL,
                      reclaim_attempts INT NOT NULL DEFAULT 0,
                      delivered_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                      claimed_at      DATETIME(3) NULL,
                      claim_owner     VARCHAR(80) NULL,
                      updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                      ON UPDATE CURRENT_TIMESTAMP(3),
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_ledger_intent_channel (intent_id, channel_id),
                      KEY idx_ledger_claim (delivery_state, claimed_at, claim_owner)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(NotificationDeliveryLedgerMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        sqlSessionFactory = factoryBean.getObject();
    }

    @BeforeEach
    void cleanLedger() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM notification_delivery_ledger");
        }
    }

    @AfterAll
    static void tearDown() {
        // Testcontainers handles container teardown.
    }

    @Test
    @DisplayName("failed rows honor backoff and durable retries use the same claim path")
    void failedRowsHonorBackoffAndDurableRetriesUseClaimPath() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            session.getConnection().createStatement().execute("""
                    INSERT INTO notification_delivery_ledger
                        (intent_id, channel_id, user_id, intent_type, delivery_state,
                         failure_reason, reclaim_attempts, delivered_at, updated_at)
                    VALUES
                        ('durable-wire', 'in_app', 'user-1', 'SUBMISSION', 'FAILED',
                         'durable failure', 0, NOW(3) - INTERVAL 10 MINUTE,
                         NOW(3) - INTERVAL 10 MINUTE),
                        ('legacy-follow', 'in_app', 'user-1', 'FollowReceivedIntent', 'FAILED',
                         'legacy failure', 0, NOW(3), NOW(3))
                    """);
            session.commit();

            NotificationDeliveryLedgerMapper mapper =
                    session.getMapper(NotificationDeliveryLedgerMapper.class);
            assertThat(mapper.tryClaim(
                    "durable-wire", "in_app", "user-1", "SUBMISSION",
                    "durable-owner")).isPositive();
            assertThat(mapper.tryClaim(
                    "legacy-follow", "in_app", "user-1", "FollowReceivedIntent",
                    "legacy-owner")).isZero();
            session.commit();

            session.getConnection().createStatement().execute("""
                    UPDATE notification_delivery_ledger
                    SET updated_at = NOW(3) - INTERVAL 10 MINUTE
                    WHERE intent_id = 'legacy-follow'
                    """);
            session.commit();
            assertThat(mapper.tryClaim(
                    "legacy-follow", "in_app", "user-1", "FollowReceivedIntent",
                    "legacy-owner")).isPositive();
            session.commit();

            try (var resultSet = session.getConnection().createStatement().executeQuery("""
                    SELECT intent_id, delivery_state, claim_owner
                    FROM notification_delivery_ledger
                    ORDER BY intent_id
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("intent_id")).isEqualTo("durable-wire");
                assertThat(resultSet.getString("delivery_state"))
                        .isEqualTo(DeliveryState.CLAIMED.name());
                assertThat(resultSet.getString("claim_owner")).isEqualTo("durable-owner");

                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("intent_id")).isEqualTo("legacy-follow");
                assertThat(resultSet.getString("delivery_state"))
                        .isEqualTo(DeliveryState.CLAIMED.name());
                assertThat(resultSet.getString("claim_owner")).isEqualTo("legacy-owner");
            }
        }
    }

    @Test
    @DisplayName("terminal ledger rows return zero for duplicate claims")
    void terminalRowsAreNotClaimedAgain() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            session.getConnection().createStatement().execute("""
                    INSERT INTO notification_delivery_ledger
                        (intent_id, channel_id, user_id, intent_type, delivery_state,
                         reclaim_attempts, delivered_at, updated_at)
                    VALUES
                        ('delivered-intent', 'in_app', 'user-1', 'SUBMISSION', 'DELIVERED',
                         0, NOW(3), NOW(3))
                    """);
            session.commit();

            NotificationDeliveryLedgerMapper mapper =
                    session.getMapper(NotificationDeliveryLedgerMapper.class);
            assertThat(mapper.tryClaim(
                    "delivered-intent", "in_app", "user-1", "SUBMISSION",
                    "owner-1")).isZero();
            session.commit();

            try (var resultSet = session.getConnection().createStatement().executeQuery("""
                    SELECT delivery_state
                    FROM notification_delivery_ledger
                    WHERE intent_id = 'delivered-intent'
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("delivery_state"))
                        .isEqualTo(DeliveryState.DELIVERED.name());
            }
        }
    }

    @Test
    @DisplayName("concurrent dispatchers have one winning claim owner")
    void concurrentClaimsHaveSingleWinner() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            session.getConnection().createStatement().execute("""
                    INSERT INTO notification_delivery_ledger
                        (intent_id, channel_id, user_id, intent_type, delivery_state,
                         failure_reason, reclaim_attempts, delivered_at, updated_at)
                    VALUES
                        ('claim-race', 'email', 'user-1', 'SYSTEM', 'FAILED',
                         'temporary failure', 0,
                         NOW(3) - INTERVAL 10 MINUTE,
                         NOW(3) - INTERVAL 10 MINUTE)
                    """);
            session.commit();
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                try (SqlSession session = sqlSessionFactory.openSession(true)) {
                    return session.getMapper(NotificationDeliveryLedgerMapper.class)
                            .tryClaim("claim-race", "email", "user-1", "SYSTEM", "owner-a");
                }
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                try (SqlSession session = sqlSessionFactory.openSession(true)) {
                    return session.getMapper(NotificationDeliveryLedgerMapper.class)
                            .tryClaim("claim-race", "email", "user-1", "SYSTEM", "owner-b");
                }
            });
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(0, 1);
        } finally {
            executor.shutdownNow();
        }

        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                     SELECT delivery_state, claim_owner
                     FROM notification_delivery_ledger
                     WHERE intent_id = 'claim-race'
                     """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("delivery_state"))
                    .isEqualTo(DeliveryState.CLAIMED.name());
            assertThat(resultSet.getString("claim_owner")).isIn("owner-a", "owner-b");
        }
    }

    @Test
    @DisplayName("claim owner fences late completion and failed retry honors backoff")
    void ownerFencesLateCompletionAndBackoff() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            NotificationDeliveryLedgerMapper mapper =
                    session.getMapper(NotificationDeliveryLedgerMapper.class);

            assertThat(mapper.tryClaim(
                    "fenced", "websocket", "user-1", "SYSTEM", "owner-a")).isPositive();
            session.commit();

            assertThat(mapper.markFailed(
                    "fenced", "websocket", "temporary", "owner-a")).isPositive();
            session.commit();

            assertThat(mapper.tryClaim(
                    "fenced", "websocket", "user-1", "SYSTEM", "owner-b")).isZero();
            session.commit();

            session.getConnection().createStatement().execute("""
                    UPDATE notification_delivery_ledger
                    SET updated_at = NOW(3) - INTERVAL 10 MINUTE
                    WHERE intent_id = 'fenced'
                    """);
            session.commit();

            assertThat(mapper.tryClaim(
                    "fenced", "websocket", "user-1", "SYSTEM", "owner-b")).isPositive();
            assertThat(mapper.markDelivered("fenced", "websocket", "owner-a")).isZero();
            assertThat(mapper.markDelivered("fenced", "websocket", "owner-b")).isPositive();
            session.commit();

            try (var resultSet = session.getConnection().createStatement().executeQuery("""
                    SELECT delivery_state, reclaim_attempts, claim_owner
                    FROM notification_delivery_ledger
                    WHERE intent_id = 'fenced'
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("delivery_state"))
                        .isEqualTo(DeliveryState.DELIVERED.name());
                assertThat(resultSet.getInt("reclaim_attempts")).isEqualTo(1);
                assertThat(resultSet.getString("claim_owner")).isNull();
            }
        }
    }

    @Test
    @DisplayName("stale reaper clears the lease and fences the old owner")
    void staleReaperFencesOldOwner() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            NotificationDeliveryLedgerMapper mapper =
                    session.getMapper(NotificationDeliveryLedgerMapper.class);

            assertThat(mapper.tryClaim(
                    "stale", "in_app", "user-1", "LegacyNotification", "owner-a")).isPositive();
            session.commit();

            session.getConnection().createStatement().execute("""
                    UPDATE notification_delivery_ledger
                    SET claimed_at = NOW(3) - INTERVAL 11 MINUTE
                    WHERE intent_id = 'stale'
                    """);
            session.commit();

            assertThat(mapper.reapStaleClaimed()).isEqualTo(1);
            session.commit();
            assertThat(mapper.markDelivered("stale", "in_app", "owner-a")).isZero();

            session.getConnection().createStatement().execute("""
                    UPDATE notification_delivery_ledger
                    SET updated_at = NOW(3) - INTERVAL 10 MINUTE
                    WHERE intent_id = 'stale'
                    """);
            session.commit();

            assertThat(mapper.tryClaim(
                    "stale", "in_app", "user-1", "LegacyNotification", "owner-b")).isPositive();
            assertThat(mapper.markDelivered("stale", "in_app", "owner-b")).isPositive();
            session.commit();
        }
    }
    @Test
    @DisplayName("ledger lag metric reports the oldest in-flight CLAIMED lease")
    void ledgerLagReflectsOldestClaim() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            NotificationDeliveryLedgerMapper mapper =
                    session.getMapper(NotificationDeliveryLedgerMapper.class);

            session.getConnection().createStatement().execute("""
                    INSERT INTO notification_delivery_ledger
                        (intent_id, channel_id, user_id, intent_type, delivery_state,
                         claim_owner, claimed_at, delivered_at, updated_at)
                    VALUES
                        ('lag-old', 'email', 'user-1', 'SYSTEM', 'CLAIMED',
                         'owner-a', NOW(3) - INTERVAL 6 MINUTE,
                         NOW(3) - INTERVAL 6 MINUTE, NOW(3) - INTERVAL 6 MINUTE),
                        ('lag-new', 'email', 'user-1', 'SYSTEM', 'CLAIMED',
                         'owner-b', NOW(3) - INTERVAL 1 MINUTE,
                         NOW(3) - INTERVAL 1 MINUTE, NOW(3) - INTERVAL 1 MINUTE)
                    """);
            session.commit();

            Long lag = mapper.oldestClaimedAgeSeconds();
            session.commit();
            assertThat(lag).isNotNull().isGreaterThanOrEqualTo(300);
        }
    }

    @Test
    @DisplayName("stale reaper releases durable notification claims for inbox replay")
    void staleReaperReleasesDurableNotificationClaims() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            session.getConnection().createStatement().execute("""
                    INSERT INTO notification_delivery_ledger
                        (intent_id, channel_id, user_id, intent_type, delivery_state,
                         reclaim_attempts, claim_owner, claimed_at, delivered_at, updated_at)
                    VALUES
                        ('durable-stale', 'in_app', 'user-1', 'FOLLOW', 'CLAIMED',
                         0, 'dead-owner', NOW(3) - INTERVAL 11 MINUTE,
                         NOW(3) - INTERVAL 11 MINUTE, NOW(3) - INTERVAL 11 MINUTE)
                    """);
            session.commit();

            NotificationDeliveryLedgerMapper mapper =
                    session.getMapper(NotificationDeliveryLedgerMapper.class);
            assertThat(mapper.reapStaleClaimed()).isEqualTo(1);
            session.commit();

            try (var resultSet = session.getConnection().createStatement().executeQuery("""
                    SELECT delivery_state, claim_owner, claimed_at
                    FROM notification_delivery_ledger
                    WHERE intent_id = 'durable-stale'
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("delivery_state"))
                        .isEqualTo(DeliveryState.FAILED.name());
                assertThat(resultSet.getString("claim_owner")).isNull();
                assertThat(resultSet.getTimestamp("claimed_at")).isNull();
            }
        }
    }
}
