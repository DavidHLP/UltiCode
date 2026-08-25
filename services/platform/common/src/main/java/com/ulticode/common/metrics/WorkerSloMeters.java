package com.ulticode.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared queue/consumer SLO meter set for Redis Streams workers (review
 * 2026-08-25 P1 "分布式 tracing 和 Worker SLO 尚未贯通").
 *
 * <p>Registers one gauge set per worker process, namespaced by a metric
 * prefix consistent with the repo's existing dotted metric style (e.g.
 * {@code search.worker}, {@code judge.streams}, {@code notification.inbox}):
 * <ul>
 *   <li>{@code <prefix>.queue.lag} &mdash; stream entries not yet delivered to
 *       the group ({@code XINFO GROUPS lag}; {@link #UNKNOWN} when the broker
 *       cannot answer, e.g. pre-7.0 Redis or trimmed history);</li>
 *   <li>{@code <prefix>.queue.pel.size} &mdash; pending-entries-list size;</li>
 *   <li>{@code <prefix>.queue.pel.oldest.age.seconds} &mdash; age of the oldest
 *       PEL entry since its last delivery;</li>
 *   <li>{@code <prefix>.queue.dlq.size} &mdash; dead-letter depth (workers
 *       without a stream DLQ never update it and keep {@link #UNKNOWN});</li>
 *   <li>{@code <prefix>.last.success.timestamp} &mdash; epoch millis of the last
 *       successful consume cycle or ack (0 = never);</li>
 *   <li>{@code <prefix>.consume.failures} &mdash; consume/ack failure counter.</li>
 * </ul>
 *
 * <p>The class is deliberately plain Java over Micrometer only: no Spring
 * annotations and no Redis client dependency, so it satisfies the
 * backend-common architecture gate while each worker feeds it from its own
 * client (spring-data-redis for Search/Notification, Redisson for Judge).
 * Gauges are backed by mutable holders that the worker's existing poll /
 * heartbeat / claim loop refreshes; values are never reset by this class.
 */
public final class WorkerSloMeters {

    /** Sentinel for "this cycle could not observe the value" / not applicable. */
    public static final long UNKNOWN = -1L;

    /**
     * Per-(registry, prefix) instances. Micrometer gauges are bound to the
     * holder object captured at first registration, so a naive second
     * {@code register} would silently write to detached holders; the cache
     * makes re-registration genuinely idempotent (e.g. tests or multiple
     * beans sharing one registry).
     */
    private static final java.util.Map<MeterRegistry, java.util.Map<String, WorkerSloMeters>> REGISTERED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private final AtomicLong queueLag = new AtomicLong(UNKNOWN);
    private final AtomicLong pelSize = new AtomicLong(UNKNOWN);
    private final AtomicLong pelOldestAgeSeconds = new AtomicLong(UNKNOWN);
    private final AtomicLong dlqSize = new AtomicLong(UNKNOWN);
    private final AtomicLong lastSuccessEpochMs = new AtomicLong(0L);
    private final Counter consumeFailures;

    private WorkerSloMeters(MeterRegistry registry, String prefix) {
        gauge(registry, prefix, "queue.lag", queueLag);
        gauge(registry, prefix, "queue.pel.size", pelSize);
        gauge(registry, prefix, "queue.pel.oldest.age.seconds", pelOldestAgeSeconds);
        gauge(registry, prefix, "queue.dlq.size", dlqSize);
        gauge(registry, prefix, "last.success.timestamp", lastSuccessEpochMs);
        this.consumeFailures = Counter.builder(prefix + ".consume.failures")
                .description("Consume/ack cycles or records that failed and will be retried")
                .register(registry);
    }

    private static void gauge(MeterRegistry registry, String prefix, String name, AtomicLong holder) {
        registry.gauge(prefix + "." + name, holder, AtomicLong::doubleValue);
    }

    /**
     * Register the meter set on {@code registry} under {@code prefix}
     * (e.g. {@code "search.worker"}). Idempotent: re-registering the same
     * (registry, prefix) pair returns the already-wired instance.
     */
    public static WorkerSloMeters register(MeterRegistry registry, String prefix) {
        return REGISTERED
                .computeIfAbsent(registry, r -> new java.util.concurrent.ConcurrentHashMap<>())
                .computeIfAbsent(prefix, p -> new WorkerSloMeters(registry, prefix));
    }

    public void setQueueLag(long value) {
        queueLag.set(value);
    }

    public void setPelSize(long value) {
        pelSize.set(value);
    }

    public void setPelOldestAgeSeconds(long value) {
        pelOldestAgeSeconds.set(value);
    }

    public void setDlqSize(long value) {
        dlqSize.set(value);
    }

    /** Record a fully successful consume cycle / acknowledged job at now. */
    public void markSuccess() {
        lastSuccessEpochMs.set(System.currentTimeMillis());
    }

    /** Count a failed record processing, read, reclaim, or ack attempt. */
    public void incrementFailures() {
        consumeFailures.increment();
    }

    public long getQueueLag() {
        return queueLag.get();
    }

    public long getPelSize() {
        return pelSize.get();
    }

    public long getPelOldestAgeSeconds() {
        return pelOldestAgeSeconds.get();
    }

    public long getDlqSize() {
        return dlqSize.get();
    }

    public long getLastSuccessEpochMs() {
        return lastSuccessEpochMs.get();
    }
}
