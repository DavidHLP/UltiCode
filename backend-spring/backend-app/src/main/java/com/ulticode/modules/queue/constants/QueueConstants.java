package com.ulticode.modules.queue.constants;

/**
 * Queue constants for the task queue system.
 * Defines queue names, priorities, and job status values.
 */
public final class QueueConstants {

    private QueueConstants() {
        // Prevent instantiation
    }

    // Queue names
    public static final String JUDGE_QUEUE = "judge_queue";
    public static final String EMAIL_QUEUE = "email_queue";
    public static final String NOTIFICATION_QUEUE = "notification_queue";

    // Job status keys prefix
    public static final String JOB_STATUS_PREFIX = "queue:job:";
    public static final String JOB_LIST_PREFIX = "queue:jobs:";

    // Default values
    public static final int DEFAULT_JOB_TIMEOUT_SECONDS = 3600; // 1 hour
    public static final int DEFAULT_MAX_RETRIES = 3;
    public static final int DEFAULT_RETRY_DELAY_MS = 5000; // 5 seconds

    /**
     * Job priority levels.
     */
    public enum Priority {
        HIGH(1),
        MEDIUM(5),
        LOW(10);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Job status values.
     */
    public enum JobStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
