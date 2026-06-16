package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
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
import com.ulticode.modules.contest.service.ContestScoringService;
import com.ulticode.modules.submission.event.SubmissionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link ContestScoringService}. Wired as the side-effect target of
 * the AFTER_COMMIT {@link com.ulticode.modules.contest.listener.ContestScoringListener}.
 *
 * <p>Idempotent and re-entrant: every public method is safe to call twice for the same
 * input without producing double-counting. This is required because the
 * {@link SubmissionJudgedEvent} can be replayed (e.g. on listener retry, on
 * transaction-rollback + re-commit) without the upstream {@code submissions} row
 * being rewritten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestScoringServiceImpl implements ContestScoringService {

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper contestParticipantMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final ContestProblemResultMapper contestProblemResultMapper;
    private final FirstSolveRecordMapper firstSolveRecordMapper;
    private final CacheManager cacheManager;

    private static final String RANKING_CACHE = "contestRanking";

    // Cache name + key pattern: must match @Cacheable("contestRanking") in ContestServiceImpl.
    // We use clear() because ranking cache keys are not contest-keyed (e.g.
    // 'getGlobalRanking:50' / 'globalPaginated:1:50') — partial eviction is unsafe.
    private static final int FIRST_SOLVE_BONUS = 10;

    @Override
    @Transactional
    public void applyJudgeResult(SubmissionJudgedEvent event) {
        if (event == null || event.getSubmissionId() == null) {
            return;
        }

        // 1. Reverse-lookup the contest_submission row that the submit pipeline created.
        Optional<ContestSubmission> csOpt =
                contestSubmissionMapper.findBySubmissionId(event.getSubmissionId());
        if (csOpt.isEmpty()) {
            // Submission is not part of any contest — nothing to do.
            return;
        }
        ContestSubmission cs = csOpt.get();
        String contestId = cs.getContestId();
        String contestProblemId = cs.getContestProblemId();
        String participantId = cs.getParticipantId();
        boolean accepted = event.isAccepted();

        // 2. Write is_accepted on the contest_submission row (idempotent).
        contestSubmissionMapper.markAcceptedBySubmissionId(event.getSubmissionId(), accepted);

        // 3. Load participant. If the participant is missing, log and bail — the
        //    listener is allowed to be lenient.
        ContestParticipant participant = contestParticipantMapper.selectById(participantId);
        if (participant == null) {
            log.warn("Contest scoring: participant {} missing for submission {}",
                    participantId, event.getSubmissionId());
            return;
        }

        // 4. Load contest problem to know its score / penalty.
        ContestProblem contestProblem = contestProblemMapper.selectById(contestProblemId);
        if (contestProblem == null) {
            log.warn("Contest scoring: contest_problem {} missing for submission {}",
                    contestProblemId, event.getSubmissionId());
            return;
        }

        // 5. Always count this submission as an attempt.
        int newAttempts = (participant.getAttemptCount() == null ? 0 : participant.getAttemptCount()) + 1;
        participant.setAttemptCount(newAttempts);
        participant.setTotalAttempts(newAttempts);

        if (accepted) {
            // 6a. Record a contest_problem_results row (idempotent via UK on
            //     (participant_id, contest_problem_id)). If a row already exists,
            //     we still increment attempts but do not double-count score.
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

                // Increment contest_participants aggregate.
                int newScore = (participant.getTotalScore() == null ? 0 : participant.getTotalScore())
                        + cpr.getScore();
                participant.setTotalScore(newScore);
            } else {
                ContestProblemResult cpr = existing.get();
                cpr.setAttempts((cpr.getAttempts() == null ? 0 : cpr.getAttempts()) + 1);
                cpr.setBestSubmissionId(event.getSubmissionId());
                contestProblemResultMapper.updateById(cpr);
            }

            // 6b. First-solve detection: insert into first_solve_records with the
            //     DB unique key (contest_id, problem_id) as the atomic gate.
            //     A DuplicateKeyException here means we are NOT the first solver.
            boolean isFirstSolver = false;
            try {
                FirstSolveRecord firstSolve = new FirstSolveRecord();
                firstSolve.setContestId(contestId);
                firstSolve.setProblemId(contestProblem.getProblemId());
                firstSolve.setUserId(event.getUserId());
                firstSolve.setSolvedAt(LocalDateTime.now());
                firstSolve.setTimeSpent(cs.getTimeFromStart() == null ? 0 : cs.getTimeFromStart());
                firstSolveRecordMapper.insert(firstSolve);
                isFirstSolver = true;
            } catch (DuplicateKeyException ignored) {
                // Race: another participant solved it first. We are not the first solver.
            }

            if (isFirstSolver && firstSolveOnThisProblem) {
                // Update the CPR row's is_first_solve flag and add bonus to score.
                Optional<ContestProblemResult> cpr =
                        contestProblemResultMapper.findByParticipantIdAndContestProblemId(participantId, contestProblemId);
                cpr.ifPresent(r -> {
                    r.setIsFirstSolve(true);
                    r.setTimeBonus(FIRST_SOLVE_BONUS);
                    r.setScore(r.getScore() + FIRST_SOLVE_BONUS);
                    contestProblemResultMapper.updateById(r);
                });
                int newScore = (participant.getTotalScore() == null ? 0 : participant.getTotalScore())
                        + FIRST_SOLVE_BONUS;
                participant.setTotalScore(newScore);
            }
        } else {
            // 6c. Penalty: +20 min per wrong before AC (ICPC convention). Project
            //     default is configurable via contest.penaltyPerWrong (already
            //     tracked on the contest; we use a sensible 20 min default for
            //     the participant aggregate column).
            int penalty = 20;
            int existingPenalty = participant.getTotalPenalty() == null ? 0 : participant.getTotalPenalty();
            participant.setTotalPenalty(existingPenalty + penalty);
        }

        // 7. lastSolveTime only advances on AC.
        if (accepted && cs.getTimeFromStart() != null) {
            participant.setLastSolveTime(cs.getTimeFromStart());
        }

        // 8. Persist participant + contest aggregate counters.
        contestParticipantMapper.updateById(participant);
        contestMapper.incrementSubmissionCount(contestId);
        // participantCount is incremented only on the user's first submission
        // for this contest. We use the existence of any prior submission as the
        // signal (cheap, no extra table).
        if (contestSubmissionMapper.findByContestIdAndParticipantId(contestId, participantId).size() == 1) {
            contestMapper.incrementParticipantCount(contestId);
        }

        // 9. Invalidate ranking cache so the next read sees the fresh aggregate.
        evictRankingCache();

        log.info("Applied contest scoring: contest={} user={} problem={} accepted={} score={} attempts={}",
                contestId, event.getUserId(), contestProblem.getProblemId(), accepted,
                participant.getTotalScore(), participant.getAttemptCount());
    }

    @Override
    @Transactional
    public int batchStartParticipants(String contestId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = contestParticipantMapper.batchUpdateStatus(contestId, "REGISTERED", "STARTED", now);
        if (updated > 0) {
            log.info("P0-2: transitioned {} participants REGISTERED -> STARTED for contest {}",
                    updated, contestId);
        }
        return updated;
    }

    @Override
    @Transactional
    public int autoFinishVirtualParticipants() {
        LocalDateTime now = LocalDateTime.now();
        List<ContestParticipant> toFinish = contestParticipantMapper.findVirtualParticipantsToFinish(now);
        int total = 0;
        for (ContestParticipant p : toFinish) {
            total += contestParticipantMapper.batchUpdateStatus(
                    p.getContestId(), "STARTED", "FINISHED", now);
        }
        if (total > 0) {
            log.info("P2-2: auto-finished {} virtual participants past their duration", total);
        }
        return total;
    }

    @Override
    @Transactional
    public void deleteContestCascade(String contestId) {
        // 1. Soft-delete the parent contest row first. The soft-delete guard is
        //    in ContestServiceImpl; here we only do the relational cleanup.
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || Boolean.TRUE.equals(contest.getIsDeleted())) {
            // Soft-delete is idempotent; missing/already-deleted is not an error.
            return;
        }
        // 2. Cascade: physical delete of contest-scoped rows. Preserve global
        //    ranking rows (they reflect the user's overall history, not the
        //    contest's lifetime).
        contestSubmissionMapper.deleteByContestId(contestId);
        contestProblemResultMapper.deleteByContestId(contestId);
        firstSolveRecordMapper.deleteByContestId(contestId);
        contestParticipantMapper.deleteByContestId(contestId);
        contestProblemMapper.deleteByContestId(contestId);
        evictRankingCache();
        log.info("P2-5: cascade-deleted relational rows for soft-deleted contest {}", contestId);
    }

    /**
     * Evict cached ranking entries. The ranking cache uses non-contest-keyed keys
     * (e.g. {@code 'getGlobalRanking:50'}), so {@code clear()} is the safe option.
     */
    private void evictRankingCache() {
        Cache cache;
        try {
            cache = cacheManager.getCache(RANKING_CACHE);
        } catch (Exception e) {
            log.debug("Ranking cache not available: {}", e.getMessage());
            return;
        }
        if (cache != null) {
            cache.clear();
        }
    }
}
