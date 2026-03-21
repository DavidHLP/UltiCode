package com.ulticode.modules.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis statistics VO for monitoring endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisStatsVO {
    /**
     * Whether Redis is connected.
     */
    private Boolean connected;

    /**
     * Redis server version.
     */
    private String version;

    /**
     * Memory used by Redis in bytes.
     */
    private Long usedMemory;

    /**
     * Number of connected clients.
     */
    private Integer connectedClients;

    /**
     * Total number of keys.
     */
    private Long totalKeys;

    /**
     * Uptime in seconds.
     */
    private Long uptimeInSeconds;
}
