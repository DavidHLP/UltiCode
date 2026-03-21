package com.ulticode.modules.queue.constants;

import com.ulticode.modules.queue.constants.QueueConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueueConstants.
 */
class QueueConstantsTest {

    @Nested
    @DisplayName("Queue Name Constants")
    class QueueNameTests {

        @Test
        @DisplayName("should have correct judge queue name")
        void shouldHaveCorrectJudgeQueueName() {
            assertEquals("judge_queue", QueueConstants.JUDGE_QUEUE);
        }

        @Test
        @DisplayName("should have correct email queue name")
        void shouldHaveCorrectEmailQueueName() {
            assertEquals("email_queue", QueueConstants.EMAIL_QUEUE);
        }

        @Test
        @DisplayName("should have correct notification queue name")
        void shouldHaveCorrectNotificationQueueName() {
            assertEquals("notification_queue", QueueConstants.NOTIFICATION_QUEUE);
        }
    }

    @Nested
    @DisplayName("Default Values")
    class DefaultValueTests {

        @Test
        @DisplayName("should have correct default job timeout")
        void shouldHaveCorrectDefaultJobTimeout() {
            assertEquals(3600, QueueConstants.DEFAULT_JOB_TIMEOUT_SECONDS);
        }

        @Test
        @DisplayName("should have correct default max retries")
        void shouldHaveCorrectDefaultMaxRetries() {
            assertEquals(3, QueueConstants.DEFAULT_MAX_RETRIES);
        }

        @Test
        @DisplayName("should have correct default retry delay")
        void shouldHaveCorrectDefaultRetryDelay() {
            assertEquals(5000, QueueConstants.DEFAULT_RETRY_DELAY_MS);
        }
    }

    @Nested
    @DisplayName("Priority Enum")
    class PriorityTests {

        @Test
        @DisplayName("should have HIGH priority with value 1")
        void shouldHaveHighPriorityWithValue1() {
            assertEquals(1, QueueConstants.Priority.HIGH.getValue());
        }

        @Test
        @DisplayName("should have MEDIUM priority with value 5")
        void shouldHaveMediumPriorityWithValue5() {
            assertEquals(5, QueueConstants.Priority.MEDIUM.getValue());
        }

        @Test
        @DisplayName("should have LOW priority with value 10")
        void shouldHaveLowPriorityWithValue10() {
            assertEquals(10, QueueConstants.Priority.LOW.getValue());
        }

        @Test
        @DisplayName("should have 3 priority levels")
        void shouldHave3PriorityLevels() {
            assertEquals(3, QueueConstants.Priority.values().length);
        }
    }

    @Nested
    @DisplayName("JobStatus Enum")
    class JobStatusTests {

        @Test
        @DisplayName("should have PENDING status")
        void shouldHavePendingStatus() {
            assertNotNull(QueueConstants.JobStatus.PENDING);
        }

        @Test
        @DisplayName("should have PROCESSING status")
        void shouldHaveProcessingStatus() {
            assertNotNull(QueueConstants.JobStatus.PROCESSING);
        }

        @Test
        @DisplayName("should have COMPLETED status")
        void shouldHaveCompletedStatus() {
            assertNotNull(QueueConstants.JobStatus.COMPLETED);
        }

        @Test
        @DisplayName("should have FAILED status")
        void shouldHaveFailedStatus() {
            assertNotNull(QueueConstants.JobStatus.FAILED);
        }

        @Test
        @DisplayName("should have CANCELLED status")
        void shouldHaveCancelledStatus() {
            assertNotNull(QueueConstants.JobStatus.CANCELLED);
        }

        @Test
        @DisplayName("should have 5 status values")
        void shouldHave5StatusValues() {
            assertEquals(5, QueueConstants.JobStatus.values().length);
        }
    }

    @Nested
    @DisplayName("Key Prefixes")
    class KeyPrefixTests {

        @Test
        @DisplayName("should have correct job status prefix")
        void shouldHaveCorrectJobStatusPrefix() {
            assertEquals("queue:job:", QueueConstants.JOB_STATUS_PREFIX);
        }

        @Test
        @DisplayName("should have correct job list prefix")
        void shouldHaveCorrectJobListPrefix() {
            assertEquals("queue:jobs:", QueueConstants.JOB_LIST_PREFIX);
        }
    }
}
