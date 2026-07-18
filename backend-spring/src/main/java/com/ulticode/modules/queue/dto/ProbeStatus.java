package com.ulticode.modules.queue.dto;

/**
 * Outcome of a queue-depth probe.
 *
 * <p>The queue inspector translates any Redis/Redisson probe failure
 * into {@link #PROBE_FAILED} on the returned snapshot instead of
 * swallowing the failure into a zero depth. Callers (notably the
 * monitoring inspector's health check) MUST treat a {@code PROBE_FAILED}
 * snapshot as "no trustworthy signal" and surface it as unhealthy,
 * never as "queue empty".
 *
 * <p>Deep-module rationale: before this enum existed, monitoring read
 * a BullMQ key layout that no Java writer ever produced, so every
 * queue always looked empty and the health check was permanently
 * green even during a Redis outage. Carrying the probe outcome on the
 * snapshot keeps the "I have no data" signal distinct from the "depth
 * is zero" signal at the type level.
 */
public enum ProbeStatus {
    /**
     * The probe reached the broker and returned a trustworthy depth.
     */
    OK,

    /**
     * The probe could not reach the broker or the broker rejected the
     * read. The accompanying depth is informational only (typically
     * zero) and MUST NOT be reported as healthy.
     */
    PROBE_FAILED
}
