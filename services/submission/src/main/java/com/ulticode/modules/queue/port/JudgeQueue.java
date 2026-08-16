package com.ulticode.modules.queue.port;

import java.util.Optional;

/**
 * Port for the judge job queue (ADR-003 §2.4 M3c, hex-arch template
 * adapted from ADR-002 {@code SandboxExecutor}).
 *
 * <p>The production adapter is Redis Streams with consumer group + ack;
 * InMemory remains available for tests. Workers call {@link #poll} for the
 * next available job and {@link #ack} / {@link #nack} to commit or reject.
 * The legacy {@code QueueService.enqueueJudgeJob(RQueue.add)} path remains a
 * compatibility rollback path, gated by {@code app.features.judge-queue.use-port}
 * (default {@code false}).
 *
 * <p><b>Idempotency contract</b>: {@link #enqueue} must be a noop on repeat
 * {@code (submissionId, generation)} — never a duplicate dispatch. Adapters
 * achieve this with the broker's dedup primitive (e.g. Redis Streams
 * auto-generated IDs with a {@code (submissionId, generation)} business key
 * in the payload, or an out-of-band SETNX in InMemory).
 *
 * <p><b>Ack-based consumption</b> (ADR-003 §2.6 F6 revision): the broker
 * retains the job between {@link #poll} and {@link #ack}. A worker that
 * crashes after poll but before ack leaves the job in the broker's pending
 * list; a reaper (M3c-2 {@code UnackedStreamEntriesReaper}) reclaims it
 * after the visibility timeout. This replaces the legacy destructive
 * {@code RQueue.poll}.
 */
public interface JudgeQueue {

    /**
     * Enqueue a job. Idempotent on {@code (submissionId, generation)} —
     * repeat calls are silent noops, never duplicate dispatches.
     *
     * @param envelope the job to enqueue (v1 or v2 per {@link JudgeJobEnvelope})
     */
    void enqueue(JudgeJobEnvelope envelope);

    /**
     * Block up to {@code timeoutMillis} for the next available job.
     * Non-blocking if {@code timeoutMillis <= 0}.
     *
     * @param timeoutMillis max wait in ms; {@code <= 0} for non-blocking poll
     * @return present job with its ack handle, or empty on timeout
     */
    Optional<JudgeJobHandle> poll(long timeoutMillis);

    /**
     * Acknowledge a successfully processed job. After ack the broker may
     * discard the payload; the outbox row that produced the dispatch can
     * then be marked SENT (M3c-2 dispatcher).
     */
    void ack(JudgeJobHandle handle);

    /**
     * Negative-ack: requeue or dead-letter depending on broker semantics.
     * For the Redisson Streams adapter (M3c-2) the convention is to leave
     * the entry in the consumer group's pending entries list so
     * {@code UnackedStreamEntriesReaper} can claim it after the visibility
     * timeout. After the configured processing-attempt budget is exhausted,
    * the adapter writes the entry to {@code judge:{judge-stream}:dlq} and ACKs the
     * original PEL entry instead of retrying forever.
     *
     * @param handle the job handle returned from {@link #poll}
     * @param reason short diagnostic, written to the outbox {@code last_error}
     */
    void nack(JudgeJobHandle handle, String reason);

    /**
     * Operationally meaningful depth of the underlying broker for
     * monitoring. For the Redisson Streams adapter this is the
     * consumer-group pending total (XPENDING) — entries delivered but
     * not yet acked, i.e. in-flight or awaiting reclaim by the unacked
     * reaper. For the in-memory test adapter this is the sum of the
     * ready and pending-ack mirrors.
     *
     * <p>The queue inspector consumes this when
     * {@code app.features.judge-queue.use-port=true} so monitoring
     * sees the Stream-backed depth in the same VO shape as the legacy
     * {@code RQueue.size()} path.
     *
     * @return the broker-side depth; never negative
     */
    long pendingDepth();
}
