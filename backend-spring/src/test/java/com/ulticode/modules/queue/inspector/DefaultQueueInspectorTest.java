package com.ulticode.modules.queue.inspector;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.dto.QueueStatsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RQueue;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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
 * cover for those three methods.
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

    private DefaultQueueInspector queueInspector;

    private static final String JOB_ID = "test-job-id";

    @BeforeEach
    void setUp() {
        queueInspector = new DefaultQueueInspector(
                judgeQueue, emailQueue, notificationQueue, jobStatusRedisTemplate);
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
}
