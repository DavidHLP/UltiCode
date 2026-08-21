package com.ulticode.modules.queue.outbox.reaper;

import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.adapter.RedissonStreamsJudgeQueueAdapter;
import com.ulticode.modules.queue.processor.JudgeWorkerProcessor;
import com.ulticode.modules.queue.redis.JudgeStreamKeys;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * ADR-003 M3c-2 unacked Streams reaper (§2.6 F6 revision).
 *
 * <p>After the Redisson Streams cutover, jobs sit in the
 * {@code judge-workers} consumer group's pending entries list between
 * {@code XREADGROUP} and {@code XACK}. A worker that crashes (or is killed)
 * after poll but before ack leaves its job in the PEL indefinitely. This
 * reaper periodically:
 * <ol>
 *   <li>reads {@code XPENDING} to count stale entries (gauge metric);</li>
 *   <li>if any are idle longer than the visibility timeout, calls
 *       {@link RedissonStreamsJudgeQueueAdapter#claimIdle(long)} so a
 *       subsequent {@code XREADGROUP > 0} re-delivers the entry to this
 *       consumer;</li>
 *   <li>routes the reclaimed handle to the worker for processing.</li>
 * </ol>
 *
 * <p>Only active when {@code app.features.judge-queue.use-port=true}.
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.features.judge-queue.use-port",
        havingValue = "true")
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.role:api}' == 'judge'")
public class UnackedStreamEntriesReaper {

    /**
     * Reaper sweep cadence. 10s is the ADR-003 §2.6 default. The reclaim
     * threshold reuses {@link JudgeStreamKeys#JUDGE_STREAM_VISIBILITY_TIMEOUT_MS}
     * so it cannot drift from the adapter's dedup TTL and poll semantics.
     */

    private final RedissonStreamsJudgeQueueAdapter streamsAdapter;
    /**
     * Provider (not direct injection) so the reaper also remains usable in
     * contexts that do not register {@link JudgeWorkerProcessor}. Resolves to
     * null in that case; the claimed entry remains in the PEL and a later
     * reaper sweep can retry it after the worker is available.
     */
    private final ObjectProvider<JudgeWorkerProcessor> workerProvider;
    /** Nullable so unit tests without a registry still compile. */
    private final MeterRegistry meterRegistry;

    @Scheduled(
            fixedDelayString = "${judge.streams.reaper.interval-ms:10000}",
            initialDelayString = "${judge.streams.reaper.initial-delay-ms:15000}")
    public void recoverUnackedStreamEntries() {
        long pending = streamsAdapter.pendingCount();
        registerPendingGauge(pending);
        if (pending == 0) {
            return;
        }
        // Reclaim one stale entry per sweep; the fixedDelay paces the loop
        // so we don't race a slow worker.
        try {
            JudgeWorkerProcessor worker = workerProvider.getIfAvailable();
            if (worker != null && !worker.hasCapacity()) {
                return;
            }
            java.util.Optional<JudgeJobHandle> reclaimed =
                    streamsAdapter.claimIdle(JudgeStreamKeys.JUDGE_STREAM_VISIBILITY_TIMEOUT_MS);
            if (reclaimed.isEmpty()) {
                return;
            }
            JudgeJobHandle handle = reclaimed.get();
            log.info("Unacked Streams reaper reclaimed submission={} gen={} (idle >= {}ms)",
                    handle.envelope().submissionId(),
                    handle.envelope().generation(),
                    JudgeStreamKeys.JUDGE_STREAM_VISIBILITY_TIMEOUT_MS);
            // codex P1 #3 fix: route the reclaimed handle to the worker
            // for fenced processing + ack. Without this, the reclaimed
            // entry sits in the PEL and is never consumed (worker's
            // neverDelivered() poll ignores it). We delegate to the
            // worker so a single fencing pass handles the whole job
            // (acquireLease -> heartbeat -> execute -> writeVerdictFenced
            // -> XACK). A later reaper sweep is the fallback when the worker
            // bean is not wired.
            if (worker != null) {
                worker.processReclaimedHandle(streamsAdapter, handle);
            } else {
                log.warn("Reaper reclaimed submission={} but worker not wired; "
                        + "a later reaper sweep will retry it",
                        handle.envelope().submissionId());
            }
        } catch (Exception e) {
            // A single reclaim failure must not stop the reaper. The
            // pendingCount is the source of truth; the next sweep will
            // retry naturally.
            log.warn("Unacked Streams reaper sweep failed: {}", e.getMessage());
        }
    }

    /**
     * Update the {@code judge.streams.pending} gauge. No-op without a
     * registry; safe to call on every sweep.
     */
    private void registerPendingGauge(long pending) {
        if (meterRegistry != null) {
            meterRegistry.gauge("judge.streams.pending", pending);
        }
    }
}
