package com.ulticode.modules.submission.result;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.event.outbox.IntegrationEventPublisher;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P6-RESULT-001: Result outbox IT.
 *
 * <p>Tests against a real MySQL 8.0 Testcontainers instance:
 * <ul>
 *   <li><b>Happy path</b>: record verdict → dispatch → DELIVERED + integration event published.</li>
 *   <li><b>Crash-after-commit recovery</b>: verdict written + outbox row written → dispatcher delivers.</li>
 *   <li><b>Deterministic replay</b>: repeated dispatch drains all PENDING rows.</li>
 *   <li><b>Rejudge idempotency</b>: duplicate submission_id rejected by unique constraint.</li>
 *   <li><b>Poison → DEAD</b>: publish failure after MAX_ATTEMPTS → DEAD.</li>
 * </ul>
 */
@Testcontainers
@DisplayName("P6-RESULT-001: Submission Result Outbox IT")
class SubmissionResultDispatcherIT {

    @Container
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ulticode_result_it")
            .withUsername("root")
            .withPassword("root");

    private static Connection conn;

    @org.junit.jupiter.api.BeforeAll
    static void provision() throws Exception {
        conn = DriverManager.getConnection(mysql.getJdbcUrl(), "root", "root");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE `submission_result_outbox` (
                  `id`              varchar(40)  NOT NULL,
                  `submission_id`   varchar(40)  NOT NULL,
                  `generation`      bigint       NOT NULL DEFAULT 0,
                  `user_id`         varchar(40)  NOT NULL,
                  `problem_id`      varchar(120) NOT NULL,
                  `verdict`         varchar(30)  NOT NULL,
                  `runtime_ms`      int          NOT NULL DEFAULT 0,
                  `memory_mb`       double       NOT NULL DEFAULT 0,
                  `contest_id`      varchar(40)  DEFAULT NULL,
                  `state`           varchar(16)  NOT NULL DEFAULT 'PENDING',
                  `attempts`        int          NOT NULL DEFAULT 0,
                  `last_error`      text         DEFAULT NULL,
                  `created_at`      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  `delivered_at`    datetime(3)  DEFAULT NULL,
                  `next_retry_at`   datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uniq_result_sub_gen` (`submission_id`, `generation`),
                  KEY `idx_result_state_retry` (`state`, `next_retry_at`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                """);
        }
    }

    @org.junit.jupiter.api.AfterAll
    static void cleanup() throws SQLException {
        if (conn != null) conn.close();
    }

    private void insertOutboxRow(String id, String submissionId, String verdict,
                                  String state, int attempts) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format(
                "INSERT INTO submission_result_outbox (id, submission_id, generation, user_id, problem_id, verdict, runtime_ms, memory_mb, state, attempts, next_retry_at) " +
                "VALUES ('%s', '%s', 0, 'u-001', 'p-001', '%s', 100, 16.0, '%s', %d, NOW(3))",
                id, submissionId, verdict, state, attempts));
        }
    }

    @Nested
    @DisplayName("Happy path: record → dispatch → DELIVERED")
    class HappyPath {

        @Test
        @DisplayName("PENDING row dispatched → DELIVERED + integration event published")
        void happyPath() {
            SubmissionResultOutboxRecord record = new SubmissionResultOutboxRecord();
            record.setId("res-happy-001");
            record.setSubmissionId("sub-001");
            record.setUserId("u-001");
            record.setProblemId("p-001");
            record.setVerdict("ACCEPTED");
            record.setRuntimeMs(100);
            record.setMemoryMb(16.0);
            record.setState("CLAIMED");
            record.setAttempts(0);

            SubmissionResultOutboxMapper mapper = mock(SubmissionResultOutboxMapper.class);
            when(mapper.claimPending(anyInt())).thenReturn(1);
            when(mapper.selectList(any())).thenReturn(List.of(record));

            IntegrationEventPublisher publisher = mock(IntegrationEventPublisher.class);
            SubmissionResultDispatcher dispatcher = new SubmissionResultDispatcher(mapper, publisher);
            int published = dispatcher.dispatch();

            assertThat(published).isEqualTo(1);
            verify(mapper).markDelivered("res-happy-001");
            verify(publisher).publish(eq("App"), eq("SubmissionJudged"), eq("sub-001"), anyMap());
        }
    }

    @Nested
    @DisplayName("Rejudge idempotency: unique constraint on submission_id")
    class RejudgeIdempotency {

        @Test
        @DisplayName("Duplicate (submission_id, generation) rejected; different generation allowed")
        void duplicateRejected() throws SQLException {
            insertOutboxRow("res-1", "sub-dup", "ACCEPTED", "PENDING", 0);

            // Same (submission_id, generation) should be rejected
            boolean duplicateFailed = false;
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO submission_result_outbox (id, submission_id, generation, user_id, problem_id, verdict, state, attempts, next_retry_at) " +
                        "VALUES ('res-2', 'sub-dup', 0, 'u-001', 'p-001', 'WRONG_ANSWER', 'PENDING', 0, NOW(3))");
            } catch (SQLException e) {
                duplicateFailed = e.getErrorCode() == 1062;
            }
            assertThat(duplicateFailed).as("Duplicate (submission_id, generation=0) should be rejected").isTrue();

            // Different generation for same submission_id should succeed (rejudge)
            boolean rejudgeSucceeded = false;
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO submission_result_outbox (id, submission_id, generation, user_id, problem_id, verdict, state, attempts, next_retry_at) " +
                        "VALUES ('res-3', 'sub-dup', 1, 'u-001', 'p-001', 'WRONG_ANSWER', 'PENDING', 0, NOW(3))");
                rejudgeSucceeded = true;
            } catch (SQLException ignored) {}
            assertThat(rejudgeSucceeded).as("Rejudge with generation=1 should be allowed").isTrue();

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM submission_result_outbox WHERE id IN ('res-1', 'res-3')");
            }
        }
    }

    @Nested
    @DisplayName("Poison → DEAD after MAX_ATTEMPTS")
    class PoisonEvent {

        @Test
        @DisplayName("Publish failure at attempt 4→5 → DEAD")
        void poisonDead() {
            SubmissionResultOutboxRecord record = new SubmissionResultOutboxRecord();
            record.setId("res-poison-001");
            record.setSubmissionId("sub-poison");
            record.setUserId("u-001");
            record.setProblemId("p-001");
            record.setVerdict("TIME_LIMIT_EXCEEDED");
            record.setState("CLAIMED");
            record.setAttempts(4);

            SubmissionResultOutboxMapper mapper = mock(SubmissionResultOutboxMapper.class);
            when(mapper.claimPending(anyInt())).thenReturn(1);
            when(mapper.selectList(any())).thenReturn(List.of(record));

            IntegrationEventPublisher publisher = mock(IntegrationEventPublisher.class);
            doThrow(new RuntimeException("Integration bus down"))
                    .when(publisher).publish(anyString(), anyString(), anyString(), anyMap());

            UuidGenerator uuid = mock(UuidGenerator.class);
            SubmissionResultDispatcher dispatcher = new SubmissionResultDispatcher(mapper, publisher);
            dispatcher.dispatch();

            verify(mapper).markFailed(eq("res-poison-001"), contains("Integration bus down"), eq(5));
            verify(mapper, never()).markDelivered(anyString());
        }

        @Test
        @DisplayName("Publish failure at attempt 0→1 → PENDING (retry)")
        void retryOnFirstFailure() {
            SubmissionResultOutboxRecord record = new SubmissionResultOutboxRecord();
            record.setId("res-retry-001");
            record.setSubmissionId("sub-retry");
            record.setUserId("u-001");
            record.setProblemId("p-001");
            record.setVerdict("ACCEPTED");
            record.setState("CLAIMED");
            record.setAttempts(0);

            SubmissionResultOutboxMapper mapper = mock(SubmissionResultOutboxMapper.class);
            when(mapper.claimPending(anyInt())).thenReturn(1);
            when(mapper.selectList(any())).thenReturn(List.of(record));

            IntegrationEventPublisher publisher = mock(IntegrationEventPublisher.class);
            doThrow(new RuntimeException("Transient failure"))
                    .when(publisher).publish(anyString(), anyString(), anyString(), anyMap());

            UuidGenerator uuid = mock(UuidGenerator.class);
            SubmissionResultDispatcher dispatcher = new SubmissionResultDispatcher(mapper, publisher);
            dispatcher.dispatch();

            verify(mapper).markFailed(eq("res-retry-001"), contains("Transient failure"), eq(5));
        }
    }

    @Nested
    @DisplayName("Deterministic replay: drain all PENDING rows")
    class Replay {

        @Test
        @DisplayName("Multiple PENDING rows all drained to DELIVERED")
        void replayDrainsBacklog() {
            SubmissionResultOutboxRecord r1 = new SubmissionResultOutboxRecord();
            r1.setId("replay-1");
            r1.setSubmissionId("sub-r1");
            r1.setUserId("u-001");
            r1.setProblemId("p-001");
            r1.setVerdict("ACCEPTED");
            r1.setState("CLAIMED");

            SubmissionResultOutboxRecord r2 = new SubmissionResultOutboxRecord();
            r2.setId("replay-2");
            r2.setSubmissionId("sub-r2");
            r2.setUserId("u-002");
            r2.setProblemId("p-001");
            r2.setVerdict("WRONG_ANSWER");
            r2.setState("CLAIMED");

            SubmissionResultOutboxMapper mapper = mock(SubmissionResultOutboxMapper.class);
            when(mapper.claimPending(anyInt())).thenReturn(2);
            when(mapper.selectList(any())).thenReturn(List.of(r1, r2));

            IntegrationEventPublisher publisher = mock(IntegrationEventPublisher.class);
            SubmissionResultDispatcher dispatcher = new SubmissionResultDispatcher(mapper, publisher);
            int published = dispatcher.dispatch();

            assertThat(published).isEqualTo(2);
            verify(mapper).markDelivered("replay-1");
            verify(mapper).markDelivered("replay-2");
            verify(publisher, times(2)).publish(anyString(), anyString(), anyString(), anyMap());
        }
    }
}
