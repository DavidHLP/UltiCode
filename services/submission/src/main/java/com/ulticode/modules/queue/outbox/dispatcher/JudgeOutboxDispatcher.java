package com.ulticode.modules.queue.outbox.dispatcher;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeQueue;
import com.ulticode.modules.queue.port.adapter.RedissonStreamsJudgeQueueAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
@RequiredArgsConstructor
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
    private final UuidGenerator uuidGenerator;

    /** Cutover watermark (F13). Only rows {@code created_at >= cutoverAt} are real-dispatched. */
    @Value("${app.features.judge-queue.cutover-at:1970-01-01T00:00:00}")
    private LocalDateTime cutoverAt;

    /**
     * Claim a batch of pending rows and dispatch (M3c-2+). Runs every 2s.
     * The transaction covers claim + markSent so a crash between the two
     * does not leave rows stranded in PENDING after they were dispatched.
     */
    @Scheduled(fixedDelayString = "${judge.outbox.dispatcher.interval-ms:2000}",
            initialDelayString = "${judge.outbox.dispatcher.initial-delay-ms:15000}")
    @Transactional
    public void dispatch() {
        JudgeQueue judgeQueue = judgeQueueProvider.getIfAvailable();
        if (judgeQueue == null) {
            dispatchUnavailable();
        } else {
            dispatchReal(judgeQueue);
        }
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
     * {@link JudgeJobEnvelope}), then mark SENT. On enqueue failure the
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
            JudgeJobEnvelope envelope = toEnvelope(row);
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

    /**
     * Build a v2 {@link JudgeJobEnvelope} from the outbox row's payload
     * map. The outbox row already captures {@code generation}; we add a
     * fresh {@code attemptId} here so the fence CAS targets the current
     * acquire attempt (mirroring the M3b worker contract).
     */
    private JudgeJobEnvelope toEnvelope(JudgeOutboxRecord row) {
        String attemptId = uuidGenerator.newId();
        Map<String, Object> payload = row.getPayload();
        return new JudgeJobEnvelope(
                2,
                row.getId(),
                row.getSubmissionId(),
                stringOrNull(payload, "problemId"),
                stringOrNull(payload, "userId"),
                stringOrNull(payload, "language"),
                stringOrNull(payload, "code"),
                intOrDefault(payload, "timeLimitMs", 2000),
                intOrDefault(payload, "memoryLimitKb", 256 * 1024),
                row.getGeneration(),
                attemptId);
    }

    private static String stringOrNull(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static int intOrDefault(Map<String, Object> map, String key, int dflt) {
        if (map == null) {
            return dflt;
        }
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return dflt;
            }
        }
        return dflt;
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
