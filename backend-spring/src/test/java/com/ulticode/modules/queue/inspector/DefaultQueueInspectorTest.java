package com.ulticode.modules.queue.inspector;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.dto.ProbeStatus;
import com.ulticode.modules.queue.dto.QueueHealthSnapshotDTO;
import com.ulticode.modules.queue.dto.QueueStatsDTO;
import com.ulticode.modules.queue.port.JudgeQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RQueue;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultQueueInspector}. The read module
 * is the new home for queue status, queue stats, and queue size
 * lookups; tests here mirror what {@code QueueServiceTest} used to
 * cover for those three methods, plus the monitoring-oriented
 * {@link QueueInspector#getQueueHealthSnapshot(String)} contract.
 */
@ExtendWith(MockitoExtension.class)
class DefaultQueueInspectorTest {

    @Mock
    private RQueue<Object> judgeQueue;

    @Mock
    private RQueue<Object> emailQueue;

    @Mock
    private RQueue<Object> notificationQueue;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RedisTemplate<String, Object> jobStatusRedisTemplate;

    @Mock
    private JudgeQueue judgeQueuePort;

    @Mock
    private ObjectProvider<JudgeQueue> judgeQueueProvider;

    private DefaultQueueInspector queueInspector;

    private static final String JOB_ID = "test-job-id";

    @BeforeEach
    void setUp() {
        // Legacy backend by default: provider resolves to null (no JudgeQueue bean).
        // Mockito returns null from the unstubbed getIfAvailable() call.
        queueInspector = new DefaultQueueInspector(
                judgeQueue, emailQueue, notificationQueue, jobStatusRedisTemplate,
                judgeQueueProvider);
    }

    @Nested
    @DisplayName("getJobStatus Tests")
    class GetJobStatusTests {

        @Test
        @DisplayName("should return job status when found")
        void shouldReturnJobStatusWhenFound() {
            // Arrange
            JobStatusDTO status = JobStatusDTO.builder()
                    .jobId(JOB_ID)
                    .status(QueueConstants.JobStatus.PENDING)
                    .payload(Map.of("testKey", "testValue"))
                    .createdAt(LocalDateTime.now())
                    .build();
            when(jobStatusRedisTemplate.opsForValue()
                    .get(QueueConstants.JOB_STATUS_PREFIX + JOB_ID))
                    .thenReturn(status);

            // Act
            JobStatusDTO result = queueInspector.getJobStatus(JOB_ID);

            // Assert
            assertNotNull(result);
            assertEquals(JOB_ID, result.getJobId());
        }

        @Test
        @DisplayName("should throw exception when job not found")
        void shouldThrowExceptionWhenJobNotFound() {
            // Arrange
            when(jobStatusRedisTemplate.opsForValue().get(anyString())).thenReturn(null);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueInspector.getJobStatus(JOB_ID));
            assertEquals(ErrorCode.QUEUE_JOB_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("should throw exception when stored payload is not a JobStatusDTO")
        void shouldThrowExceptionWhenStoredPayloadIsNotJobStatusDTO() {
            // Arrange
            when(jobStatusRedisTemplate.opsForValue().get(anyString())).thenReturn("not-a-dto");

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueInspector.getJobStatus(JOB_ID));
            assertEquals(ErrorCode.QUEUE_JOB_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getQueueStats Tests")
    class GetQueueStatsTests {

        @Test
        @DisplayName("should return queue stats for judge queue")
        void shouldReturnQueueStatsForJudgeQueue() {
            // Arrange
            when(judgeQueue.size()).thenReturn(5);

            // Act
            QueueStatsDTO stats = queueInspector.getQueueStats(QueueConstants.JUDGE_QUEUE);

            // Assert
            assertNotNull(stats);
            assertEquals(QueueConstants.JUDGE_QUEUE, stats.getQueueName());
            assertEquals(5, stats.getWaitingCount());
        }

        @Test
        @DisplayName("should throw exception for unknown queue")
        void shouldThrowExceptionForUnknownQueue() {
            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueInspector.getQueueStats("unknown_queue"));
            assertEquals(ErrorCode.QUEUE_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getQueueSize Tests")
    class GetQueueSizeTests {

        @Test
        @DisplayName("should return queue size")
        void shouldReturnQueueSize() {
            // Arrange
            when(judgeQueue.size()).thenReturn(10);

            // Act
            long size = queueInspector.getQueueSize(QueueConstants.JUDGE_QUEUE);

            // Assert
            assertEquals(10, size);
        }

        @Test
        @DisplayName("should throw exception for unknown queue")
        void shouldThrowExceptionForUnknownQueue() {
            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueInspector.getQueueSize("unknown_queue"));
            assertEquals(ErrorCode.QUEUE_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getQueueHealthSnapshot Tests")
    class GetQueueHealthSnapshotTests {

        @Test
        @DisplayName("legacy backend: should report RQueue.size() as waitingDepth with OK status")
        void shouldReportRQueueSizeAsWaitingDepth() {
            // Arrange
            when(judgeQueue.size()).thenReturn(7);

            // Act
            QueueHealthSnapshotDTO snapshot =
                    queueInspector.getQueueHealthSnapshot(QueueConstants.JUDGE_QUEUE);

            // Assert
            assertNotNull(snapshot);
            assertEquals(QueueConstants.JUDGE_QUEUE, snapshot.getQueueName());
            assertEquals(7L, snapshot.getWaitingDepth());
            assertEquals(ProbeStatus.OK, snapshot.getProbeStatus());
        }

        @Test
        @DisplayName("legacy backend: should translate Redis failure into PROBE_FAILED (never zero-then-OK)")
        void shouldTranslateRQueueFailureIntoProbeFailed() {
            // Arrange — RQueue.size() throws (e.g. Redis down)
            when(judgeQueue.size()).thenThrow(new RuntimeException("Redis connection refused"));

            // Act
            QueueHealthSnapshotDTO snapshot =
                    queueInspector.getQueueHealthSnapshot(QueueConstants.JUDGE_QUEUE);

            // Assert
            assertNotNull(snapshot);
            assertEquals(ProbeStatus.PROBE_FAILED, snapshot.getProbeStatus());
            // The depth is informational only when the probe failed; callers MUST
            // consult probeStatus, not the depth, for health decisions.
            assertEquals(0L, snapshot.getWaitingDepth());
        }

        @Test
        @DisplayName("unknown queue: should surface QUEUE_NOT_FOUND (programming error, not probe failure)")
        void shouldSurfaceQueueNotFoundForUnknownQueue() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> queueInspector.getQueueHealthSnapshot("no_such_queue"));
            assertEquals(ErrorCode.QUEUE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("Stream backend (use-port=true): judge_queue depth comes from JudgeQueue.pendingDepth()")
        void streamBackendShouldSourceJudgeDepthFromPort() {
            // Rebuild the inspector with the JudgeQueue port present, mimicking
            // app.features.judge-queue.use-port=true.
            DefaultQueueInspector streamBackedInspector = new DefaultQueueInspector(
                    judgeQueue, emailQueue, notificationQueue, jobStatusRedisTemplate,
                    judgeQueueProvider);
            when(judgeQueueProvider.getIfAvailable()).thenReturn(judgeQueuePort);
            when(judgeQueuePort.pendingDepth()).thenReturn(42L);

            QueueHealthSnapshotDTO snapshot =
                    streamBackedInspector.getQueueHealthSnapshot(QueueConstants.JUDGE_QUEUE);

            assertNotNull(snapshot);
            assertEquals(42L, snapshot.getWaitingDepth());
            assertEquals(ProbeStatus.OK, snapshot.getProbeStatus());
        }

        @Test
        @DisplayName("Stream backend: a port probe failure surfaces as PROBE_FAILED")
        void streamBackendFailureSurfacesAsProbeFailed() {
            DefaultQueueInspector streamBackedInspector = new DefaultQueueInspector(
                    judgeQueue, emailQueue, notificationQueue, jobStatusRedisTemplate,
                    judgeQueueProvider);
            when(judgeQueueProvider.getIfAvailable()).thenReturn(judgeQueuePort);
            when(judgeQueuePort.pendingDepth()).thenThrow(new RuntimeException("STREAM key missing"));

            QueueHealthSnapshotDTO snapshot =
                    streamBackedInspector.getQueueHealthSnapshot(QueueConstants.JUDGE_QUEUE);

            assertEquals(ProbeStatus.PROBE_FAILED, snapshot.getProbeStatus());
            assertEquals(0L, snapshot.getWaitingDepth());
        }

        @Test
        @DisplayName("Stream backend: non-judge queues still read their RQueue (no Stream fallback)")
        void streamBackendNonJudgeQueuesStillUseRQueue() {
            // Provider intentionally not stubbed: the EMAIL_QUEUE path never
            // consults it (the JUDGE_QUEUE guard short-circuits), so stubbing
            // getIfAvailable() would trip UnnecessaryStubbingException.
            DefaultQueueInspector streamBackedInspector = new DefaultQueueInspector(
                    judgeQueue, emailQueue, notificationQueue, jobStatusRedisTemplate,
                    judgeQueueProvider);
            when(emailQueue.size()).thenReturn(3);

            QueueHealthSnapshotDTO snapshot =
                    streamBackedInspector.getQueueHealthSnapshot(QueueConstants.EMAIL_QUEUE);

            assertEquals(3L, snapshot.getWaitingDepth());
            assertEquals(ProbeStatus.OK, snapshot.getProbeStatus());
        }

        @Test
        @DisplayName("Snapshot defers failed/completed aggregates to zero (bounded SCAN is a follow-up)")
        void snapshotDefersFailedAndCompletedAggregates() {
            when(judgeQueue.size()).thenReturn(5);

            QueueHealthSnapshotDTO snapshot =
                    queueInspector.getQueueHealthSnapshot(QueueConstants.JUDGE_QUEUE);

            assertEquals(0L, snapshot.getFailedCount(),
                    "failedCount is intentionally zero until a bounded SCAN over queue:job:* is wired");
            assertEquals(0L, snapshot.getCompletedCount(),
                    "completedCount is intentionally zero until a bounded SCAN over queue:job:* is wired");
        }
    }
}
