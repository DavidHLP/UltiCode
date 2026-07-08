package com.ulticode.modules.queue.outbox.shadow;

import com.ulticode.modules.queue.outbox.entity.JudgeOutboxRecord;
import com.ulticode.modules.queue.outbox.mapper.JudgeOutboxMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Shadow comparator for the judge outbox (ADR-003 M3a, ADR-005 F8).
 *
 * <p>The M3a invariant is <b>at most one active producer of judge jobs</b>: the
 * legacy RQueue. The outbox is a shadow that records dispatch intent. To prove
 * the shadow dispatcher is keeping pace with outbox writes (the M3c hard gate is
 * "7-day accumulated diff = 0"), this comparator detects rows that were written
 * but never observed by the dispatcher within a grace window — the signature of
 * either a stalled dispatcher or a real enqueue that never happened.
 *
 * <p>A row is "in diff" when it is still {@code PENDING} older than the dispatcher
 * grace period (the dispatcher runs every 2s, so a PENDING row older than ~15s
 * means the dispatcher has fallen behind or crashed). Each such row increments
 * the {@code outbox.shadow.diff} counter.
 *
 * <p><b>F5 known limitation (M3c TODO):</b> this metric only proves the shadow
 * dispatcher observed each outbox row; it does <b>not</b> prove the active
 * producer (the legacy {@code enqueueJudgeJob} RQueue call) actually ran for
 * that {@code (submissionId, generation)}. The codex F5 review flagged that a
 * real enqueue failure or skip leaves the outbox row PENDING, but if the shadow
 * dispatcher still claims+marks it SENT in time the comparator sees zero diff —
 * a false negative. Closing that gap requires recording a Redis seen-set key
 * {@code judge:dispatch:seen:{submissionId}:{generation}} (prefix already
     * defined in {@code JudgeStreamKeys#JUDGE_DISPATCH_SEEN_PREFIX}) at the real
 * enqueue site, then having this comparator count stale-PENDING rows whose seen
 * key is absent. That recording cannot live in {@code QueueServiceImpl} today
 * because the v1 {@code JudgeJob} envelope carries no {@code generation}; the
 * envelope v2 (ADR-005 M3c) is the planned carrier. Until then this comparator
 * is a dispatcher-liveness check, not a real-delivery proof — the M3c cutover
 * gate must be read with that caveat.
 *
 * <p>Only active when {@code app.features.use-judge-outbox=true}. Flag-off
 * deployments do not write outbox rows and do not run this comparator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.features.use-judge-outbox",
        havingValue = "true")
public class OutboxShadowComparator {

    /**
     * Grace period (seconds) a row may stay PENDING before it counts as a diff.
     * Generous relative to the dispatcher's 2s tick so normal jitter does not
     * trip the alarm.
     */
    private static final long PENDING_GRACE_SECONDS = 15L;

    private final JudgeOutboxMapper judgeOutboxMapper;
    /** Nullable so unit tests without a registry still work. */
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    /**
     * Detect outbox rows the dispatcher has not caught up to. Runs every 5s;
     * each stale-PENDING row increments {@code outbox.shadow.diff}. Expected
     * steady-state is zero over a 7-day window (the M3c cutover gate).
     *
     * <p>Caveat (F5): this counts dispatcher-liveness, not real-delivery proof.
     * A row whose real RQueue enqueue failed but whose shadow dispatcher still
     * claimed+marked it SENT will NOT show up here. See the class Javadoc.
     */
    @Scheduled(fixedDelayString = "${judge.outbox.comparator.interval-ms:5000}",
            initialDelayString = "${judge.outbox.comparator.initial-delay-ms:20000}")
    public void compare() {
        LocalDateTime staleBefore = LocalDateTime.now(clock).minusSeconds(PENDING_GRACE_SECONDS);
        List<JudgeOutboxRecord> stale = judgeOutboxMapper.selectStalePending(staleBefore);
        if (stale.isEmpty()) {
            return;
        }

        int diff = stale.size();
        if (meterRegistry != null) {
            meterRegistry.counter("outbox.shadow.diff").increment(diff);
        }
        log.warn("Outbox shadow diff: {} PENDING row(s) older than {}s (dispatcher lag or missed enqueue)",
                diff, PENDING_GRACE_SECONDS);
    }
}

