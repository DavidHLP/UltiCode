package com.ulticode.modules.queue.port;

/**
 * Opaque handle returned from {@link JudgeQueue#poll}, passed to
 * {@link JudgeQueue#ack} / {@link JudgeQueue#nack} to commit or reject a
 * specific job.
 *
 * <p>M3c-1 ships with just the envelope — the legacy RQueue path (still
 * active until M3c cutover) does not have a broker ack concept, and the
 * Redisson Streams adapter (M3c-2) will thread a {@code StreamMessageId}
 * through adapter-internal state (e.g. the payload map value) rather than
 * over the port boundary. Keeping the port free of Redisson types preserves
 * the hex-arch dependency rule (ADR-002): the port package depends only on
 * the domain, not on the broker.
 *
 * <p>When the port gains a true ack target, this record gains one more
 * field; the worker already passes the handle end-to-end so the migration
 * is a one-field add.
 */
public record JudgeJobHandle(JudgeJobEnvelope envelope) {
}
