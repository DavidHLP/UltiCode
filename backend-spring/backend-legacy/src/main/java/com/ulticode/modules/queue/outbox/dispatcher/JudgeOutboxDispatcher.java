package com.ulticode.modules.queue.outbox.dispatcher;

import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.queue.port.JudgeJobEnvelope;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.port.adapter.RedissonStreamsJudgeQueueAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Judge outbox dispatcher (ADR-003 M3a / M3c-2).
 *
 * <p><b>M3a mode</b> (default): claim rows, log + increment
 * {@code outbox.row.observed}, mark SENT. Never enqueues. The legacy RQueue
 * remains the sole active producer.
 *
 * <p><b>M3c-2 mode</b> (cutover): when {@code app.features.judge-queue.use-port=true},
 * the dispatcher hands claimed rows to the {@link JudgeQueue} port (the
 * Redisson Streams adapter in production) and marks them SENT. The watermark
 * {@code app.features.judge-queue.cutover-at} (default {@code 1970-01-01})
 * plus the {@code is_shadow = 0} filter on
 * {@link JudgeOutboxMapper#claimRealDispatch} prevents re-dispatch of M3a
 * legacy rows — ADR-005 F13.
 *
 * <p>Any moment in time has at most one active producer (ADR-005 F8):
 * either the legacy RQueue (M3a/M3b) or the outbox dispatcher (M3c-2+).
 *
 * <p>Only active when {@code app.features.use-judge-outbox=true}. The
 * cutover between shadow and real dispatch is driven by
 * {@code app.features.judge-queue.use-port}; the {@link JudgeQueue} bean
 * is only registered (via {@link RedissonStreamsJudgeQueueAdapter}'s
 * {@code @ConditionalOnProperty}) when the port is enabled, so when the
 * flag is off the {@link ObjectProvider} returns null and the dispatcher
 * stays in M3a shadow mode even if its own flag is on.
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
    /**
     * Provider (not direct injection) so the dispatcher compiles even when
     * no {@link JudgeQueue} bean is registered (i.e. before the M3c-2
     * cutover). Resolves to null in M3a/M3b; resolves to the Streams
     * adapter once the port flag is on.
     */
    private final ObjectProvider<JudgeQueue> judgeQueueProvider;
    /** Nullable so unit tests without a registry still work. */
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;

    /**
     * Boot-time mirror of {@code app.features.judge-queue.use-port}; when
     * false, the dispatcher stays in M3a shadow mode even if its own flag
     * is on.
     */
    @Value("${app.features.judge-queue.use-port:false}")
    private boolean judgeQueuePortEnabled;

    /** Cutover watermark (F13). Only rows {@code created_at >= cutoverAt} are real-dispatched. */
    @Value("${app.features.judge-queue.cutover-at:1970-01-01T00:00:00}")
    private LocalDateTime cutoverAt;

    /**
     * Claim a batch of pending rows and either observe (shadow) or
     * dispatch (real, M3c-2+). Runs every 2s. The transaction covers
     * claim + markSent so a crash between the two does not leave rows
     * stranded in PENDING after they were observed.
     */
    @Scheduled(fixedDelayString = "${judge.outbox.dispatcher.interval-ms:2000}",
            initialDelayString = "${judge.outbox.dispatcher.initial-delay-ms:15000}")
    @Transactional
    public void dispatch() {
        JudgeQueue judgeQueue = judgeQueuePortEnabled
                ? judgeQueueProvider.getIfAvailable()
                : null;
        if (judgeQueue != null) {
            dispatchReal(judgeQueue);
        } else {
            dispatchShadow();
        }
    }

    /**
     * M3a shadow path: claim (any is_shadow) → observe + mark SENT. Never
     * enqueues.
     */
    private void dispatchShadow() {
        List<JudgeOutboxRecord> claimed = judgeOutboxMapper.claim(CLAIM_BATCH_SIZE);
        if (claimed.isEmpty()) {
            return;
        }
        for (JudgeOutboxRecord row : claimed) {
            incrementRowsObserved();
            log.debug("Outbox shadow-observed row: submission={}, generation={}, is_shadow={}",
                    row.getSubmissionId(), row.getGeneration(), row.getIsShadow());
            judgeOutboxMapper.markSent(row.getId());
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

    private void incrementRowsObserved() {
        if (meterRegistry != null) {
            meterRegistry.counter("outbox.row.observed").increment();
        }
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
