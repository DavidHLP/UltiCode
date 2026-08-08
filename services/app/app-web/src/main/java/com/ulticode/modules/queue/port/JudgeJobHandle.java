package com.ulticode.modules.queue.port;

/**
 * Opaque handle returned from {@link JudgeQueue#poll}, passed to
 * {@link JudgeQueue#ack} / {@link JudgeQueue#nack} to commit or reject a
 * specific job.
 *
 * <p>M3c-1 shipped with just the envelope as a placeholder. M3c-2 adds
 * {@link #ackToken}, an opaque broker-specific token the adapter uses to
 * target the exact pending entry on ack/nack. For the Redisson Streams
 * adapter this is the {@code StreamMessageId} (kept as
 * {@link Object} so the port package stays broker-agnostic per the
 * ADR-002 hex-arch dependency rule). The single-arg constructor preserves
 * source compatibility with M3c-1 callers and test scaffolding.
 */
public record JudgeJobHandle(JudgeJobEnvelope envelope, Object ackToken) {

    public JudgeJobHandle(JudgeJobEnvelope envelope) {
        this(envelope, null);
    }
}
