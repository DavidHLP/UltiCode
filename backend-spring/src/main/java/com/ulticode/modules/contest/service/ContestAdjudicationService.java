package com.ulticode.modules.contest.service;

import com.ulticode.modules.submission.event.SubmissionJudgedEvent;

/**
 * Deep Contest adjudication module — the single owner of verdict application.
 *
 * <p>One seam: a judged submission event flows in (from the AFTER_COMMIT
 * {@link com.ulticode.modules.contest.listener.ContestAdjudicationListener}),
 * and this module applies every contest-scoring invariant that follows a
 * verdict:
 * <ul>
 *   <li><b>Idempotency</b> — safe to replay for the same submission without
 *       double-counting, because the upstream {@code submissions} row is
 *       never rewritten here and each downstream write is keyed.</li>
 *   <li><b>Verdict write</b> — stamps {@code contest_submissions.is_accepted}.</li>
 *   <li><b>First-solve claiming</b> — the {@code first_solve_records} unique
 *       key is the atomic gate that decides the bonus; a lost race is a
 *       no-op, never an error.</li>
 *   <li><b>Participant aggregates</b> — totalScore / totalPenalty /
 *       attemptCount / lastSolveTime advance atomically with the verdict.</li>
 *   <li><b>Cache invalidation</b> — the ranking cache is evicted so the next
 *       read reflects the fresh aggregate.</li>
 * </ul>
 *
 * <p>Contest lifecycle transitions (REGISTERED -> STARTED, virtual
 * auto-finish) and contest cleanup (cascade delete) live in the
 * {@link ContestLifecycleService}; this module does not touch them.
 *
 * <p>Preserves D-04 (AFTER_COMMIT post-judge scoring) and ADR-006 (scoring
 * mode + penalty-keyed wrong-submission handling).
 */
public interface ContestAdjudicationService {

    /**
     * Apply a submission's final judge verdict to contest scoring state.
     * No-op if the submission is not part of any running contest.
     *
     * @param event the judged submission event
     */
    void applyJudgeResult(SubmissionJudgedEvent event);
}
