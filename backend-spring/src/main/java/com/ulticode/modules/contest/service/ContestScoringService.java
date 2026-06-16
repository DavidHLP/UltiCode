package com.ulticode.modules.contest.service;

import com.ulticode.modules.submission.event.SubmissionJudgedEvent;

/**
 * Aggregates contest-scoring side effects of submission judge results.
 *
 * <p>Called from {@link com.ulticode.modules.contest.listener.ContestScoringListener}
 * (a {@code @TransactionalEventListener(AFTER_COMMIT)} consumer of
 * {@link SubmissionJudgedEvent}) and from the contest scheduler.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li><b>P0-1</b> apply a judge verdict: write {@code contest_submissions.is_accepted},
 *       insert {@code contest_problem_results}, insert {@code first_solve_records} when
 *       applicable, and increment participant aggregate fields
 *       (totalScore / totalPenalty / attemptCount).</li>
 *   <li><b>P0-2</b> transition all REGISTERED participants to STARTED when a contest
 *       moves into the RUNNING state.</li>
 *   <li><b>P1-2 / P2-2</b> auto-finish virtual participants whose
 *       {@code started_at + duration} has elapsed.</li>
 *   <li><b>P2-5</b> cascade-delete a contest's relational rows (participants, problems,
 *       submissions, problem results, first-solve records) when the parent contest is
 *       soft-deleted.</li>
 * </ul>
 */
public interface ContestScoringService {

    /**
     * Apply a submission's final judge verdict to contest scoring state.
     * No-op if the submission is not part of any running contest.
     *
     * @param event the judged submission event
     */
    void applyJudgeResult(SubmissionJudgedEvent event);

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
     * Cascade-delete a soft-deleted contest's relational rows. Idempotent.
     *
     * @param contestId the contest to clean up
     */
    void deleteContestCascade(String contestId);
}
