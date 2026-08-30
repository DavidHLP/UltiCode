package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.ContestProblemResult;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.contest.entity.FirstSolveRecord;
import com.ulticode.modules.contest.mapper.ContestAdjudicationReceiptMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestProblemResultMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.ScoringRuleMapper;
import com.ulticode.modules.contest.entity.ScoringRule;
import com.ulticode.modules.contest.mapper.FirstSolveRecordMapper;
import com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor;
import com.ulticode.modules.contest.scoring.ScoringStrategy;
import com.ulticode.modules.contest.scoring.ScoringStrategyResolver;
import com.ulticode.modules.contest.service.ContestAdjudicationService;
import com.ulticode.submission.api.event.SubmissionJudgedEvent;
import com.ulticode.submission.api.service.SubmissionGenerationReadPort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.common.uuid.UuidGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of {@link ContestAdjudicationService}. Wired as the
 * durable inbox consumer. The receipt fence makes the transaction safe to
 * retry after transport redelivery or worker failure.
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
 * <p>Preserves durable post-judge scoring and ADR-006 (scoring mode +
 * penalty-keyed wrong-submission handling); the structure deepens without
 * reopening either decision.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ContestAdjudicationServiceImpl implements ContestAdjudicationService {

    private static final int DEFAULT_FIRST_SOLVE_BONUS = 10;
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
    private final ContestAdjudicationReceiptMapper receiptMapper;
    private final UuidGenerator uuidGenerator;
    private final SubmissionGenerationReadPort submissionGenerationReadPort;
    private final ScoringRuleMapper scoringRuleMapper;

    /** Compatibility constructor for existing unit fixtures; production uses the full port graph. */
    public ContestAdjudicationServiceImpl(
            ContestMapper contestMapper,
            ContestParticipantMapper contestParticipantMapper,
            ContestProblemMapper contestProblemMapper,
            ContestSubmissionMapper contestSubmissionMapper,
            ContestProblemResultMapper contestProblemResultMapper,
            FirstSolveRecordMapper firstSolveRecordMapper,
            ContestRankingCacheEvictor rankingCacheEvictor,
            Clock clock,
            ScoringStrategyResolver scoringStrategyResolver,
            ContestAdjudicationReceiptMapper receiptMapper,
            UuidGenerator uuidGenerator) {
        this(contestMapper, contestParticipantMapper, contestProblemMapper,
                contestSubmissionMapper, contestProblemResultMapper, firstSolveRecordMapper,
                rankingCacheEvictor, clock, scoringStrategyResolver, receiptMapper,
                uuidGenerator, submissionId -> 1L, null);
    }

    @Override
    @Transactional
    public void applyJudgeResult(SubmissionJudgedEvent event) {
        if (event == null) {
            return;
        }
        SubmissionStatus status = SubmissionStatus.fromWire(event.getVerdict());
        if (!status.isTerminal() || status.getKind() == SubmissionStatus.Kind.TERMINAL_INFRA) {
            return;
        }
        boolean accepted = status == SubmissionStatus.ACCEPTED;

        Optional<ContestSubmission> csOpt = locateContestSubmission(event);
        if (csOpt.isEmpty()) {
            return;
        }
        ContestSubmission candidate = csOpt.get();

        // Contest deletion locks the parent before removing any child rows.
        // Acquire that same App-side lock first. The owner generation read
        // below is a stale-event guard, not a lock held across the RPC.
        Contest contest = loadContest(candidate.getContestId(), event);
        if (contest == null) {
            return;
        }
        csOpt = contestSubmissionMapper.findBySubmissionIdForUpdate(event.getSubmissionId());
        if (csOpt.isEmpty()) {
            return;
        }
        ContestSubmission cs = csOpt.get();

        long generation = event.getGeneration() > 0 ? event.getGeneration() : 1L;
        // Submission owner commits the fenced terminal verdict before
        // publishing this event and rejects rejudge for contest submissions;
        // this read therefore drops stale/duplicate events without pretending
        // to hold an owner-row lock across the RPC boundary.
        Long currentGeneration = submissionGenerationReadPort.findGenerationForUpdate(event.getSubmissionId());
        if (currentGeneration == null || currentGeneration.longValue() != generation) {
            return;
        }
        long latestGeneration = receiptMapper.findMaxGenerationForSubmissionForUpdate(
                event.getSubmissionId()).orElse(0L);
        // A newer rejudge needs a replacement ledger to subtract the prior
        // verdict's contribution. Until that ledger exists, accepting it would
        // double-count score, penalty, attempts, and problem counters.
        if (latestGeneration > 0) {
            return;
        }

        ContestParticipant participant = loadParticipant(cs.getParticipantId(), event);
        if (participant == null) {
            return;
        }
        if ("FINISHED".equals(contest.getStatus()) && !Boolean.TRUE.equals(participant.getIsVirtual())) {
            // FINISHED is the adjudication cutoff for real participants. The
            // lifecycle transaction holds the same parent lock through rating
            // and publication. Virtual replays remain eligible on their own
            // session clock after the parent contest has finished.
            return;
        }
        ContestProblem contestProblem = loadContestProblem(cs.getContestProblemId(), event);
        if (contestProblem == null) {
            return;
        }

        if (receiptMapper.insertIfAbsent(
                uuidGenerator.newId(), event.getSubmissionId(), generation,
                event.getVerdict(), accepted) == 0) {
            return;
        }

        // Update the submission projection only after the generation fence and
        // durable receipt claim; an older replay must not regress is_accepted.
        stampVerdict(event, accepted);

        ScoringContext scoring = resolveScoring(contest);

        countAttempt(participant);
        boolean firstSolveOnThisProblem = false;
        if (accepted) {
            firstSolveOnThisProblem = applyAccepted(
                    cs, contestProblem, participant, event, scoring.firstSolveBonus());
        } else {
            // ADR-006 §2.2: wrong-submission penalty is mode-keyed. SCORE and
            // IOI are no-ops; ICPC adds penaltyPerWrong. Default scoringMode
            // is SCORE, so unset contests keep the legacy "no penalty" path.
            scoring.strategy().applyWrongSubmission(participant, scoring.penaltyPerWrong());
        }
        advanceLastSolveTime(participant, cs, firstSolveOnThisProblem);
        persistAggregates(participant, cs);
        if (!Boolean.TRUE.equals(participant.getIsVirtual())) {
            contestProblemMapper.incrementSubmissionCount(cs.getContestProblemId());
            if (accepted && firstSolveOnThisProblem) {
                contestProblemMapper.incrementSolvedCount(cs.getContestProblemId());
            }
        }
        rankingCacheEvictor.evictRankingCache();

        log.info("Applied contest scoring: contest={} user={} problem={} accepted={} score={} attempts={}",
                cs.getContestId(), participant.getUserId(), contestProblem.getProblemId(), accepted,
                participant.getTotalScore(), participant.getAttemptCount());
    }

    // ─── Resolve step ──────────────────────────────────────────────────

    /**
     * Locate the contest_submission row the submit pipeline created for this
     * judged event. Empty when the event is malformed or the submission is
     * not part of any contest.
     */
    private Optional<ContestSubmission> locateContestSubmission(SubmissionJudgedEvent event) {
        if (event.getSubmissionId() == null || event.getSubmissionId().isBlank()) {
            return Optional.empty();
        }
        return contestSubmissionMapper.findBySubmissionId(event.getSubmissionId());
    }

    /**
     * Stamp {@code is_accepted} on the contest_submission row in the receipt
     * transaction.
     */
    private void stampVerdict(SubmissionJudgedEvent event, boolean accepted) {
        contestSubmissionMapper.markAcceptedBySubmissionId(event.getSubmissionId(), accepted);
    }

    // ─── Load step ─────────────────────────────────────────────────────

    private ContestParticipant loadParticipant(String participantId, SubmissionJudgedEvent event) {
        ContestParticipant participant = contestParticipantMapper.selectByIdForUpdate(participantId);
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
        Contest contest = contestMapper.selectByIdForUpdate(contestId);
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
    private record ScoringContext(ScoringStrategy strategy, int penaltyPerWrong, int firstSolveBonus) {
    }

    private ScoringContext resolveScoring(Contest contest) {
        ScoringStrategy strategy = scoringStrategyResolver.resolveFromString(contest.getScoringMode());
        int penaltyPerWrong = contest.getPenaltyPerWrong() != null
                ? contest.getPenaltyPerWrong()
                : DEFAULT_PENALTY_PER_WRONG;
        int firstSolveBonus = DEFAULT_FIRST_SOLVE_BONUS;
        if (scoringRuleMapper != null && contest.getScoringRuleId() != null) {
            ScoringRule rule = scoringRuleMapper.selectById(contest.getScoringRuleId());
            if (rule != null && rule.getFirstSolveBonus() != null) {
                firstSolveBonus = rule.getFirstSolveBonus();
            }
        }
        return new ScoringContext(strategy, penaltyPerWrong, firstSolveBonus);
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
    private boolean applyAccepted(ContestSubmission cs, ContestProblem contestProblem,
                                  ContestParticipant participant, SubmissionJudgedEvent event,
                                  int firstSolveBonus) {
        String contestId = cs.getContestId();
        String contestProblemId = cs.getContestProblemId();
        String participantId = cs.getParticipantId();

        Optional<ContestProblemResult> existing =
                contestProblemResultMapper.findByParticipantIdAndContestProblemIdForUpdate(participantId, contestProblemId);
        boolean firstSolveOnThisProblem = existing.isEmpty();
        if (firstSolveOnThisProblem) {
            ContestProblemResult cpr = new ContestProblemResult();
            cpr.setContestId(contestId);
            cpr.setContestProblemId(contestProblemId);
            cpr.setUserId(participant.getUserId());
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
            int solveTime = cs.getTimeFromStart() == null ? 0 : cs.getTimeFromStart();
            participant.setTotalTime((participant.getTotalTime() == null ? 0 : participant.getTotalTime())
                    + solveTime);
        } else {
            ContestProblemResult cpr = existing.get();
            cpr.setAttempts((cpr.getAttempts() == null ? 0 : cpr.getAttempts()) + 1);
            cpr.setBestSubmissionId(event.getSubmissionId());
            contestProblemResultMapper.updateById(cpr);
        }

        if (claimFirstSolve(cs, contestProblem, participant) && firstSolveOnThisProblem) {
            applyFirstSolveBonus(participant, participantId, contestProblemId, firstSolveBonus);
        }
        return firstSolveOnThisProblem;
    }

    /**
     * Atomically claim first-solve via the {@code first_solve_records}
     * (contest_id, problem_id) unique key. A duplicate-key means another
     * participant solved it first; the lost race is a no-op, never an error.
     */
    private boolean claimFirstSolve(ContestSubmission cs, ContestProblem contestProblem,
                                    ContestParticipant participant) {
        // Virtual replay scores are per-session and must not consume the
        // official contest-wide first-solve claim.
        if (Boolean.TRUE.equals(participant.getIsVirtual())) {
            return false;
        }
        try {
            FirstSolveRecord firstSolve = new FirstSolveRecord();
            firstSolve.setContestId(cs.getContestId());
            firstSolve.setProblemId(contestProblem.getProblemId());
            firstSolve.setUserId(participant.getUserId());
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
    private void applyFirstSolveBonus(ContestParticipant participant, String participantId,
                                      String contestProblemId, int firstSolveBonus) {
        contestProblemResultMapper.findByParticipantIdAndContestProblemIdForUpdate(participantId, contestProblemId)
                .ifPresent(r -> {
                    r.setIsFirstSolve(true);
                    r.setTimeBonus(firstSolveBonus);
                    r.setScore(r.getScore() + firstSolveBonus);
                    contestProblemResultMapper.updateById(r);
                });
        addScore(participant, firstSolveBonus);
    }

    private void addScore(ContestParticipant participant, int delta) {
        int newScore = (participant.getTotalScore() == null ? 0 : participant.getTotalScore()) + delta;
        participant.setTotalScore(newScore);
    }

    /**
     * lastSolveTime records the time of the participant's latest first solve.
     */
    private void advanceLastSolveTime(ContestParticipant participant, ContestSubmission cs,
                                      boolean firstSolveOnThisProblem) {
        if (firstSolveOnThisProblem && cs.getTimeFromStart() != null) {
            participant.setLastSolveTime(Math.max(
                    participant.getLastSolveTime() == null ? 0 : participant.getLastSolveTime(),
                    cs.getTimeFromStart()));
        }
    }

    /**
     * Persist the participant aggregate, bump the contest submission count,
     * and bump the contest participant count on the user's first adjudicated
     * submission for this contest. The participant row is locked for the whole
     * transaction, so the post-count attempt value is the authoritative first
     * adjudication guard; counting contest-submission rows races with delayed
     * judge events and can permanently miss the increment.
     */
    private void persistAggregates(ContestParticipant participant, ContestSubmission cs) {
        String contestId = cs.getContestId();
        contestParticipantMapper.updateById(participant);
        if (!Boolean.TRUE.equals(participant.getIsVirtual())) {
            contestMapper.incrementSubmissionCount(contestId);
        }
        if (!Boolean.TRUE.equals(participant.getIsVirtual())
                && Integer.valueOf(1).equals(participant.getAttemptCount())) {
            contestMapper.incrementParticipantCount(contestId);
        }
    }
}
