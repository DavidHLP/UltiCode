package com.ulticode.modules.admin.service.impl;

import com.sun.management.OperatingSystemMXBean;
import com.ulticode.modules.admin.dto.PerformanceReportVO;
import com.ulticode.modules.admin.service.AdminPerformanceReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Collections;

/**
 * Implementation of AdminPerformanceReportService.
 * Provides JVM metrics and system resource usage data using JDK ManagementFactory APIs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPerformanceReportServiceImpl implements AdminPerformanceReportService {

    @Override
    public PerformanceReportVO getPerformanceReport() {
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

        // Performance metrics - these require external monitoring (APM tool); -1 indicates unavailable
        report.setAverageResponseTime(-1.0);
        report.setErrorRate(-1.0);
        report.setThroughput(-1L);
        report.setCacheHitRate(-1.0);

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
                return -1.0;
            }
            return Math.round(load * 10000.0) / 100.0;
        } catch (Exception e) {
            log.warn("Failed to read system CPU load", e);
            return -1.0;
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
                return -1.0;
            }
            long used = total - root.getFreeSpace();
            return Math.round(used * 10000.0 / total) / 100.0;
        } catch (Exception e) {
            log.warn("Failed to read root disk usage", e);
            return -1.0;
        }
    }
}
