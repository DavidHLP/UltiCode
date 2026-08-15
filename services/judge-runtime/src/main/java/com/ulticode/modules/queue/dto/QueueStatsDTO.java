package com.ulticode.modules.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Queue statistics DTO.
 * Contains statistics about a queue's current state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Queue name.
     */
    private String queueName;

    /**
     * Number of jobs waiting to be processed.
     */
    @Builder.Default
    private long waitingCount = 0;

    /**
     * Number of jobs currently being processed.
     */
    @Builder.Default
    private long activeCount = 0;

    /**
     * Number of completed jobs (kept in history).
     */
    @Builder.Default
    private long completedCount = 0;

    /**
     * Number of failed jobs.
     */
    @Builder.Default
    private long failedCount = 0;

    /**
     * Total number of jobs processed.
     */
    @Builder.Default
    private long totalProcessed = 0;

    /**
     * Average processing time in milliseconds.
     */
    @Builder.Default
    private long avgProcessingTimeMs = 0;

    /**
     * Timestamp of last job processed.
     */
    private Long lastProcessedAt;

    /**
     * Whether the queue is currently paused.
     */
    @Builder.Default
    private boolean paused = false;
}
