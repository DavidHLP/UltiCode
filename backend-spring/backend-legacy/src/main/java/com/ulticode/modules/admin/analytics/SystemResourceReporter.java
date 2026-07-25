package com.ulticode.modules.admin.analytics;

import com.sun.management.OperatingSystemMXBean;
import com.ulticode.modules.admin.dto.PerformanceReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Collections;

/**
 * Builds the JVM/OS resource section of
 * {@link PerformanceReportVO}: uptime, heap-memory %, recent system CPU
 * load, and root-filesystem usage. Returns {@code -1.0} for the three
 * percentages when the underlying JVM cannot produce a sample (matches
 * the historical inline behaviour).
 *
 * <p>The application-level metrics that depend on external monitoring
 * (response time, throughput, cache hit rate, slowest endpoints, error
 * breakdown) remain null/empty in the assembled VO — they are outside
 * this reporter's scope and stay commented-out as they were in the
 * pre-refactor inline implementation.
 *
 * @author ulticode
 */
@Slf4j
@Component
public class SystemResourceReporter {

    /**
     * Sentinel returned by every percentage sample when the JVM cannot
     * produce a real reading. Matches the historical inline behaviour.
     */
    static final double UNSAMPLED = -1.0;

    /**
     * Raw JVM/OS samples consumed by the platform-overview endpoint.
     * Holds the same shape the inline implementation used to push
     * directly into the overview map.
     */
    public record SystemMetrics(long systemUptimeSeconds, double memoryUsagePercent) {
    }

    /**
     * Sample the JVM/OS metrics needed by the lightweight platform
     * overview. Centralised here so the orchestrator never touches
     * {@link ManagementFactory} directly.
     *
     * @return uptime in seconds and heap-memory usage as a percentage
     *         (0–100, 2 decimals; matches the historical overview
     *         contract)
     */
    public SystemMetrics sampleSystemMetrics() {
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long maxMemory = heapUsage.getMax() > 0 ? heapUsage.getMax() : 1;
        double memoryUsagePercent = Math.round(heapUsage.getUsed() * 10000.0 / maxMemory) / 100.0;
        return new SystemMetrics(uptimeSeconds, memoryUsagePercent);
    }

    /**
     * Build the performance report (JVM/OS resource + scaffolded
     * application-level placeholders).
     *
     * @return assembled report VO
     */
    public PerformanceReportVO buildReport() {
        PerformanceReportVO report = new PerformanceReportVO();

        // System uptime (JVM uptime)
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        report.setSystemUptime(uptimeMillis / 1000);

        // Resource usage
        PerformanceReportVO.ResourceUsage resourceUsage = new PerformanceReportVO.ResourceUsage();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long maxMemory = heapUsage.getMax() > 0 ? heapUsage.getMax() : 1;
        double memoryUsagePercent = (heapUsage.getUsed() * 100.0) / maxMemory;
        resourceUsage.setCpu(readSystemCpuLoad());
        resourceUsage.setMemory(Math.round(memoryUsagePercent * 100.0) / 100.0);
        resourceUsage.setDisk(readRootDiskUsagePercent());
        report.setResourceUsage(resourceUsage);

        // Performance metrics - these require external monitoring (APM tool).
        // Sentinel -1.0/-1L values were dropped in favor of null + @JsonInclude(NON_NULL).
        report.setAverageResponseTime(null);
        report.setErrorRate(null);
        report.setThroughput(0L);
        report.setCacheHitRate(null);

        // Slowest endpoints - requires request timing middleware
        report.setSlowestEndpoints(Collections.emptyList());

        // Error breakdown - requires error tracking integration
        report.setErrorBreakdown(Collections.emptyList());

        return report;
    }

    /**
     * Read recent system-wide CPU load (0.0–100.0).
     * Returns {@code -1.0} if the JVM has not yet collected enough samples
     * (the first call to {@link OperatingSystemMXBean#getSystemCpuLoad()}
     * typically returns -1) or if the value cannot be obtained on this JVM.
     */
    private double readSystemCpuLoad() {
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double load = osBean.getSystemCpuLoad();
            if (load < 0) {
                return UNSAMPLED;
            }
            return Math.round(load * 10000.0) / 100.0;
        } catch (Exception e) {
            log.warn("Failed to read system CPU load", e);
            return UNSAMPLED;
        }
    }

    /**
     * Read root filesystem usage as a percentage (0.0–100.0).
     * Returns {@code -1.0} if the underlying OS does not report filesystem stats.
     */
    private double readRootDiskUsagePercent() {
        try {
            File root = new File("/");
            long total = root.getTotalSpace();
            if (total <= 0) {
                return UNSAMPLED;
            }
            long used = total - root.getFreeSpace();
            return Math.round(used * 10000.0 / total) / 100.0;
        } catch (Exception e) {
            log.warn("Failed to read root disk usage", e);
            return UNSAMPLED;
        }
    }
}