package com.ulticode.modules.queue.dto;

import com.ulticode.modules.queue.constants.QueueConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Job status DTO.
 * Contains the current status and progress of a job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique job identifier.
     */
    private String jobId;

    /**
     * Job type.
     */
    private String jobType;

    /**
     * Queue name.
     */
    private String queueName;

    /**
     * Current status.
     */
    private QueueConstants.JobStatus status;

    /**
     * Job priority.
     */
    private QueueConstants.Priority priority;

    /**
     * Progress percentage (0-100).
     */
    @Builder.Default
    private int progress = 0;

    /**
     * Current step description.
     */
    private String currentStep;

    /**
     * Number of attempts made.
     */
    @Builder.Default
    private int attempts = 0;

    /**
     * Maximum retry attempts.
     */
    @Builder.Default
    private int maxRetries = QueueConstants.DEFAULT_MAX_RETRIES;

    /**
     * Error message if failed.
     */
    private String error;

    /**
     * Result data if completed.
     */
    private Map<String, Object> result;

    /**
     * Time when job was created.
     */
    private LocalDateTime createdAt;

    /**
     * Time when job started processing.
     */
    private LocalDateTime startedAt;

    /**
     * Time when job completed.
     */
    private LocalDateTime completedAt;

    /**
     * Processing duration in milliseconds.
     */
    private Long durationMs;

    /**
     * User ID that created this job.
     */
    private String createdBy;

    /**
     * User ID for judge jobs.
     */
    private String userId;

    /**
     * Original payload.
     */
    private Map<String, Object> payload;
}
