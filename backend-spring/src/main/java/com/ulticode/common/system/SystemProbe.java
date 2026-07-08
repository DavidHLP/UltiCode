package com.ulticode.common.system;

/**
 * Read-only seam for JVM-level signals the rest of the backend used to
 * grab by calling {@code Runtime.getRuntime().availableProcessors()} or
 * casting {@code com.sun.management.OperatingSystemMXBean} off
 * {@code ManagementFactory.getOperatingSystemMXBean()}.
 *
 * <p>Both patterns are test-hostile:
 * <ul>
 *   <li>{@code Runtime.getRuntime()} is a static factory; tests cannot
 *       substitute a deterministic value.</li>
 *   <li>The {@code com.sun.management} extension interface is HotSpot-specific;
 *       a unit test running on a non-HotSpot JVM cannot exercise the
 *       {@code getProcessCpuLoad()} / {@code getCpuLoad()} paths at all.</li>
 * </ul>
 *
 * <p>The probe exposes the three signals any caller needs (CPU core count,
 * process CPU load, system CPU load). Returns {@code -1.0} when the
 * underlying signal is unavailable so the caller can render a "metric
 * unavailable" indicator in the dashboard without a sentinel object.
 *
 * <p><strong>Deep-module shape (architecture review 2026-07-08).</strong>
 * Two adapters justify the seam per the architecture glossary: a
 * {@link JvmSystemProbe} for production and a test-only
 * {@code FakeSystemProbe} constructed in the unit tests. Mirrors the
 * {@link com.ulticode.common.ratelimiter.RateLimiter} pattern
 * (RedisRateLimiter + InMemoryRateLimiter).
 *
 * <p>Callers: {@code monitoring} module (resource usage, health probes).
 */
public interface SystemProbe {

    /**
     * Number of processors available to the JVM. Always at least 1 in any
     * real runtime; the value comes from
     * {@code OperatingSystemMXBean.getAvailableProcessors()} with a
     * {@code Runtime.getRuntime().availableProcessors()} fallback.
     */
    int availableProcessors();

    /**
     * Recent CPU usage of the JVM process in {@code [0.0, 1.0]}.
     *
     * @return the load, or {@code -1.0} when the host JVM does not expose
     *         the {@code com.sun.management} extension
     */
    double processCpuLoad();

    /**
     * Recent CPU usage of the whole system in {@code [0.0, 1.0]}.
     *
     * @return the load, or {@code -1.0} when the host JVM does not expose
     *         the {@code com.sun.management} extension
     */
    double systemCpuLoad();
}
