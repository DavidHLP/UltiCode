package com.ulticode.modules.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * System health check VO for monitoring endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthVO {
    /**
     * Overall system status: healthy, degraded, unhealthy.
     */
    private String status;

    /**
     * Individual service health checks.
     */
    private List<HealthCheck> checks;

    /**
     * Timestamp of the health check.
     */
    private Instant timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthCheck {
        /**
         * Service name.
         */
        private String service;

        /**
         * Service status: healthy, degraded, unhealthy.
         */
        private String status;

        /**
         * Response latency in milliseconds.
         */
        private Long latency;

        /**
         * Additional status message.
         */
        private String message;
    }
}
