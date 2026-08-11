package com.ulticode.modules.contest.service;

import java.time.LocalDateTime;

/**
 * Contest lifecycle module — owns every time-driven contest lifetime policy:
 * due selection, idempotent state transitions, participant closure, start
 * reminders, push/ranking side effects, and rating handoff (plus the
 * relational cleanup that follows a contest soft-delete).
 *
 * <p>This is the natural home for behavior that is about a contest's lifetime
 * rather than about a single verdict:
 * <ul>
 *   <li><b>tick</b> — the 10-second heartbeat that advances UPCOMING→RUNNING,
 *       claims RUNNING→FINISHING, retries FINISHING finalization, and then
 *       auto-finishes expired virtual participants.</li>
 *   <li><b>P0-2</b> batch-transition REGISTERED participants to STARTED when a
 *       contest moves into RUNNING.</li>
 *   <li><b>P1-2 / P2-2</b> auto-finish virtual participants whose
 *       {@code started_at + duration} has elapsed.</li>
 *   <li><b>reminders</b> — the per-minute T-24h / T-1h contest-start fan-out.</li>
 *   <li><b>P2-5</b> cascade-delete a soft-deleted contest's relational rows
 *       (participants, problems, submissions, problem results, first-solve
 *       records).</li>
 * </ul>
 *
 * <p>The {@link com.ulticode.modules.contest.scheduler.ContestScheduler} is a
 * thin trigger adapter over this seam: it contributes only the scheduling
 * annotations and the wall-clock {@code now}, so the lifecycle invariants
 * concentrate here and become directly testable with a deterministic
 * {@link java.time.Clock}.
 *
 * <p>Verdict application and scoring invariants live in the deep
 * {@link ContestAdjudicationService}; this module does not score — it only
 * hands off to {@link RatingCalculationService} while a contest is FINISHING,
 * before publishing FINISHED. Every method is idempotent and safe to retry.
 */
public interface ContestLifecycleService {

    /**
     * Transition all REGISTERED participants of a contest to STARTED, stamping
     * {@code started_at} to {@code now}. Idempotent.
     *
     * @param contestId the contest whose participants to start
     * @return number of participants transitioned
     */
    int batchStartParticipants(String contestId);

    /**
     * Auto-finish virtual participants whose time has expired
     * ({@code started_at + duration_minutes < now}). Idempotent.
     *
     * @return number of participants transitioned
     */
    int autoFinishVirtualParticipants();

    /**
     * Soft-delete an UPCOMING or FINISHED contest and remove every
     * contest-owned relational row in one owner transaction. A retry after a
     * committed soft-delete only repeats cleanup and does not rewrite the
     * parent row.
     *
     * @param contestId the contest to delete
     * @param deletedBy the actor performing the delete
     */
    void deleteContestCascade(String contestId, String deletedBy);

    /**
     * Drive one 10-second scheduler heartbeat at the given wall-clock
     * instant: advance due UPCOMING contests to RUNNING, claim due RUNNING
     * contests as FINISHING, retry FINISHING finalization (closing real
     * participants and handing off to the rating service), publish FINISHED,
     * then auto-finish expired virtual participants.
     *
     * <p>Step 3 (virtual auto-finish) is fault-isolated so a failure there
     * never blocks the next tick; the per-contest transition loops match the
     * prior scheduler and do not catch per-contest failures (the 10s tick
     * retries). Idempotent: already-transitioned rows are skipped, so
     * repeated ticks are safe.
     *
     * @param now the wall-clock instant the trigger observed for this tick
     */
    void tick(LocalDateTime now);

    /**
     * Send T-24h and T-1h contest-start reminders to registered participants
     * for the given wall-clock instant. Each (user, contest, reminderType)
     * triple is a distinct typed {@code ContestStartingIntent} so the
     * notification dispatcher's ledger dedups repeats across ticks.
     *
     * <p>Dispatch is fire-and-forget per recipient; a delivery failure for one
     * participant never aborts the remainder. Idempotent across ticks.
     *
     * @param now the wall-clock instant the trigger observed
     */
    void sendReminders(LocalDateTime now);
}
