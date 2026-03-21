package com.ulticode.modules.queue.dto;

import com.ulticode.modules.queue.constants.QueueConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Generic job request DTO.
 * Contains the data needed to enqueue a job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Job type identifier.
     */
    private String jobType;

    /**
     * Job priority.
     */
    @Builder.Default
    private QueueConstants.Priority priority = QueueConstants.Priority.MEDIUM;

    /**
     * Maximum retry attempts.
     */
    @Builder.Default
    private int maxRetries = QueueConstants.DEFAULT_MAX_RETRIES;

    /**
     * Job timeout in seconds.
     */
    @Builder.Default
    private int timeoutSeconds = QueueConstants.DEFAULT_JOB_TIMEOUT_SECONDS;

    /**
     * Delay before processing in milliseconds.
     */
    @Builder.Default
    private long delayMs = 0;

    /**
     * Job payload data.
     */
    private Map<String, Object> payload;

    /**
     * User ID that created this job.
     */
    private String createdBy;

    /**
     * Additional metadata.
     */
    private Map<String, String> metadata;
}
