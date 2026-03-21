package com.ulticode.modules.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resource usage VO for monitoring endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUsageVO {
    /**
     * Memory usage information.
     */
    private MemoryInfo memory;

    /**
     * CPU usage information.
     */
    private CpuInfo cpu;

    /**
     * Current thread count.
     */
    private Integer threadCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryInfo {
        /**
         * Currently used heap memory in bytes.
         */
        private Long heapUsed;

        /**
         * Maximum heap memory in bytes.
         */
        private Long heapMax;

        /**
         * Non-heap memory used in bytes.
         */
        private Long nonHeapUsed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuInfo {
        /**
         * Process CPU load (0.0 to 1.0).
         */
        private Double processCpuLoad;

        /**
         * System CPU load (0.0 to 1.0).
         */
        private Double systemCpuLoad;

        /**
         * Number of available processors.
         */
        private Integer availableProcessors;
    }
}
