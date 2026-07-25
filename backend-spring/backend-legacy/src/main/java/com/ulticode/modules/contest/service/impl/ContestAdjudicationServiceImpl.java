package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.ContestProblemResult;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.contest.entity.FirstSolveRecord;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestProblemResultMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.FirstSolveRecordMapper;
import com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor;
import com.ulticode.modules.contest.scoring.ScoringStrategy;
import com.ulticode.modules.contest.scoring.ScoringStrategyResolver;
import com.ulticode.modules.contest.service.ContestAdjudicationService;
import com.ulticode.modules.submission.event.SubmissionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of {@link ContestAdjudicationService}. Wired as the
 * side-effect target of the AFTER_COMMIT
 * {@link com.ulticode.modules.contest.listener.ContestAdjudicationListener}.
 *
 * <p>Idempotent and re-entrant: {@link #applyJudgeResult} is safe to call
 * twice for the same input without producing double-counting. This is
 * required because the {@link SubmissionJudgedEvent} can be replayed (e.g. on
 * listener retry, on transaction-rollback + re-commit) without the upstream
 * {@code submissions} row being rewritten.
 *
 * <p>This is the deep adjudication module: verdict application, first-solve
 * claiming, participant aggregates, and ranking-cache invalidation all live
 * here as named depth. The public seam is one method; the seven mapper
 * interactions, the first-solve race, the aggregate bookkeeping, and the
 * cache eviction are each a single-responsibility step below. Contest
 * lifecycle transitions and cascade cleanup belong to
 * {@link ContestLifecycleServiceImpl}.
 *
 * <p>Preserves D-04 (AFTER_COMMIT post-judge scoring) and ADR-006 (scoring
 * mode + penalty-keyed wrong-submission handling); the structure deepens
 * without reopening either decision.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestAdjudicationServiceImpl implements ContestAdjudicationService {

    private static final int FIRST_SOLVE_BONUS = 10;
    private static final int DEFAULT_PENALTY_PER_WRONG = 20;

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper contestParticipantMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final ContestProblemResultMapper contestProblemResultMapper;
    private final FirstSolveRecordMapper firstSolveRecordMapper;
    private final ContestRankingCacheEvictor rankingCacheEvictor;
    private final Clock clock;
    private final ScoringStrategyResolver scoringStrategyResolver;

    @Override
    @Transactional
    public void applyJudgeResult(SubmissionJudgedEvent event) {
        Optional<ContestSubmission> csOpt = locateContestSubmission(event);
        if (csOpt.isEmpty()) {
            return;
        }
        ContestSubmission cs = csOpt.get();

        // The verdict is stamped before any load that can bail, matching the
        // legacy order: even if a downstream participant/problem/contest row
        // is missing, the contest_submission verdict still lands.
        stampVerdict(event);

        ContestParticipant participant = loadParticipant(cs.getParticipantId(), event);
        if (participant == null) {
            return;
        }
        ContestProblem contestProblem = loadContestProblem(cs.getContestProblemId(), event);
        if (contestProblem == null) {
            return;
        }
        Contest contest = loadContest(cs.getContestId(), event);
        if (contest == null) {
            return;
        }
        ScoringContext scoring = resolveScoring(contest);
        boolean accepted = event.isAccepted();

        countAttempt(participant);
        if (accepted) {
            applyAccepted(cs, contestProblem, participant, event);
        } else {
            // ADR-006 §2.2: wrong-submission penalty is mode-keyed. SCORE and
            // IOI are no-ops; ICPC adds penaltyPerWrong. Default scoringMode
            // is SCORE, so unset contests keep the legacy "no penalty" path.
            scoring.strategy().applyWrongSubmission(participant, scoring.penaltyPerWrong());
        }
        advanceLastSolveTime(participant, cs, accepted);
        persistAggregates(participant, cs);
        rankingCacheEvictor.evictRankingCache();

        log.info("Applied contest scoring: contest={} user={} problem={} accepted={} score={} attempts={}",
                cs.getContestId(), event.getUserId(), contestProblem.getProblemId(), accepted,
                participant.getTotalScore(), participant.getAttemptCount());
    }

    // ─── Resolve step ──────────────────────────────────────────────────

    /**
     * Locate the contest_submission row the submit pipeline created for this
     * judged event. Empty when the event is malformed or the submission is
     * not part of any contest.
     */
    private Optional<ContestSubmission> locateContestSubmission(SubmissionJudgedEvent event) {
        if (event == null || event.getSubmissionId() == null) {
            return Optional.empty();
        }
        return contestSubmissionMapper.findBySubmissionId(event.getSubmissionId());
    }

    /**
     * Stamp {@code is_accepted} on the contest_submission row (idempotent).
     */
    private void stampVerdict(SubmissionJudgedEvent event) {
        contestSubmissionMapper.markAcceptedBySubmissionId(event.getSubmissionId(), event.isAccepted());
    }

    // ─── Load step ─────────────────────────────────────────────────────

    private ContestParticipant loadParticipant(String participantId, SubmissionJudgedEvent event) {
        ContestParticipant participant = contestParticipantMapper.selectById(participantId);
        if (participant == null) {
            log.warn("Contest scoring: participant {} missing for submission {}",
                    participantId, event.getSubmissionId());
        }
        return participant;
    }

    private ContestProblem loadContestProblem(String contestProblemId, SubmissionJudgedEvent event) {
        ContestProblem contestProblem = contestProblemMapper.selectById(contestProblemId);
        if (contestProblem == null) {
            log.warn("Contest scoring: contest_problem {} missing for submission {}",
                    contestProblemId, event.getSubmissionId());
        }
        return contestProblem;
    }

    private Contest loadContest(String contestId, SubmissionJudgedEvent event) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            log.warn("Contest scoring: contest {} missing for submission {}",
                    contestId, event.getSubmissionId());
        }
        return contest;
    }

    // ─── Scoring policy ────────────────────────────────────────────────

    /**
     * ADR-006 scoring context for one verdict: the strategy selected from the
     * contest's scoring mode, and the per-wrong-submission penalty (null
     * tolerates as the legacy hardcoded default).
     */
    private record ScoringContext(ScoringStrategy strategy, int penaltyPerWrong) {
    }

    private ScoringContext resolveScoring(Contest contest) {
        ScoringStrategy strategy = scoringStrategyResolver.resolveFromString(contest.getScoringMode());
        int penaltyPerWrong = contest.getPenaltyPerWrong() != null
                ? contest.getPenaltyPerWrong()
                : DEFAULT_PENALTY_PER_WRONG;
        return new ScoringContext(strategy, penaltyPerWrong);
    }

    // ─── Aggregate bookkeeping ─────────────────────────────────────────

    /**
     * Every submission, accepted or not, counts as one attempt.
     */
    private void countAttempt(ContestParticipant participant) {
        int newAttempts = (participant.getAttemptCount() == null ? 0 : participant.getAttemptCount()) + 1;
        participant.setAttemptCount(newAttempts);
        participant.setTotalAttempts(newAttempts);
    }

    /**
     * Accepted branch: record or update the per-problem result row (idempotent
     * via the (participant, contest_problem) unique key), then race for the
     * first-solve bonus.
     */
    private void applyAccepted(ContestSubmission cs, ContestProblem contestProblem,
                               ContestParticipant participant, SubmissionJudgedEvent event) {
        String contestId = cs.getContestId();
        String contestProblemId = cs.getContestProblemId();
        String participantId = cs.getParticipantId();

        Optional<ContestProblemResult> existing =
                contestProblemResultMapper.findByParticipantIdAndContestProblemId(participantId, contestProblemId);
        boolean firstSolveOnThisProblem = existing.isEmpty();
        if (firstSolveOnThisProblem) {
            ContestProblemResult cpr = new ContestProblemResult();
            cpr.setContestId(contestId);
            cpr.setContestProblemId(contestProblemId);
            cpr.setUserId(event.getUserId());
            cpr.setParticipantId(participantId);
            cpr.setIsSolved(true);
            cpr.setScore(contestProblem.getScore() == null ? 100 : contestProblem.getScore());
            cpr.setAttempts(1);
            cpr.setFirstSolveTime(cs.getTimeFromStart());
            cpr.setBestSubmissionId(event.getSubmissionId());
            cpr.setTimeSpent(cs.getTimeFromStart() == null ? 0 : cs.getTimeFromStart());
            cpr.setTimeBonus(0);
            cpr.setIsFirstSolve(false); // updated below if confirmed first-solver
            contestProblemResultMapper.insert(cpr);

            addScore(participant, cpr.getScore());
        } else {
            ContestProblemResult cpr = existing.get();
            cpr.setAttempts((cpr.getAttempts() == null ? 0 : cpr.getAttempts()) + 1);
            cpr.setBestSubmissionId(event.getSubmissionId());
            contestProblemResultMapper.updateById(cpr);
        }

        if (claimFirstSolve(cs, contestProblem, event) && firstSolveOnThisProblem) {
            applyFirstSolveBonus(participant, participantId, contestProblemId);
        }
    }

    /**
     * Atomically claim first-solve via the {@code first_solve_records}
     * (contest_id, problem_id) unique key. A duplicate-key means another
     * participant solved it first; the lost race is a no-op, never an error.
     */
    private boolean claimFirstSolve(ContestSubmission cs, ContestProblem contestProblem, SubmissionJudgedEvent event) {
        try {
            FirstSolveRecord firstSolve = new FirstSolveRecord();
            firstSolve.setContestId(cs.getContestId());
            firstSolve.setProblemId(contestProblem.getProblemId());
            firstSolve.setUserId(event.getUserId());
            firstSolve.setSolvedAt(LocalDateTime.now(clock));
            firstSolve.setTimeSpent(cs.getTimeFromStart() == null ? 0 : cs.getTimeFromStart());
            firstSolveRecordMapper.insert(firstSolve);
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    /**
     * Flip the per-problem result's first-solve flag and award the bonus to
     * both the result row and the participant aggregate.
     */
    private void applyFirstSolveBonus(ContestParticipant participant, String participantId, String contestProblemId) {
        contestProblemResultMapper.findByParticipantIdAndContestProblemId(participantId, contestProblemId)
                .ifPresent(r -> {
                    r.setIsFirstSolve(true);
                    r.setTimeBonus(FIRST_SOLVE_BONUS);
                    r.setScore(r.getScore() + FIRST_SOLVE_BONUS);
                    contestProblemResultMapper.updateById(r);
                });
        addScore(participant, FIRST_SOLVE_BONUS);
    }

    private void addScore(ContestParticipant participant, int delta) {
        int newScore = (participant.getTotalScore() == null ? 0 : participant.getTotalScore()) + delta;
        participant.setTotalScore(newScore);
    }

    /**
     * lastSolveTime only advances on an accepted submission with a real time.
     */
    private void advanceLastSolveTime(ContestParticipant participant, ContestSubmission cs, boolean accepted) {
        if (accepted && cs.getTimeFromStart() != null) {
            participant.setLastSolveTime(cs.getTimeFromStart());
        }
    }

    /**
     * Persist the participant aggregate, bump the contest submission count,
     * and bump the contest participant count on the user's first submission
     * for this contest.
     */
    private void persistAggregates(ContestParticipant participant, ContestSubmission cs) {
        String contestId = cs.getContestId();
        String participantId = cs.getParticipantId();
        contestParticipantMapper.updateById(participant);
        contestMapper.incrementSubmissionCount(contestId);
        if (contestSubmissionMapper.findByContestIdAndParticipantId(contestId, participantId).size() == 1) {
            contestMapper.incrementParticipantCount(contestId);
        }
    }
}
