package com.ulticode.common.tracing;

import java.io.Serializable;

/**
 * Trace metadata captured at the producer, propagated unchanged through
 * cross-service calls (HTTP, Dubbo, queue events), and surfaced to the
 * client.
 *
 * <p>Three orthogonal fields:
 * <ul>
 *   <li>{@link #traceId} &mdash; the end-to-end correlation id, generated
 *       once at the system entry point (HTTP filter, message producer).
 *       Format is provider-defined; the project's current convention is
 *       {@code "t-<epochMillis>"} via {@code TraceIdUtil.current()}.</li>
 *   <li>{@link #spanId} &mdash; per-hop id when the runtime implements
 *       span-based tracing. {@code null} when the deployment has not yet
 *       wired a span reporter.</li>
 *   <li>{@link #parentSpanId} &mdash; optional, populated only when this
 *       hop descends from a known parent. Useful for tree rebuilding in
 *       incident review.</li>
 *   <li>{@link #deadlineMs} &mdash; absolute deadline epoch-millis; the
 *       downstream may reject invocations past this instant rather than
 *       silently wait.</li>
 * </ul>
 *
 * <p>This record is the wire-safe shape that backend-common contributes to
 * the RPC envelope ({@code RpcResult.deadlineMs}) and that the eventual
 * W3C trace-context migration can drop in without touching call sites.
 *
 * <p>The static {@code TraceIdUtil} generator remains the project's
 * default end-to-end trace-id source until Phase 1 enables full W3C trace
 * context across HTTP, Dubbo and event flows. See MICROSERVICE_MIGRATION_GUIDE §2.7.
 */
public record TraceMetadata(
        String traceId,
        String spanId,
        String parentSpanId,
        Long deadlineMs) implements Serializable {

    /**
     * @return true when {@link #traceId} is non-null and non-blank.
     */
    public boolean hasTraceId() {
        return traceId != null && !traceId.isBlank();
    }

    /**
     * Project a deadline epoch-millis from a ttlMillis relative to now.
     *
     * @param nowMillis baseline epoch-millis
     * @param ttlMillis time-to-live added to the baseline
     * @return a new record with {@link #deadlineMs} set; other fields
     *         preserved
     */
    public TraceMetadata withDeadline(long nowMillis, long ttlMillis) {
        return new TraceMetadata(traceId, spanId, parentSpanId, nowMillis + ttlMillis);
    }

    /** Empty placeholder record (all fields null); "no metadata". */
    public static final TraceMetadata EMPTY = new TraceMetadata(null, null, null, null);
}
