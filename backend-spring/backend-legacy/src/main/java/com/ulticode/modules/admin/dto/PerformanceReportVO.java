package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Performance Report View Object.
 * Contains system performance metrics and resource usage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PerformanceReportVO {

    /**
     * Resource usage statistics.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceUsage {
        private Double cpu;     // CPU usage (%)
        private Double memory;  // Memory usage (%)
        private Double disk;    // Disk usage (%)
    }

    /**
     * Slow endpoint data point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlowEndpoint {
        private String endpoint;
        private Double averageTime;  // Average response time (ms)
        private Integer requestCount;
    }

    /**
     * Error breakdown data point.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorBreakdown {
        private String errorType;
        private Integer count;
        private Double percentage;
    }

    /**
     * System uptime in seconds.
     */
    private Long systemUptime;

    /**
     * Average response time (ms).
     */
    private Double averageResponseTime;

    /**
     * Error rate (%).
     */
    private Double errorRate;

    /**
     * Request throughput (requests per 24h).
     */
    private Long throughput;

    /**
     * Resource usage statistics.
     */
    private ResourceUsage resourceUsage;

    /**
     * Slowest endpoints.
     */
    private List<SlowEndpoint> slowestEndpoints;

    /**
     * Error breakdown by type.
     */
    private List<ErrorBreakdown> errorBreakdown;

    /**
     * Cache hit rate (%).
     */
    private Double cacheHitRate;
}
