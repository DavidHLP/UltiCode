package com.ulticode.modules.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Database statistics VO for monitoring endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseStatsVO {
    /**
     * Number of active database connections.
     */
    private Integer activeConnections;

    /**
     * Maximum allowed connections.
     */
    private Integer maxConnections;

    /**
     * Total query count since startup.
     */
    private Long queryCount;

    /**
     * Number of slow queries detected since process start.
     * Stored as {@code long} to avoid overflow on long-running processes
     * (a busy OJ can easily produce > 2 billion slow queries in a year).
     */
    private Long slowQueries;

    /**
     * Database connection status.
     */
    private String status;
}
