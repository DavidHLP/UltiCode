package com.ulticode.modules.queue.outbox.reaper;

import com.ulticode.modules.queue.port.adapter.RedissonStreamsJudgeQueueAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
 *   <li>leaves the actual processing to the worker (M3c-3).</li>
 * </ol>
 *
 * <p>Only active when {@code app.features.judge-queue.use-port=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.features.judge-queue.use-port",
        havingValue = "true")
public class UnackedStreamEntriesReaper {

    /** Reaper sweep cadence. 10s is the ADR-003 §2.6 default. */
    private static final long VISIBILITY_TIMEOUT_MS = 60_000L;

    private final RedissonStreamsJudgeQueueAdapter streamsAdapter;
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
            streamsAdapter.claimIdle(VISIBILITY_TIMEOUT_MS)
                    .ifPresent(handle -> log.info(
                            "Unacked Streams reaper reclaimed submission={} gen={} (idle >= {}ms)",
                            handle.envelope().submissionId(),
                            handle.envelope().generation(),
                            VISIBILITY_TIMEOUT_MS));
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
