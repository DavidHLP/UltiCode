package com.ulticode.modules.queue.service;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.concurrent.TimeUnit;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobRequestDTO;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.inspector.QueueInspector;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.service.impl.QueueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RQueue;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueueService.
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private RQueue<Object> judgeQueue;

    @Mock
    private RQueue<Object> emailQueue;

    @Mock
    private RQueue<Object> notificationQueue;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RedisTemplate<String, Object> jobStatusRedisTemplate;

    @Mock
    private QueueInspector queueInspector;

    @Mock
    private Clock clock;

    @Spy
    private QueueConfig queueConfig = new QueueConfig();

    @InjectMocks
    private QueueServiceImpl queueService;

    private static final String SUBMISSION_ID = "test-submission-id";
    private static final String PROBLEM_ID = "test-problem-id";
    private static final String USER_ID = "test-user-id";
    private static final String JOB_ID = "test-job-id";
    private static final String LANGUAGE = "java";
    private static final String CODE = "public class Main { public static void main() { System.out.println(\"Hello\"); } }";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queueService, "judgeQueue", judgeQueue);
        ReflectionTestUtils.setField(queueService, "emailQueue", emailQueue);
        ReflectionTestUtils.setField(queueService, "notificationQueue", notificationQueue);
        ReflectionTestUtils.setField(queueService, "queueInspector", queueInspector);
        ReflectionTestUtils.setField(queueConfig, "enableStatusTracking", true);
        ReflectionTestUtils.setField(queueConfig, "jobStatusTtlSeconds", 86400L);
        ReflectionTestUtils.setField(queueService, "clock", clock);
        org.mockito.Mockito.lenient().when(clock.instant()).thenReturn(java.time.Instant.now());
        org.mockito.Mockito.lenient().when(clock.getZone()).thenReturn(java.time.ZoneId.systemDefault());
    }

    @Nested
    @DisplayName("enqueueJudgeJob Tests")
    class EnqueueJudgeJobTests {

        @Test
        @DisplayName("should enqueue judge job with all required fields")
        void shouldEnqueueJudgeJobWithAllRequiredFields() {
            // Arrange
            when(judgeQueue.add(any())).thenReturn(true);

            // Act
            String jobId = queueService.enqueueJudgeJob(
                    SUBMISSION_ID, PROBLEM_ID, USER_ID, LANGUAGE, CODE);

            // Assert
            assertNotNull(jobId);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<JudgeJob> captor = ArgumentCaptor.forClass(JudgeJob.class);
            verify(judgeQueue).add(captor.capture());

            JudgeJob savedJob = captor.getValue();
            assertNotNull(savedJob.getId());
            assertEquals(SUBMISSION_ID, savedJob.getSubmissionId());
            assertEquals(PROBLEM_ID, savedJob.getProblemId());
            assertEquals(USER_ID, savedJob.getUserId());
            assertEquals(LANGUAGE, savedJob.getLanguage());
            assertEquals(CODE, savedJob.getCode());
            assertEquals(QueueConstants.JobStatus.PENDING, savedJob.getStatus());
        }

        @Test
        @DisplayName("should generate UUID if not provided")
        void shouldGenerateUuidIfNotProvided() {
            // Arrange
            JudgeJob job = JudgeJob.builder()
                    .submissionId(SUBMISSION_ID)
                    .problemId(PROBLEM_ID)
                    .userId(USER_ID)
                    .language(LANGUAGE)
                    .code(CODE)
                    .build();
            when(judgeQueue.add(any())).thenReturn(true);

            // Act
            String jobId = queueService.enqueueJudgeJob(job);

            // Assert
            assertNotNull(jobId);
            assertEquals(jobId, job.getId());
        }

        @Test
        @DisplayName("should set createdAt if not provided")
        void shouldSetCreatedAtIfNotProvided() {
            // Arrange
            JudgeJob job = JudgeJob.builder()
                    .submissionId(SUBMISSION_ID)
                    .problemId(PROBLEM_ID)
                    .userId(USER_ID)
                    .language(LANGUAGE)
                    .code(CODE)
                    .build();
            when(judgeQueue.add(any())).thenReturn(true);

            // Act
            queueService.enqueueJudgeJob(job);

            // Assert
            @SuppressWarnings("unchecked")
            ArgumentCaptor<JudgeJob> captor = ArgumentCaptor.forClass(JudgeJob.class);
            verify(judgeQueue).add(captor.capture());
            assertNotNull(captor.getValue().getCreatedAt());
        }

        @Test
        @DisplayName("should set PENDING status")
        void shouldSetPendingStatus() {
            // Arrange
            JudgeJob job = JudgeJob.builder()
                    .submissionId(SUBMISSION_ID)
                    .problemId(PROBLEM_ID)
                    .userId(USER_ID)
                    .language(LANGUAGE)
                    .code(CODE)
                    .build();
            when(judgeQueue.add(any())).thenReturn(true);

            // Act
            queueService.enqueueJudgeJob(job);

            // Assert
            @SuppressWarnings("unchecked")
            ArgumentCaptor<JudgeJob> captor = ArgumentCaptor.forClass(JudgeJob.class);
            verify(judgeQueue).add(captor.capture());
            assertEquals(QueueConstants.JobStatus.PENDING, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("should save job status when tracking enabled")
        void shouldSaveJobStatusWhenTrackingEnabled() {
            // Arrange
            JudgeJob job = JudgeJob.builder()
                    .submissionId(SUBMISSION_ID)
                    .problemId(PROBLEM_ID)
                    .userId(USER_ID)
                    .language(LANGUAGE)
                    .code(CODE)
                    .build();
            when(judgeQueue.add(any())).thenReturn(true);

            // Act
            queueService.enqueueJudgeJob(job);

            // Assert
            verify(jobStatusRedisTemplate.opsForValue()).set(anyString(), any(JobStatusDTO.class), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("should not save job status when tracking disabled")
        void shouldNotSaveJobStatusWhenTrackingDisabled() {
            // Arrange
            ReflectionTestUtils.setField(queueConfig, "enableStatusTracking", false);
            JudgeJob job = JudgeJob.builder()
                    .submissionId(SUBMISSION_ID)
                    .problemId(PROBLEM_ID)
                    .userId(USER_ID)
                    .language(LANGUAGE)
                    .code(CODE)
                    .build();
            when(judgeQueue.add(any())).thenReturn(true);

            // Act
            queueService.enqueueJudgeJob(job);

            // Assert
            verify(jobStatusRedisTemplate.opsForValue(), never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("should throw exception when add fails")
        void shouldThrowExceptionWhenAddFails() {
            // Arrange
            when(judgeQueue.add(any())).thenReturn(false);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueService.enqueueJudgeJob(
                            SUBMISSION_ID, PROBLEM_ID, USER_ID, LANGUAGE, CODE));
            assertEquals(ErrorCode.QUEUE_OPERATION_FAILED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("enqueueJob Tests")
    class EnqueueJobTests {

        @Test
        @DisplayName("should enqueue job to judge queue")
        void shouldEnqueueJobToJudgeQueue() {
            // Arrange
            JobRequestDTO request = JobRequestDTO.builder()
                    .jobType("JUDGE")
                    .payload(Map.of("testKey", "testValue"))
                    .build();
            when(judgeQueue.add(any())).thenReturn(true);

            // Act
            String jobId = queueService.enqueueJob(QueueConstants.JUDGE_QUEUE, request);

            // Assert
            assertNotNull(jobId);
        }

        @Test
        @DisplayName("should enqueue job to email queue")
        void shouldEnqueueJobToEmailQueue() {
            // Arrange
            JobRequestDTO request = JobRequestDTO.builder()
                    .jobType("EMAIL")
                    .payload(Map.of("to", "test@example.com"))
                    .build();
            when(emailQueue.add(any())).thenReturn(true);

            // Act
            String jobId = queueService.enqueueJob(QueueConstants.EMAIL_QUEUE, request);

            // Assert
            assertNotNull(jobId);
        }

        @Test
        @DisplayName("should enqueue job to notification queue")
        void shouldEnqueueJobToNotificationQueue() {
            // Arrange
            JobRequestDTO request = JobRequestDTO.builder()
                    .jobType("NOTIFICATION")
                    .payload(Map.of("userId", USER_ID))
                    .build();
            when(notificationQueue.add(any())).thenReturn(true);

            // Act
            String jobId = queueService.enqueueJob(QueueConstants.NOTIFICATION_QUEUE, request);

            // Assert
            assertNotNull(jobId);
        }

        @Test
        @DisplayName("should throw exception for unknown queue")
        void shouldThrowExceptionForUnknownQueue() {
            // Arrange
            JobRequestDTO request = JobRequestDTO.builder()
                    .jobType("UNKNOWN")
                    .payload(Map.of("testKey", "testValue"))
                    .build();

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueService.enqueueJob("unknown_queue", request));
            assertEquals(ErrorCode.QUEUE_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("should throw exception when add fails")
        void shouldThrowExceptionWhenAddFails() {
            // Arrange
            JobRequestDTO request = JobRequestDTO.builder()
                    .jobType("JUDGE")
                    .payload(Map.of("testKey", "testValue"))
                    .build();
            when(judgeQueue.add(any())).thenReturn(false);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueService.enqueueJob(QueueConstants.JUDGE_QUEUE, request));
            assertEquals(ErrorCode.QUEUE_OPERATION_FAILED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("cancelJob Tests")
    class CancelJobTests {

        @Test
        @DisplayName("should cancel job when found and pending")
        void shouldCancelJobWhenFoundAndPending() {
            // Arrange
            JobStatusDTO status = JobStatusDTO.builder()
                    .jobId(JOB_ID)
                    .status(QueueConstants.JobStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(queueInspector.getJobStatus(anyString())).thenReturn(status);

            // Act
            queueService.cancelJob(JOB_ID);

            // Assert
            verify(jobStatusRedisTemplate.opsForValue()).set(anyString(), any(JobStatusDTO.class), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("should throw exception when job not found")
        void shouldThrowExceptionWhenJobNotFound() {
            // Arrange
            when(queueInspector.getJobStatus(anyString()))
                    .thenThrow(new BusinessException(ErrorCode.QUEUE_JOB_NOT_FOUND, "Job not found: " + JOB_ID));

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueService.cancelJob(JOB_ID));
            assertEquals(ErrorCode.QUEUE_JOB_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("should throw exception when job is processing")
        void shouldThrowExceptionWhenJobIsProcessing() {
            // Arrange
            JobStatusDTO status = JobStatusDTO.builder()
                    .jobId(JOB_ID)
                    .status(QueueConstants.JobStatus.PROCESSING)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(queueInspector.getJobStatus(anyString())).thenReturn(status);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueService.cancelJob(JOB_ID));
            assertEquals(ErrorCode.QUEUE_OPERATION_FAILED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("retryJob Tests")
    class RetryJobTests {

        @Test
        @DisplayName("should throw exception when job not found")
        void shouldThrowExceptionWhenJobNotFound() {
            // Arrange
            when(queueInspector.getJobStatus(anyString()))
                    .thenThrow(new BusinessException(ErrorCode.QUEUE_JOB_NOT_FOUND, "Job not found: " + JOB_ID));

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueService.retryJob(JOB_ID));
            assertEquals(ErrorCode.QUEUE_JOB_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("should throw exception when job is not failed")
        void shouldThrowExceptionWhenJobIsNotFailed() {
            // Arrange
            JobStatusDTO status = JobStatusDTO.builder()
                    .jobId(JOB_ID)
                    .status(QueueConstants.JobStatus.PENDING)
                    .queueName(QueueConstants.JUDGE_QUEUE)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(queueInspector.getJobStatus(anyString())).thenReturn(status);

            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueService.retryJob(JOB_ID));
            assertEquals(ErrorCode.QUEUE_OPERATION_FAILED, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("pollJob Tests")
    class PollJobTests {

        @Test
        @DisplayName("should return null when queue is empty")
        void shouldReturnNullWhenQueueIsEmpty() {
            // Arrange
            when(judgeQueue.poll()).thenReturn(null);

            // Act
            Object result = queueService.pollJob(QueueConstants.JUDGE_QUEUE);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("should return job when queue has items")
        void shouldReturnJobWhenQueueHasItems() {
            // Arrange
            JudgeJob job = JudgeJob.builder()
                    .id(JOB_ID)
                    .submissionId(SUBMISSION_ID)
                    .build();
            when(judgeQueue.poll()).thenReturn(job);
            when(queueInspector.getJobStatus(anyString())).thenReturn(
                    JobStatusDTO.builder()
                            .jobId(JOB_ID)
                            .status(QueueConstants.JobStatus.PENDING)
                            .build()
            );

            // Act
            Object result = queueService.pollJob(QueueConstants.JUDGE_QUEUE);

            // Assert
            assertNotNull(result);
            assertTrue(result instanceof JudgeJob);
            assertEquals(JOB_ID, ((JudgeJob) result).getId());
        }

        @Test
        @DisplayName("should throw exception for unknown queue")
        void shouldThrowExceptionForUnknownQueue() {
            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueService.pollJob("unknown_queue"));
            assertEquals(ErrorCode.QUEUE_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("clearQueue Tests")
    class ClearQueueTests {

        @Test
        @DisplayName("should clear queue")
        void shouldClearQueue() {
            // Act
            queueService.clearQueue(QueueConstants.JUDGE_QUEUE);

            // Assert
            verify(judgeQueue).clear();
        }

        @Test
        @DisplayName("should throw exception for unknown queue")
        void shouldThrowExceptionForUnknownQueue() {
            // Act & Assert
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> queueService.clearQueue("unknown_queue"));
            assertEquals(ErrorCode.QUEUE_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("updateJobStatus Tests")
    class UpdateJobStatusTests {

        @Test
        @DisplayName("should update job status to PROCESSING")
        void shouldUpdateJobStatusToProcessing() {
            // Arrange
            JobStatusDTO status = JobStatusDTO.builder()
                    .jobId(JOB_ID)
                    .status(QueueConstants.JobStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(queueInspector.getJobStatus(anyString())).thenReturn(status);

            // Act
            queueService.updateJobStatus(JOB_ID, QueueConstants.JobStatus.PROCESSING.name(), null);

            // Assert
            verify(jobStatusRedisTemplate.opsForValue()).set(anyString(), any(JobStatusDTO.class), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("should update job status to COMPLETED with error message")
        void shouldUpdateJobStatusToCompletedWithError() {
            // Arrange
            JobStatusDTO status = JobStatusDTO.builder()
                    .jobId(JOB_ID)
                    .status(QueueConstants.JobStatus.PROCESSING)
                    .startedAt(LocalDateTime.now().minusMinutes(1))
                    .createdAt(LocalDateTime.now().minusMinutes(2))
                    .build();
            when(queueInspector.getJobStatus(anyString())).thenReturn(status);

            // Act
            queueService.updateJobStatus(JOB_ID, QueueConstants.JobStatus.COMPLETED.name(), null);

            // Assert
            verify(jobStatusRedisTemplate.opsForValue()).set(anyString(), any(JobStatusDTO.class), anyLong(), any(TimeUnit.class));
        }
    }
}

