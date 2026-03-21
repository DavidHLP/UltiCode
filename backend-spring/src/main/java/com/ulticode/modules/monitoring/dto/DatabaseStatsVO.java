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
     * Number of slow queries detected.
     */
    private Integer slowQueries;

    /**
     * Database connection status.
     */
    private String status;
}
