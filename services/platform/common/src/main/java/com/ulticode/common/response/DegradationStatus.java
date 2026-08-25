package com.ulticode.common.response;

/**
 * Explicit degradation marker for reads that aggregate data from multiple
 * owners or caches (cross-owner RPC aggregation, event-built read models).
 *
 * <p>Carried on response payloads (for example as a nullable field on
 * {@link PageResult}) so that partial or unavailable upstream data is visible
 * to API consumers instead of being silently indistinguishable from a
 * business-empty result.
 *
 * <p>Additive by design: legacy payloads leave the marker {@code null}, which
 * consumers must treat as {@link #OK}.
 */
public enum DegradationStatus {

    /** All sources answered normally; the payload is complete. */
    OK,

    /**
     * At least one upstream source failed or was unreachable; the payload
     * contains only the data from the sources that answered. Fields sourced
     * from the failed owner may be null even for existing entities.
     */
    PARTIAL,

    /**
     * Data was served from an eventually-consistent read model that may lag
     * the write side. Reserved for event-driven read models; not produced by
     * synchronous cross-owner reads today.
     */
    STALE,

    /**
     * Every upstream source required to answer the query was unavailable.
     * A payload carrying this status MUST NOT be interpreted as a
     * business-empty result. Reads that cannot answer at all should prefer
     * throwing a typed 503 exception over returning an empty payload.
     */
    UNAVAILABLE
}
