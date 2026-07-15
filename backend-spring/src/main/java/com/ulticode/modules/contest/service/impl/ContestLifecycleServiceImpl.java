package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.clock.ContestClock;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestProblemResultMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.FirstSolveRecordMapper;
import com.ulticode.modules.contest.port.ContestRankingMarkDirtyPort;
import com.ulticode.modules.contest.port.ContestStatusPushPort;
import com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor;
import com.ulticode.modules.contest.service.ContestLifecycleService;
import com.ulticode.modules.contest.service.RatingCalculationService;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.ContestStartingIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link ContestLifecycleService}. Owns every time-driven
 * contest lifetime policy: the 10-second {@link #tick(LocalDateTime)}
 * heartbeat (due selection + idempotent state transitions + participant
 * closure + push/ranking side effects + rating handoff), the per-minute
 * {@link #sendReminders(LocalDateTime)} fan-out, and the relational cleanup
 * invoked when a contest is soft-deleted.
 *
 * <p>The {@link com.ulticode.modules.contest.scheduler.ContestScheduler} is a
 * thin trigger adapter that delegates here, so the lifecycle invariants
 * concentrate in this seam and are directly testable with a deterministic
 * {@link Clock}.
 *
 * <p>This module never scores a verdict — that is the deep
 * {@link ContestAdjudicationServiceImpl}'s job. The two share only the
 * {@link ContestRankingCacheEvictor}, because both a verdict and a cascade
 * delete can change what the ranking cache reflects. Rating recalculation is
 * handed off to {@link RatingCalculationService} once a contest is FINISHED.
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
    private final ContestClock contestClock;
    private final ContestStatusPushPort contestStatusPushPort;
    private final ContestRankingMarkDirtyPort contestRankingMarkDirtyPort;
    private final RatingCalculationService ratingService;
    private final NotificationDispatcher notificationDispatcher;

    /**
     * Self reference resolved through the Spring proxy so {@link #tick} and
     * the private transition helpers can invoke {@link #batchStartParticipants}
     * / {@link #autoFinishVirtualParticipants} via the proxy and keep their
     * {@code @Transactional} semantics (direct {@code this} calls bypass the
     * proxy and silently drop the transaction). {@code @Lazy} breaks the
     * construction-time self-dependency.
     */
    @Autowired
    @Lazy
    private ContestLifecycleService self;

    /** Test seam: inject the self proxy (or a spy for unit tests). */
    void setSelf(ContestLifecycleService self) {
        this.self = self;
    }

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

    @Override
    public void tick(LocalDateTime now) {
        // Step 1: transition due UPCOMING contests to RUNNING.
        List<Contest> upcoming = contestMapper.findByStatus(ContestStatus.UPCOMING.name());
        for (Contest contest : upcoming) {
            if (contest.getStartTime() != null && !contest.getStartTime().isAfter(now)) {
                transitionToRunning(contest, now);
            }
        }

        // Step 2: transition due RUNNING contests to FINISHED.
        List<Contest> running = contestMapper.findByStatus(ContestStatus.RUNNING.name());
        for (Contest contest : running) {
            LocalDateTime effectiveEndTime = contestClock.contestEndTime(contest).orElse(null);
            if (effectiveEndTime != null && !effectiveEndTime.isAfter(now)) {
                transitionToFinished(contest, now);
            }
        }

        // Step 3 (R3.1): auto-finish expired virtual participants whose
        // virtual_end_time has passed, even if the user is offline. Fault-
        // isolated so a virtual-finish failure never blocks the next tick.
        try {
            int finished = self.autoFinishVirtualParticipants();
            if (finished > 0) {
                log.info("R3.1: auto-finished {} expired virtual participants", finished);
            }
        } catch (Exception e) {
            log.warn("R3.1 autoFinishVirtualParticipants failed: {}", e.getMessage());
        }
    }

    @Override
    public void sendReminders(LocalDateTime now) {
        // Find UPCOMING contests starting in 24-25 hours from now (T-24h window)
        LocalDateTime window24hStart = now.plusHours(24);
        LocalDateTime window24hEnd = now.plusHours(25);
        List<Contest> contests24h = contestMapper.findByStatus(ContestStatus.UPCOMING.name()).stream()
                .filter(c -> c.getStartTime() != null
                        && !c.getStartTime().isBefore(window24hStart)
                        && c.getStartTime().isBefore(window24hEnd))
                .toList();

        // Find UPCOMING contests starting in 1-2 hours from now (T-1h window)
        LocalDateTime window1hStart = now.plusHours(1);
        LocalDateTime window1hEnd = now.plusHours(2);
        List<Contest> contests1h = contestMapper.findByStatus(ContestStatus.UPCOMING.name()).stream()
                .filter(c -> c.getStartTime() != null
                        && !c.getStartTime().isBefore(window1hStart)
                        && c.getStartTime().isBefore(window1hEnd))
                .toList();

        // Process T-24h reminders
        if (!contests24h.isEmpty()) {
            List<String> contestIds24h = contests24h.stream().map(Contest::getId).toList();
            List<ContestParticipant> participants24h = contestParticipantMapper.findByContestIds(contestIds24h);
            for (ContestParticipant p : participants24h) {
                sendContestReminder(p, "24h", contests24h);
            }
        }

        // Process T-1h reminders
        if (!contests1h.isEmpty()) {
            List<String> contestIds1h = contests1h.stream().map(Contest::getId).toList();
            List<ContestParticipant> participants1h = contestParticipantMapper.findByContestIds(contestIds1h);
            for (ContestParticipant p : participants1h) {
                sendContestReminder(p, "1h", contests1h);
            }
        }
    }

    /**
     * Dispatch a single contest-start reminder as a typed
     * {@link ContestStartingIntent}. The 24h and 1h paths carry distinct
     * {@code reminderType} values, so the intent id differs and the
     * dispatcher ledger dedups them independently. Fire-and-forget per D-13:
     * a delivery failure for one participant never aborts the rest.
     */
    private void sendContestReminder(ContestParticipant participant, String reminderType,
                                     List<Contest> matchingContests) {
        Contest contest = matchingContests.stream()
                .filter(c -> c.getId().equals(participant.getContestId()))
                .findFirst()
                .orElse(null);
        if (contest == null) {
            return;
        }
        try {
            notificationDispatcher.dispatch(
                    ContestStartingIntent.of(contest, participant, reminderType));
            log.debug("Sent {} reminder for contest {} to user {}",
                    reminderType, contest.getId(), participant.getUserId());
        } catch (Exception e) {
            log.warn("Failed to send {} reminder for contest {} to user {}: {}",
                    reminderType, contest.getId(), participant.getUserId(), e.getMessage());
        }
    }

    /**
     * Idempotently transition a contest to RUNNING: stamp the actual start
     * time, batch-start REGISTERED participants (P0-2), broadcast the WS
     * status, and mark the ranking dirty so the initial leaderboard appears.
     */
    private void transitionToRunning(Contest contest, LocalDateTime now) {
        // Idempotent: skip if already RUNNING.
        if (ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            return;
        }
        contest.setStatus(ContestStatus.RUNNING.name());
        contest.setActualStartTime(now);
        contestMapper.updateById(contest);

        // P0-2: batch-transition REGISTERED participants to STARTED so they can
        // submit. Runs after contest.status is committed; transitions are
        // idempotent (only REGISTERED rows are touched).
        try {
            int started = self.batchStartParticipants(contest.getId());
            if (started > 0) {
                log.info("P0-2: started {} participants for contest {}", started, contest.getId());
            }
        } catch (Exception e) {
            log.warn("P0-2 batchStartParticipants failed for contest {}: {}",
                    contest.getId(), e.getMessage());
        }

        // Emit WebSocket status (via ContestStatusPushPort; the adapter maps
        // contest.entity.enums.ContestStatus.RUNNING to the wire-format enum)
        contestStatusPushPort.emitStatus(
                contest.getId(),
                ContestStatus.RUNNING,
                contest.getActualStartTime() != null
                        ? contest.getActualStartTime().atZone(ZoneId.systemDefault()).toInstant()
                        : null,
                null,
                null
        );

        // Mark dirty so initial ranking appears on leaderboard
        contestRankingMarkDirtyPort.markDirty(contest.getId());

        log.info("Contest {} transitioned to RUNNING", contest.getId());
    }

    /**
     * Idempotently transition a contest to FINISHED: stamp the actual end
     * time, close all real (non-virtual) participants (R3.1) so the rating
     * calculation sees a stable set, broadcast the WS status, and hand off to
     * the rating service.
     */
    private void transitionToFinished(Contest contest, LocalDateTime now) {
        // Idempotent: skip if already FINISHED.
        if (ContestStatus.FINISHED.name().equals(contest.getStatus())) {
            return;
        }
        contest.setStatus(ContestStatus.FINISHED.name());
        contest.setActualEndTime(now);
        contestMapper.updateById(contest);

        // R3.1: close all real (is_virtual=0) participants so the rating
        // calculation below sees a stable, FINISHED set. Virtual participants
        // are managed by the per-user virtual session, not the contest clock.
        try {
            int finished = contestParticipantMapper.finishStartedRealParticipants(contest.getId(), now);
            if (finished > 0) {
                log.info("R3.1: closed {} real participants for contest {}", finished, contest.getId());
            }
        } catch (Exception e) {
            log.warn("R3.1 finishStartedRealParticipants failed for contest {}: {}",
                    contest.getId(), e.getMessage());
        }

        // Emit WebSocket status (adapter maps contest.FINISHED to wire.ENDED)
        contestStatusPushPort.emitStatus(
                contest.getId(),
                ContestStatus.FINISHED,
                null,
                contest.getActualEndTime() != null
                        ? contest.getActualEndTime().atZone(ZoneId.systemDefault()).toInstant()
                        : null,
                null
        );

        // Trigger rating calculation
        ratingService.calculateAndUpdate(contest.getId());

        log.info("Contest {} transitioned to FINISHED, ratings calculated", contest.getId());
    }
}
