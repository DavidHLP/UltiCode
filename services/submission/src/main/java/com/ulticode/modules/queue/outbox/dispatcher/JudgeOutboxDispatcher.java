package com.ulticode.modules.queue.outbox.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.common.lifecycle.DrainGate;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeQueue;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Judge outbox dispatcher (SPLIT-003 slice-3, backend-submission local copy).
 *
 * <p><b>M3c-2 mode only</b>: backend-submission has no legacy RQueue
 * producer, so the shadow/replay path from the App dispatcher is removed
 * (DEC-014). The dispatcher claims {@code is_shadow = 0} rows newer than the
 * cutover watermark ({@link JudgeOutboxMapper#claimRealDispatch}), hands each
 * to the {@link JudgeQueue} port (Redisson Streams adapter) and marks SENT;
 * on enqueue failure the row is rolled back to PENDING with a backoff via
 * {@link JudgeOutboxMapper#markRetry}.
 *
 * <p>Only active when {@code app.features.use-judge-outbox=true} and the
 * {@link JudgeQueue} bean is registered (the Streams adapter is
 * {@code @ConditionalOnProperty} on {@code app.features.judge-queue.use-port});
 * when the port flag is off the {@link ObjectProvider} returns null and the
 * dispatcher keeps real rows retryable instead of dropping them.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!test")
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "${app.features.use-judge-outbox:false}")
public class JudgeOutboxDispatcher {

    /** Max rows claimed per sweep. Bounded to keep each transaction short. */
    private static final int CLAIM_BATCH_SIZE = 50;

    private final JudgeOutboxMapper judgeOutboxMapper;
    /**
     * Provider (not direct injection) so the dispatcher compiles even when
     * no {@link JudgeQueue} bean is registered. Resolves to null when the
     * port flag is off; resolves to the Streams adapter once it is on.
     */
    private final ObjectProvider<JudgeQueue> judgeQueueProvider;
    /** Nullable so unit tests without a registry still work. */
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final JudgeJobEnvelopeTranslator envelopeTranslator;
    private final DrainGate drainGate = new DrainGate();

    @Autowired
    public JudgeOutboxDispatcher(JudgeOutboxMapper judgeOutboxMapper,
                                 ObjectProvider<JudgeQueue> judgeQueueProvider,
                                 MeterRegistry meterRegistry,
                                 Clock clock,
                                 UuidGenerator uuidGenerator) {
        this(judgeOutboxMapper, judgeQueueProvider, meterRegistry, clock,
                new JudgeJobEnvelopeTranslator(
                        new ObjectMapper(), uuidGenerator));
    }

    JudgeOutboxDispatcher(JudgeOutboxMapper judgeOutboxMapper,
                          ObjectProvider<JudgeQueue> judgeQueueProvider,
                          MeterRegistry meterRegistry,
                          Clock clock,
                          JudgeJobEnvelopeTranslator envelopeTranslator) {
        this.judgeOutboxMapper = judgeOutboxMapper;
        this.judgeQueueProvider = judgeQueueProvider;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.envelopeTranslator = envelopeTranslator;
    }

    /** Cutover watermark (F13). Only rows {@code created_at >= cutoverAt} are real-dispatched. */
    @Value("${app.features.judge-queue.cutover-at:1970-01-01T00:00:00}")
    private LocalDateTime cutoverAt;

    /**
     * Claim a batch of pending rows and dispatch (M3c-2+). Runs every 2s.
     * The transaction covers claim + markSent so a crash between the two
     * does not leave rows stranded in PENDING after they were dispatched.
     */
    @Scheduled(scheduler = "submissionJudgeOutboxScheduler",
            fixedDelayString = "${judge.outbox.dispatcher.interval-ms:2000}",
            initialDelayString = "${judge.outbox.dispatcher.initial-delay-ms:15000}")
    @Transactional
    public void dispatch() {
        if (!drainGate.tryEnter()) {
            return;
        }
        try {
            JudgeQueue judgeQueue = judgeQueueProvider.getIfAvailable();
            if (judgeQueue == null) {
                dispatchUnavailable();
            } else {
                dispatchReal(judgeQueue);
            }
        } finally {
            drainGate.leave();
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
    }

    /** Keep real rows retryable when cutover is configured but its provider is absent. */
    private void dispatchUnavailable() {
        List<JudgeOutboxRecord> claimed = judgeOutboxMapper.claimRealDispatch(CLAIM_BATCH_SIZE, cutoverAt);
        for (JudgeOutboxRecord row : claimed) {
            judgeOutboxMapper.markRetry(row.getId(),
                    LocalDateTime.now(clock).plusSeconds(backoffSeconds(row)),
                    "judge queue provider unavailable");
        }
        if (!claimed.isEmpty()) {
            log.error("Judge Streams provider unavailable; kept {} outbox rows retryable", claimed.size());
        }
    }

    /**
     * M3c-2 real-dispatch path: claim only {@code is_shadow = 0} rows newer
     * than the cutover watermark (F13), hand each to the
     * {@link JudgeQueue} port (envelope payload → JSON via
     * {@link JudgeJobEnvelope}), then mark SENT. Rows with unusable payloads
     * are dead-lettered instead of being marked SENT; on enqueue failure the
     * row is rolled back to PENDING with a backoff via
     * {@link JudgeOutboxMapper#markRetry} so the next sweep retries.
     */
    private void dispatchReal(JudgeQueue judgeQueue) {
        List<JudgeOutboxRecord> claimed =
                judgeOutboxMapper.claimRealDispatch(CLAIM_BATCH_SIZE, cutoverAt);
        if (claimed.isEmpty()) {
            return;
        }
        for (JudgeOutboxRecord row : claimed) {
            JudgeJobEnvelope envelope = envelopeTranslator.translate(row);
            if (envelope == null) {
                // Malformed or legacy payload: enqueueing the half-null
                // envelope would mark the row SENT and only fail later as a
                // judge system error. Dead-letter instead so ops can replay
                // with a corrected payload.
                String reason = truncate("malformed outbox payload: required fields "
                        + "(problemId/userId/language/code) missing or undecodable");
                judgeOutboxMapper.markDead(row.getId(), reason);
                log.error("Dead-lettered outbox row {} (submission={} gen={}): unusable payload",
                        row.getId(), row.getSubmissionId(), row.getGeneration());
                continue;
            }
            try {
                judgeQueue.enqueue(envelope);
                judgeOutboxMapper.markSent(row.getId());
                incrementRealDispatched();
                log.debug("Outbox real-dispatched submission={} gen={} (cutover)",
                        row.getSubmissionId(), row.getGeneration());
            } catch (Exception e) {
                LocalDateTime nextRetry = LocalDateTime.now(clock).plusSeconds(backoffSeconds(row));
                String reason = truncate(e.getMessage());
                judgeOutboxMapper.markRetry(row.getId(), nextRetry, reason);
                incrementRealRetried();
                log.warn("Real dispatch failed for submission={} gen={}: {}",
                        row.getSubmissionId(), row.getGeneration(), reason);
            }
        }
    }

    /** Exponential backoff: 2s × 2^attempts, capped at 60s. */
    private static long backoffSeconds(JudgeOutboxRecord row) {
        int attempts = row.getAttempts() == null ? 0 : row.getAttempts();
        long seconds = (long) (2L * Math.pow(2, Math.min(attempts, 5)));
        return Math.min(seconds, 60L);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "unknown";
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private void incrementRealDispatched() {
        if (meterRegistry != null) {
            meterRegistry.counter("outbox.row.real_dispatched").increment();
        }
    }

    private void incrementRealRetried() {
        if (meterRegistry != null) {
            meterRegistry.counter("outbox.row.real_retried").increment();
        }
    }
}
