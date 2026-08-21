package com.ulticode.modules.queue.port.adapter;

import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeJobHandle;
import com.ulticode.submission.api.queue.JudgeQueue;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * In-memory {@link JudgeQueue} adapter (ADR-003 M3c-2). Hex-arch test
 * adapter following the ADR-002 {@code InMemorySandboxAdapter} pattern.
 *
 * <p>Semantics mirror the Redisson Streams adapter so tests assert
 * identical behavior:
 * <ul>
 *   <li>{@link #enqueue} is idempotent on {@code (submissionId, generation)}
 *       via a {@link ConcurrentHashMap} SETNX-mirror.</li>
 *   <li>{@link #poll} is non-destructive: the envelope moves to a pending
 *       acks map (PEL mirror) and is removed only by {@link #ack}.</li>
 *   <li>{@link #nack} leaves the entry in the pending acks map so a future
 *       poll can retry it (no re-enqueue, mirroring the broker's PEL
 *       retention).</li>
 * </ul>
 *
 * <p>Single-process only; the {@code pendingAckCount} helper exists for
 * integration tests.
 */
@Slf4j
public class InMemoryJudgeQueueAdapter implements JudgeQueue {

    private final BlockingDeque<JudgeJobEnvelope> ready = new LinkedBlockingDeque<>();
    private final ConcurrentHashMap<String, JudgeJobEnvelope> pendingAcks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> dedupSet = new ConcurrentHashMap<>();
    private static final String DEDUP_PREFIX = "judge:dispatch:seen:";

    @Override
    public void enqueue(JudgeJobEnvelope envelope) {
        String dedupKey = DEDUP_PREFIX + envelope.submissionId() + ":" + envelope.generation();
        Boolean prior = dedupSet.putIfAbsent(dedupKey, Boolean.TRUE);
        if (prior != null) {
            log.debug("In-memory dedup: skipping repeat enqueue for {} gen {}",
                    envelope.submissionId(), envelope.generation());
            return;
        }
        ready.add(envelope);
    }

    @Override
    public Optional<JudgeJobHandle> poll(long timeoutMillis) {
        JudgeJobEnvelope envelope;
        try {
            envelope = timeoutMillis > 0
                    ? ready.poll(timeoutMillis, TimeUnit.MILLISECONDS)
                    : ready.poll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        if (envelope == null) {
            return Optional.empty();
        }
        // Move to pendingAcks (PEL mirror).
        pendingAcks.put(ackKey(envelope), envelope);
        return Optional.of(new JudgeJobHandle(envelope));
    }

    @Override
    public void ack(JudgeJobHandle handle) {
        if (handle == null || handle.envelope() == null) {
            return;
        }
        pendingAcks.remove(ackKey(handle.envelope()));
    }

    @Override
    public void nack(JudgeJobHandle handle, String reason) {
        if (handle == null || handle.envelope() == null) {
            return;
        }
        // Leave in pendingAcks; do NOT re-enqueue. Mirrors the broker's PEL:
        // the unacked reaper (M3c-2 UnackedStreamEntriesReaper) decides
        // visibility timeout / reclaim. M3c-2 ships the XCLAIM half; M3c-3
        // adds worker-side PEL re-read to actually consume reclaimed entries.
        log.debug("In-memory nack for {} ({}): left in PEL",
                ackKey(handle.envelope()), reason);
    }

    /** Test-only: pending acks count (PEL mirror). */
    public int pendingAckCount() {
        return pendingAcks.size();
    }

    @Override
    public long pendingDepth() {
        // Ready (not yet polled) plus in-flight (polled but not acked):
        // both are "not committed" from monitoring's point of view.
        return ready.size() + pendingAcks.size();
    }

    private static String ackKey(JudgeJobEnvelope envelope) {
        return envelope.id() != null
                ? envelope.id()
                : envelope.submissionId() + ":" + envelope.generation();
    }
}
