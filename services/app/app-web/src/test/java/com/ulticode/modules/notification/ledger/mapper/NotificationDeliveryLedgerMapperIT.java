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
                      intent_id       VARCHAR(64) NOT NULL,
                      channel_id      VARCHAR(32) NOT NULL,
                      user_id         VARCHAR(36) NOT NULL,
                      intent_type     VARCHAR(64) NOT NULL,
                      delivery_state  VARCHAR(16) NOT NULL,
                      failure_reason  VARCHAR(500) NULL,
                      reclaim_attempts INT NOT NULL DEFAULT 0,
                      delivered_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                      updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                      ON UPDATE CURRENT_TIMESTAMP(3),
                      PRIMARY KEY (id),
                      UNIQUE KEY uk_ledger_intent_channel (intent_id, channel_id)
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
    @DisplayName("legacy reaper does not reclaim durable submission rows")
    void reclaimFailedLegacyExcludesDurableSubmissionWireTypes() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            session.getConnection().createStatement().execute("""
                    INSERT INTO notification_delivery_ledger
                        (intent_id, channel_id, user_id, intent_type, delivery_state,
                         failure_reason, reclaim_attempts, delivered_at, updated_at)
                    VALUES
                        ('durable-wire', 'in_app', 'user-1', 'SUBMISSION', 'FAILED',
                         'durable failure', 0, NOW(3) - INTERVAL 10 MINUTE,
                         NOW(3) - INTERVAL 10 MINUTE),
                        ('durable-legacy', 'in_app', 'user-1', 'SubmissionCompletedIntent', 'FAILED',
                         'durable failure', 0, NOW(3) - INTERVAL 10 MINUTE,
                         NOW(3) - INTERVAL 10 MINUTE),
                        ('legacy-follow', 'in_app', 'user-1', 'FollowReceivedIntent', 'FAILED',
                         'legacy failure', 0, NOW(3) - INTERVAL 10 MINUTE,
                         NOW(3) - INTERVAL 10 MINUTE)
                    """);
            session.commit();

            NotificationDeliveryLedgerMapper mapper = session.getMapper(NotificationDeliveryLedgerMapper.class);
            assertThat(mapper.reclaimFailedLegacy()).isEqualTo(1);
            session.commit();

            try (var resultSet = session.getConnection().createStatement().executeQuery("""
                    SELECT intent_id, delivery_state, reclaim_attempts
                    FROM notification_delivery_ledger
                    ORDER BY intent_id
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("intent_id")).isEqualTo("durable-legacy");
                assertThat(resultSet.getString("delivery_state")).isEqualTo(DeliveryState.FAILED.name());
                assertThat(resultSet.getInt("reclaim_attempts")).isZero();

                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("intent_id")).isEqualTo("durable-wire");
                assertThat(resultSet.getString("delivery_state")).isEqualTo(DeliveryState.FAILED.name());
                assertThat(resultSet.getInt("reclaim_attempts")).isZero();

                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("intent_id")).isEqualTo("legacy-follow");
                assertThat(resultSet.getString("delivery_state")).isEqualTo(DeliveryState.FAILED.name());
                assertThat(resultSet.getInt("reclaim_attempts")).isZero();
            }

            assertThat(mapper.tryClaim(
                    "legacy-follow", "in_app", "user-1", "FollowReceivedIntent")).isPositive();
            session.commit();

            try (var resultSet = session.getConnection().createStatement().executeQuery("""
                    SELECT delivery_state, delivered_at
                    FROM notification_delivery_ledger
                    WHERE intent_id = 'legacy-follow'
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("delivery_state"))
                        .isEqualTo(DeliveryState.CLAIMED.name());
                assertThat(resultSet.getTimestamp("delivered_at")).isNotNull();
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
                    "delivered-intent", "in_app", "user-1", "SUBMISSION")).isZero();
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
}
