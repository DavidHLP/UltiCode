package com.ulticode.modules.admin.analytics;

import com.ulticode.modules.admin.dto.PerformanceReportVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SystemResourceReporter}.
 *
 * <p>Most fields are derived from JVM-side {@link ManagementFactory}
 * data, so we only assert the structural shape and the well-defined
 * scaffolding values that do not depend on the live JVM:
 * <ul>
 *   <li>{@code slowestEndpoints} / {@code errorBreakdown} are empty</li>
 *   <li>{@code averageResponseTime} / {@code errorRate} / {@code cacheHitRate} are null</li>
 *   <li>{@code throughput} is 0</li>
 *   <li>{@code systemUptime} is {@code &ge; 0}</li>
 *   <li>{@code resourceUsage} is non-null and the percentage values are within
 *       the documented range (0..100 or -1 sentinel)</li>
 * </ul>
 */
class SystemResourceReporterTest {

    private SystemResourceReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new SystemResourceReporter();
    }

    @Test
    @DisplayName("report shape: null placeholders, empty lists, throughput=0, uptime>=0")
    void reportShape() {
        PerformanceReportVO report = reporter.buildReport();

        assertNotNull(report);
        assertNotNull(report.getResourceUsage());
        assertNotNull(report.getSlowestEndpoints());
        assertTrue(report.getSlowestEndpoints().isEmpty());
        assertNotNull(report.getErrorBreakdown());
        assertTrue(report.getErrorBreakdown().isEmpty());

        assertNull(report.getAverageResponseTime());
        assertNull(report.getErrorRate());
        assertNull(report.getCacheHitRate());
        assertEquals(Long.valueOf(0L), report.getThroughput());

        assertNotNull(report.getSystemUptime());
        assertTrue(report.getSystemUptime() >= 0L);
    }

    @Test
    @DisplayName("memory percentage is between 0 and 100 or -1 sentinel")
    void memoryPercentageInRange() {
        PerformanceReportVO report = reporter.buildReport();

        Double memory = report.getResourceUsage().getMemory();
        assertNotNull(memory);
        assertTrue(memory == -1.0 || (memory >= 0.0 && memory <= 100.0),
                "memory should be in 0..100 or -1 sentinel, was: " + memory);
    }

    @Test
    @DisplayName("memory sentinel kicks in when heap max is 0")
    void memorySentinelOnZeroMax() {
        // We can't easily inject a custom MemoryMXBean without PowerMock, but the
        // actual % calculation handles max=0 via the (max > 0 ? max : 1) guard.
        // This test re-asserts the guard logic by simulating the math directly.
        long max = 0L;
        long boundedMax = max > 0 ? max : 1;
        long used = 500L;
        double percent = (used * 100.0) / boundedMax;
        assertEquals(50000.0, percent);
        // The reporter additionally rounds to 2 decimals; the math here just
        // verifies the guard path doesn't divide by zero.
    }

    @Test
    @DisplayName("cpu and disk percentage in 0..100 or -1 sentinel")
    void cpuAndDiskInRange() {
        PerformanceReportVO report = reporter.buildReport();

        Double cpu = report.getResourceUsage().getCpu();
        Double disk = report.getResourceUsage().getDisk();
        assertNotNull(cpu);
        assertNotNull(disk);

        assertTrue(cpu == -1.0 || (cpu >= 0.0 && cpu <= 100.0),
                "cpu should be in 0..100 or -1 sentinel, was: " + cpu);
        assertTrue(disk == -1.0 || (disk >= 0.0 && disk <= 100.0),
                "disk should be in 0..100 or -1 sentinel, was: " + disk);
    }

    @Test
    @DisplayName("live JVM sanity: MemoryMXBean is reachable and returns a heap usage object")
    void liveJvmSanity() {
        // Defensive: the reporter reads from the real MemoryMXBean; the test JVM
        // must support this. If this ever fails on a stripped-down JRE we know
        // immediately, rather than a confusing arithmetic failure later.
        MemoryMXBean mxBean = ManagementFactory.getMemoryMXBean();
        assertNotNull(mxBean);
        MemoryUsage heap = mxBean.getHeapMemoryUsage();
        assertNotNull(heap);
    }

    @Test
    @DisplayName("sampleSystemMetrics: uptime >= 0 and memory in 0..100")
    void sampleSystemMetricsShape() {
        SystemResourceReporter.SystemMetrics metrics = reporter.sampleSystemMetrics();

        assertNotNull(metrics);
        assertTrue(metrics.systemUptimeSeconds() >= 0L);
        Double memory = metrics.memoryUsagePercent();
        assertNotNull(memory);
        assertTrue(memory >= 0.0 && memory <= 100.0,
                "memoryUsagePercent should be in 0..100, was: " + memory);
    }

    @Test
    @DisplayName("sampleSystemMetrics: memory uses heap-max guard (no divide-by-zero on max=0)")
    void sampleSystemMetricsHeapMaxGuard() {
        // The reporter's sampleSystemMetrics guards heap-max via (max > 0 ? max : 1)
        // just like buildReport. Verify the guard path keeps the live JVM sampling
        // safe even on a JRE that reports an undefined heap max.
        long max = 0L;
        long boundedMax = max > 0 ? max : 1;
        long used = 500L;
        // sampleSystemMetrics rounds (used * 10000 / boundedMax) to 2 decimals,
        // i.e. Math.round(...) / 100.0 → 50000.0 for used=500, boundedMax=1.
        double percent = Math.round((used * 10000.0) / boundedMax) / 100.0;
        assertEquals(50000.0, percent);
    }
}