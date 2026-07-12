package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestProblemResultMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.FirstSolveRecordMapper;
import com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor;
import com.ulticode.modules.contest.service.ContestLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link ContestLifecycleService}. Owns the contest-level
 * participant status transitions driven by the
 * {@link com.ulticode.modules.contest.scheduler.ContestScheduler} and the
 * relational cleanup invoked when a contest is soft-deleted.
 *
 * <p>This module never scores a verdict — that is the deep
 * {@link ContestAdjudicationServiceImpl}'s job. The two share only the
 * {@link ContestRankingCacheEvictor}, because both a verdict and a cascade
 * delete can change what the ranking cache reflects.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestLifecycleServiceImpl implements ContestLifecycleService {

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper contestParticipantMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final ContestProblemResultMapper contestProblemResultMapper;
    private final FirstSolveRecordMapper firstSolveRecordMapper;
    private final ContestRankingCacheEvictor rankingCacheEvictor;
    private final Clock clock;

    @Override
    @Transactional
    public int batchStartParticipants(String contestId) {
        LocalDateTime now = LocalDateTime.now(clock);
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
        LocalDateTime now = LocalDateTime.now(clock);
        List<ContestParticipant> toFinish = contestParticipantMapper.findVirtualParticipantsToFinish(now);
        if (toFinish.isEmpty()) {
            return 0;
        }
        // M2: replace per-row UPDATE with a single bulk UPDATE keyed by id
        // (the result list is bounded by the 10s scheduler tick, typically
        // small, but previously this was N+1 UPDATEs).
        Set<String> ids = new HashSet<>();
        for (ContestParticipant p : toFinish) {
            ids.add(p.getId());
        }
        int total = contestParticipantMapper.bulkFinishByIds(ids, now);
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
        rankingCacheEvictor.evictRankingCache();
        log.info("P2-5: cascade-deleted relational rows for soft-deleted contest {}", contestId);
    }
}
