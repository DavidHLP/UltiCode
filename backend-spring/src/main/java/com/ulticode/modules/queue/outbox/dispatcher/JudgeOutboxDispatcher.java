package com.ulticode.modules.queue.outbox.dispatcher;

import com.ulticode.modules.queue.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.queue.outbox.mapper.JudgeOutboxMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Shadow outbox dispatcher (ADR-003 M3a / ADR-005 F8).
 *
 * <p><b>M3a mode: claim rows, log + increment {@code outbox.row.observed}, then
 * mark them SENT. This dispatcher NEVER enqueues.</b> The legacy RQueue remains
 * the sole active producer of judge jobs until the M3c cutover. The point of
 * this shadow loop is to exercise the claim/markSent path against real data so
 * the M3c flip to real delivery is a one-line change.
 *
 * <p>Claiming happens inside a transaction with {@code FOR UPDATE SKIP LOCKED}
 * so multiple dispatcher instances (or a dispatcher racing the lease reaper's
 * outbox writes) never collide. Each claimed row is marked SENT via
 * {@link JudgeOutboxMapper#markSent} so it is not re-claimed on the next tick.
 *
 * <p>Only active when {@code app.features.use-judge-outbox=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.features.use-judge-outbox",
        havingValue = "true")
public class JudgeOutboxDispatcher {

    /** Max rows claimed per sweep. Bounded to keep each transaction short. */
    private static final int CLAIM_BATCH_SIZE = 50;

    private final JudgeOutboxMapper judgeOutboxMapper;
    /** Nullable so unit tests without a registry still work. */
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    /**
     * Claim a batch of pending rows and observe them (shadow). Runs every 2s.
     * The transaction covers claim + markSent so a crash between the two does
     * not leave rows stranded in PENDING after they were observed.
     */
    @Scheduled(fixedDelayString = "${judge.outbox.dispatcher.interval-ms:2000}",
            initialDelayString = "${judge.outbox.dispatcher.initial-delay-ms:15000}")
    @Transactional
    public void dispatch() {
        List<JudgeOutboxRecord> claimed = judgeOutboxMapper.claim(CLAIM_BATCH_SIZE);
        if (claimed.isEmpty()) {
            return;
        }
        for (JudgeOutboxRecord row : claimed) {
            // M3a shadow: observe only, never enqueue. The real delivery already
            // happened via the legacy RQueue; this row exists to prove the
            // dispatch intent was durable.
            incrementRowsObserved();
            log.debug("Outbox shadow-observed row: submission={}, generation={}, is_shadow={}",
                    row.getSubmissionId(), row.getGeneration(), row.getIsShadow());
            judgeOutboxMapper.markSent(row.getId());
        }
    }

    /**
     * Increment the {@code outbox.row.observed} counter. No-op without a registry.
     */
    private void incrementRowsObserved() {
        if (meterRegistry != null) {
            meterRegistry.counter("outbox.row.observed").increment();
        }
    }
}
