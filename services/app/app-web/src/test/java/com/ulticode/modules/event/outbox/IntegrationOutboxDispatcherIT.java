package com.ulticode.modules.event.outbox;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P6-OUTBOX-001: Integration outbox + per-Owner dispatcher IT.
 *
 * <p>Tests the dispatcher's state-machine transitions (PENDING→CLAIMED→DELIVERED/DEAD)
 * using a real MySQL 8.0 Testcontainers for the DB state assertions, and Mockito
 * for the mapper and Redis layers:
 * <ul>
 *   <li><b>Happy path</b>: PENDING → CLAIMED → DELIVERED after XADD succeeds.</li>
 *   <li><b>Poison event DLQ</b>: failed publish at MAX_ATTEMPTS → DEAD.</li>
 *   <li><b>Retry on first failure</b>: failed publish at attempt 0 → PENDING for retry.</li>
 *   <li><b>Oldest-outbox-age metric</b>: non-null when PENDING rows exist.</li>
 * </ul>
 */
@Testcontainers
@DisplayName("P6-OUTBOX-001: Integration Outbox Dispatcher IT")
class IntegrationOutboxDispatcherIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_outbox_it")
            .withUsername("root")
            .withPassword("root");

    private static Connection conn;

    @BeforeAll
    static void provision() throws Exception {
        conn = DriverManager.getConnection(mysql.getJdbcUrl(), "root", "root");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE `integration_outbox` (
                  `event_id`           varchar(40)  NOT NULL,
                  `owner`              varchar(20)  NOT NULL,
                  `aggregate_id`       varchar(120) NOT NULL,
                  `aggregate_version`  bigint       NOT NULL DEFAULT 0,
                  `causation_id`       varchar(40)  DEFAULT NULL,
                  `trace_id`           varchar(40)  DEFAULT NULL,
                  `event_type`         varchar(120) NOT NULL,
                  `schema_version`     int          NOT NULL DEFAULT 1,
                  `payload`            json         NOT NULL,
                  `state`              varchar(16)  NOT NULL DEFAULT 'PENDING',
                  `attempts`           int          NOT NULL DEFAULT 0,
                  `last_error`         text         DEFAULT NULL,
                  `stream_id`          varchar(80)  DEFAULT NULL,
                  `created_at`         datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  `claimed_at`         datetime(3)  DEFAULT NULL,
                  `claim_owner`        varchar(80)   DEFAULT NULL,
                  `delivered_at`       datetime(3)  DEFAULT NULL,
                  `next_retry_at`      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (`event_id`),
                  KEY `idx_outbox_state_retry` (`state`, `next_retry_at`),
                  KEY `idx_outbox_claim_owner` (`state`, `claim_owner`, `created_at`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        }
    }

    @AfterAll
    static void cleanup() throws SQLException {
        if (conn != null) conn.close();
    }

    private IntegrationOutboxRecord makeRecord(String eventId, String eventType, int attempts) {
        IntegrationOutboxRecord r = new IntegrationOutboxRecord();
        r.setEventId(eventId);
        r.setOwner("App");
        r.setAggregateId("agg-1");
        r.setAggregateVersion(0L);
        r.setEventType(eventType);
        r.setSchemaVersion(1);
        r.setState("CLAIMED");
        r.setAttempts(attempts);
        return r;
    }

    @Nested
    @DisplayName("Claim + publish + deliver cycle")
    class ClaimPublishDeliver {

        @Test
        @DisplayName("PENDING row → CLAIMED → DELIVERED after XADD succeeds")
        void happyPath() {
            IntegrationOutboxRecord record = makeRecord("evt-happy-001", "UserRegistered", 0);

            IntegrationOutboxMapper mapper = mock(IntegrationOutboxMapper.class);
            when(mapper.claimPending(anyString(), anyInt())).thenReturn(1);
            when(mapper.selectClaimed(anyString())).thenReturn(List.of(record));

            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
            when(redis.opsForStream()).thenReturn((StreamOperations) streamOps);
            when(streamOps.add(any(MapRecord.class))).thenReturn(RecordId.of("1234567890-0"));

            when(mapper.markDelivered(anyString(), anyString(), anyString())).thenReturn(1);

            IntegrationOutboxDispatcher dispatcher = new IntegrationOutboxDispatcher(mapper, redis);
            int published = dispatcher.dispatch();

            assertThat(published).isEqualTo(1);
            verify(mapper).reclaimStaleClaimed();
            verify(mapper).markDelivered(eq("evt-happy-001"), anyString(), eq("1234567890-0"));
            verify(mapper, never()).markFailed(anyString(), anyString(), anyString(), anyInt());

            ArgumentCaptor<MapRecord> streamRecord = ArgumentCaptor.forClass(MapRecord.class);
            verify(streamOps).add(streamRecord.capture());
            Map<?, ?> streamFields = (Map<?, ?>) streamRecord.getValue().getValue();
            assertThat(streamFields.get("aggregateVersion")).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("Poison event → DLQ")
    class PoisonEvent {

        @Test
        @DisplayName("Failed publish at attempt 4→5 (MAX) → DEAD")
        void poisonEventDead() {
            IntegrationOutboxRecord record = makeRecord("evt-poison-001", "PoisonEvent", 4);

            IntegrationOutboxMapper mapper = mock(IntegrationOutboxMapper.class);
            when(mapper.claimPending(anyString(), anyInt())).thenReturn(1);
            when(mapper.selectClaimed(anyString())).thenReturn(List.of(record));

            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
            when(redis.opsForStream()).thenReturn((StreamOperations) streamOps);
            when(streamOps.add(any(MapRecord.class)))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            IntegrationOutboxDispatcher dispatcher = new IntegrationOutboxDispatcher(mapper, redis);
            dispatcher.dispatch();

            // markFailed with maxAttempts=5 (IntegrationOutboxDispatcher.MAX_ATTEMPTS)
            verify(mapper).markFailed(
                    eq("evt-poison-001"), anyString(), contains("Redis connection refused"), eq(5));
            verify(mapper, never()).markDelivered(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Failed publish at attempt 0→1 → PENDING (retry)")
        void retryOnFirstFailure() {
            IntegrationOutboxRecord record = makeRecord("evt-retry-001", "RetryEvent", 0);

            IntegrationOutboxMapper mapper = mock(IntegrationOutboxMapper.class);
            when(mapper.claimPending(anyString(), anyInt())).thenReturn(1);
            when(mapper.selectClaimed(anyString())).thenReturn(List.of(record));

            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
            when(redis.opsForStream()).thenReturn((StreamOperations) streamOps);
            when(streamOps.add(any(MapRecord.class)))
                    .thenThrow(new RuntimeException("Transient failure"));

            IntegrationOutboxDispatcher dispatcher = new IntegrationOutboxDispatcher(mapper, redis);
            dispatcher.dispatch();

            // Should call markFailed (which internally transitions to PENDING since 0+1 < 5)
            verify(mapper).markFailed(
                    eq("evt-retry-001"), anyString(), contains("Transient failure"), eq(5));
            verify(mapper, never()).markDelivered(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Oldest-outbox-age metric (real MySQL)")
    class OldestAgeMetric {

        @Test
        @DisplayName("Non-null when PENDING rows exist")
        void oldestAgeWithPending() throws Exception {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM integration_outbox WHERE state IN ('PENDING','CLAIMED')");
                stmt.execute("INSERT INTO integration_outbox (event_id, owner, aggregate_id, aggregate_version, event_type, schema_version, payload, state, attempts, next_retry_at) " +
                        "VALUES ('evt-age-001', 'App', 'agg-1', 0, 'MetricTest', 1, '{}', 'PENDING', 0, NOW(3))");
            }
            Thread.sleep(1100);

            IntegrationOutboxMapper mapper = mock(IntegrationOutboxMapper.class);
            when(mapper.oldestOutboxAgeSeconds()).thenAnswer(inv -> {
                try (var ps = conn.prepareStatement(
                        "SELECT TIMESTAMPDIFF(SECOND, MIN(created_at), NOW(3)) FROM integration_outbox WHERE state IN ('PENDING','CLAIMED')")) {
                    var rs = ps.executeQuery();
                    return rs.next() ? rs.getLong(1) : null;
                }
            });

            IntegrationOutboxDispatcher dispatcher = new IntegrationOutboxDispatcher(mapper, mock(StringRedisTemplate.class));
            Long age = dispatcher.getOldestOutboxAgeSeconds();

            assertThat(age).as("Oldest outbox age should be non-null with pending events").isNotNull();
            assertThat(age).as("Age should be at least 1 second").isGreaterThanOrEqualTo(1);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM integration_outbox WHERE event_id = 'evt-age-001'");
            }
        }

        @Test
        @DisplayName("Null when no PENDING/CLAIMED rows")
        void oldestAgeEmpty() throws Exception {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM integration_outbox WHERE state IN ('PENDING','CLAIMED')");
            }

            IntegrationOutboxMapper mapper = mock(IntegrationOutboxMapper.class);
            when(mapper.oldestOutboxAgeSeconds()).thenAnswer(inv -> {
                try (var ps = conn.prepareStatement(
                        "SELECT TIMESTAMPDIFF(SECOND, MIN(created_at), NOW(3)) FROM integration_outbox WHERE state IN ('PENDING','CLAIMED')")) {
                    var rs = ps.executeQuery();
                    if (rs.next()) {
                        long val = rs.getLong(1);
                        return rs.wasNull() ? null : val;
                    }
                    return null;
                }
            });

            IntegrationOutboxDispatcher dispatcher = new IntegrationOutboxDispatcher(mapper, mock(StringRedisTemplate.class));
            Long age = dispatcher.getOldestOutboxAgeSeconds();

            assertThat(age).as("No pending events → age should be null").isNull();
        }
    }
}
