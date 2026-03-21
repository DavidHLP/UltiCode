package com.ulticode.modules.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Queue statistics VO for monitoring endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatsVO {
    /**
     * Queue name.
     */
    private String name;

    /**
     * Number of jobs waiting to be processed.
     */
    private Long waiting;

    /**
     * Number of jobs currently being processed.
     */
    private Long active;

    /**
     * Number of completed jobs.
     */
    private Long completed;

    /**
     * Number of failed jobs.
     */
    private Long failed;

    /**
     * Number of delayed jobs.
     */
    private Long delayed;
}
