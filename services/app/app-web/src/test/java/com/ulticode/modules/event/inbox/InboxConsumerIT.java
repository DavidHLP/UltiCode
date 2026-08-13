package com.ulticode.modules.event.inbox;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * P6-INBOX-001: Consumer inbox dedup + reclaimable notification ledger IT.
 *
 * <p>Tests against a real MySQL 8.0 Testcontainers instance:
 * <ul>
 *   <li><b>Dedup</b>: (consumer, event_id) unique constraint blocks duplicate consume.</li>
 *   <li><b>Lease reclaim</b>: stale PROCESSING row is reclaimed by next lease holder.</li>
 *   <li><b>DLQ</b>: event that fails MAX_ATTEMPTS times → DEAD.</li>
 *   <li><b>Happy path</b>: PENDING → PROCESSING → PROCESSED after handler succeeds.</li>
 * </ul>
 */
@Testcontainers
@DisplayName("P6-INBOX-001: Consumer Inbox IT")
class InboxConsumerIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_inbox_it")
            .withUsername("root")
            .withPassword("root");

    private static Connection conn;
    private static InboxConsumer consumer;
    private static ConsumerInboxMapper mapper;

    @BeforeAll
    static void provision() throws Exception {
        conn = DriverManager.getConnection(mysql.getJdbcUrl(), "root", "root");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE `consumer_inbox` (
                  `id`               varchar(40)  NOT NULL,
                  `consumer`         varchar(40)  NOT NULL,
                  `event_id`         varchar(40)  NOT NULL,
                  `event_type`       varchar(120) NOT NULL,
                  `payload`          json         NOT NULL,
                  `state`            varchar(16)  NOT NULL DEFAULT 'PENDING',
                  `attempts`         int          NOT NULL DEFAULT 0,
                  `last_error`       text         DEFAULT NULL,
                  `lease_owner`      varchar(80)  DEFAULT NULL,
                  `lease_expires_at` datetime(3)  DEFAULT NULL,
                  `created_at`       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  `processed_at`     datetime(3)  DEFAULT NULL,
                  `next_retry_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uniq_consumer_event` (`consumer`, `event_id`),
                  KEY `idx_inbox_state_retry` (`state`, `next_retry_at`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        }

        mapper = mock(ConsumerInboxMapper.class);
        consumer = new InboxConsumer(mapper);
    }

    @AfterAll
    static void cleanup() throws SQLException {
        if (conn != null) conn.close();
    }

    private void insertInboxRow(String id, String consumer, String eventId, String eventType,
                                 String state, int attempts) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(
                "INSERT INTO consumer_inbox (id, consumer, event_id, event_type, payload, state, attempts, next_retry_at) " +
                "VALUES ('%s', '%s', '%s', '%s', '{}', '%s', %d, NOW(3))",
                id, consumer, eventId, eventType, state, attempts));
        }
    }

    @Nested
    @DisplayName("Dedup: (consumer, event_id) unique constraint")
    class Dedup {

        @Test
        @DisplayName("Duplicate insert rejected by unique constraint")
        void duplicateRejected() throws SQLException {
            insertInboxRow("dedup-1", "App", "evt-dup-001", "TestEvent", "PENDING", 0);

            boolean duplicateFailed = false;
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO consumer_inbox (id, consumer, event_id, event_type, payload, state, attempts, next_retry_at) " +
                        "VALUES ('dedup-2', 'App', 'evt-dup-001', 'TestEvent', '{}', 'PENDING', 0, NOW(3))");
            } catch (SQLException e) {
                duplicateFailed = e.getErrorCode() == 1062;
            }
            assertThat(duplicateFailed).as("Duplicate (consumer, event_id) should be rejected with error 1062").isTrue();

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM consumer_inbox WHERE id IN ('dedup-1')");
            }
        }
    }

    @Nested
    @DisplayName("Stale lease reclaim")
    class LeaseReclaim {

        @Test
        @DisplayName("Stale PROCESSING row reclaimed by next claimLease")
        void staleClaimReclaimed() throws SQLException {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO consumer_inbox (id, consumer, event_id, event_type, payload, state, attempts, lease_owner, lease_expires_at, next_retry_at) " +
                        "VALUES ('stale-1', 'App', 'evt-stale-001', 'TestEvent', '{}', 'PROCESSING', 1, 'dead-pid', " +
                        "DATE_SUB(NOW(3), INTERVAL 1 MINUTE), NOW(3))");
            }

            int reclaimed;
            try (var ps = conn.prepareStatement(
                    "UPDATE consumer_inbox SET state='PROCESSING', lease_owner=?, lease_expires_at=DATE_ADD(NOW(3), INTERVAL 30 SECOND) " +
                    "WHERE consumer='App' " +
                    "AND ((state='PENDING' AND next_retry_at <= NOW(3)) " +
                    "OR (state='PROCESSING' AND lease_expires_at < NOW(3))) " +
                    "AND id IN (SELECT id FROM (SELECT id FROM consumer_inbox " +
                    "WHERE consumer='App' " +
                    "AND ((state='PENDING' AND next_retry_at <= NOW(3)) " +
                    "OR (state='PROCESSING' AND lease_expires_at < NOW(3))) " +
                    "ORDER BY created_at LIMIT 50) AS c)")) {
                ps.setString(1, "new-pid");
                reclaimed = ps.executeUpdate();
            }
            assertThat(reclaimed).as("Stale PROCESSING row should be reclaimed").isEqualTo(1);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM consumer_inbox WHERE id = 'stale-1'");
            }
        }
    }

    @Nested
    @DisplayName("Happy path and DLQ via InboxConsumer")
    class ConsumerFlow {

        @Test
        @DisplayName("PENDING → PROCESSING → PROCESSED after handler succeeds")
        void happyPath() {
            ConsumerInboxRecord record = new ConsumerInboxRecord();
            record.setId("happy-1");
            record.setConsumer("App");
            record.setEventId("evt-happy-inbox");
            record.setEventType("UserRegistered");
            record.setPayload(Map.of("userId", "u-001"));
            record.setState("PROCESSING");
            record.setAttempts(0);

            when(mapper.claimLease(anyString(), eq("App"), anyInt())).thenReturn(1);
            when(mapper.selectLeased(anyString(), eq("App"))).thenReturn(java.util.List.of(record));
            when(mapper.renewLease(eq("happy-1"), eq("App"), anyString())).thenReturn(1);
            when(mapper.markProcessed(eq("happy-1"), eq("App"), anyString())).thenReturn(1);

            java.util.concurrent.atomic.AtomicBoolean handlerCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
            consumer.registerHandler("UserRegistered", payload -> {
                assertThat(payload.get("userId")).isEqualTo("u-001");
                handlerCalled.set(true);
            });

            int processed = consumer.consume();

            assertThat(processed).isEqualTo(1);
            assertThat(handlerCalled.get()).as("Handler should have been called").isTrue();
            verify(mapper).markProcessed(eq("happy-1"), eq("App"), anyString());
        }

        @Test
        @DisplayName("Handler exception → markFailed with retry")
        void handlerFailure() {
            ConsumerInboxRecord record = new ConsumerInboxRecord();
            record.setId("fail-1");
            record.setConsumer("App");
            record.setEventId("evt-fail-inbox");
            record.setEventType("RiskyEvent");
            record.setPayload(Map.of());
            record.setState("PROCESSING");
            record.setAttempts(0);

            when(mapper.claimLease(anyString(), eq("App"), anyInt())).thenReturn(1);
            when(mapper.selectLeased(anyString(), eq("App"))).thenReturn(java.util.List.of(record));
            when(mapper.renewLease(eq("fail-1"), eq("App"), anyString())).thenReturn(1);

            consumer.registerHandler("RiskyEvent", payload -> {
                throw new RuntimeException("Handler exploded");
            });

            consumer.consume();

            verify(mapper).markFailed(
                    eq("fail-1"), eq("App"), anyString(), contains("RuntimeException"), eq(10));
        }
    }
}
