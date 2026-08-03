package com.ulticode.common.metrics;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide metrics counters shared between MyBatis interceptors
 * (producers) and {@code MonitoringInspector} (reader).
 *
 * <p>Decouples the SQL execution chain from the monitoring service to
 * avoid circular dependencies and keep each side independently testable.
 *
 * <p>All counters are stored in {@link AtomicLong} instances and are
 * safe to read and increment concurrently from any thread.
 *
 * @author UltiCode
 * @since 1.0.0
 */
@Component
public class MetricsCollector {

    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong slowQueryCount = new AtomicLong(0);

    /**
     * Increment the total SQL execution counter by 1.
     * <p>Thread-safe; uses {@link AtomicLong} under the hood.
     */
    public void incrementQuery() {
        queryCount.incrementAndGet();
    }

    /**
     * Increment the slow-query counter by 1. Called by the interceptor
     * when a query exceeds the configured threshold.
     * <p>Thread-safe; uses {@link AtomicLong} under the hood.
     */
    public void incrementSlowQuery() {
        slowQueryCount.incrementAndGet();
    }

    /**
     * @return cumulative SQL executions since process start
     */
    public long getQueryCount() {
        return queryCount.get();
    }

    /**
     * @return cumulative slow-query executions since process start
     */
    public long getSlowQueryCount() {
        return slowQueryCount.get();
    }
}
