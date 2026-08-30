package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.clock.ContestClock;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestAdjudicationReceiptMapper;
import com.ulticode.modules.contest.mapper.ContestCascadeMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.app.api.service.ContestRankingMarkDirtyPort;
import com.ulticode.app.error.ContestErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.api.service.ContestStatusPushPort;
import com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor;
import com.ulticode.modules.contest.service.ContestLifecycleService;
import com.ulticode.modules.contest.service.ContestParticipantTransitions;
import com.ulticode.modules.contest.service.RatingCalculationService;
import com.ulticode.app.api.service.ContestNotificationPort;
import com.ulticode.submission.api.dto.SubmissionAdjudicationFact;
import com.ulticode.submission.api.service.SubmissionAdjudicationReadPort;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 * handed off to {@link RatingCalculationService} while a contest is FINISHING,
 * before FINISHED is published.
 *
 * <p>Architectural boundary: this service does NOT depend on
 * {@code ContestParticipantMapper}. All {@link ContestParticipant} status
 * transitions and participant deletion cross the
 * {@link ContestParticipantTransitions} seam so the canonical status
 * literals, input hygiene, and atomic SQL guard boundary stay in one
 * deep module. Contest-level locking/state writes cross {@link ContestMapper};
 * all contest-owned relation deletes cross {@link ContestCascadeMapper}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestLifecycleServiceImpl implements ContestLifecycleService {

    private static final int SUBMISSION_FACT_BATCH_SIZE = 100;

    private final ContestMapper contestMapper;
    private final ContestParticipantTransitions participantTransitions;
    private final ContestCascadeMapper contestCascadeMapper;
    private final ContestRankingCacheEvictor rankingCacheEvictor;
    private final Clock clock;
    private final ContestClock contestClock;
    private final ContestStatusPushPort contestStatusPushPort;
    private final ContestRankingMarkDirtyPort contestRankingMarkDirtyPort;
    private final ContestNotificationPort contestNotificationPort;
    private final RatingCalculationService ratingService;
    private final ContestAdjudicationReceiptMapper adjudicationReceiptMapper;
    private final SubmissionAdjudicationReadPort submissionAdjudicationReadPort;

    @Override
    @Transactional
    public int batchStartParticipants(String contestId) {
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = participantTransitions.batchStartRegistered(contestId, now);
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
        int total = participantTransitions.findAndFinishExpiredVirtuals(now);
        if (total > 0) {
            log.info("P2-2: auto-finished {} virtual participants past their duration", total);
        }
        return total;
    }

    @Override
    @Transactional
    public void deleteContestCascade(String contestId, String deletedBy) {
        Contest contest = contestMapper.selectByIdIncludingDeletedForUpdate(contestId);
        if (contest == null) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(contest.getIsDeleted())
                && !ContestStatus.UPCOMING.name().equals(contest.getStatus())
                && !ContestStatus.FINISHED.name().equals(contest.getStatus())) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_FOUND);
        }

        // Receipts must be removed before their contest_submission mapping.
        contestCascadeMapper.deleteAdjudicationReceiptsByContestId(contestId);
        contestCascadeMapper.deleteProblemResultsByContestId(contestId);
        contestCascadeMapper.deleteSubmissionsByContestId(contestId);
        contestCascadeMapper.deleteFirstSolveRecordsByContestId(contestId);
        contestCascadeMapper.deleteRankingsByContestId(contestId);
        contestCascadeMapper.deleteAnalyticsByContestId(contestId);
        contestCascadeMapper.deleteAnnouncementsByContestId(contestId);
        contestCascadeMapper.deleteRatingCalculationsByContestId(contestId);
        participantTransitions.deleteAllByContestId(contestId);
        contestCascadeMapper.deleteVirtualSessionsByContestId(contestId);
        contestCascadeMapper.deleteProblemsByContestId(contestId);

        if (!Boolean.TRUE.equals(contest.getIsDeleted())) {
            contest.setIsDeleted(true);
            contest.setDeletedAt(LocalDateTime.now(clock));
            contest.setDeletedBy(deletedBy);
            contestMapper.updateById(contest);
        }
        rankingCacheEvictor.evictRankingCache();
        log.info("P2-5: cascade-deleted relational rows for soft-deleted contest {}", contestId);
    }

    @Override
    @Transactional
    public void tick(LocalDateTime now) {
        // Step 1: transition due UPCOMING contests to RUNNING.
        List<Contest> upcoming = contestMapper.findByStatus(ContestStatus.UPCOMING.name());
        for (Contest contest : upcoming) {
            if (contest.getStartTime() != null && !contest.getStartTime().isAfter(now)) {
                try {
                    transitionToRunning(contest, now);
                } catch (Exception e) {
                    // Fault-isolate per contest so one transition failure does
                    // not starve the rest of the tick's queue.
                    log.error("transition to RUNNING failed for contest {}", contest.getId(), e);
                }
            }
        }

        // Step 2: claim due RUNNING contests as FINISHING. The claim is the
        // only one-way state change here; all finalization work remains
        // retryable while the row is FINISHING.
        List<Contest> running = contestMapper.findByStatus(ContestStatus.RUNNING.name());
        for (Contest contest : running) {
            // Recovery path for a process crash after the RUNNING claim but
            // before REGISTERED participants were batch-started.
            try {
                int started = batchStartParticipants(contest.getId());
                if (started > 0) {
                    contestStatusPushPort.emitStatus(
                            contest.getId(), ContestStatus.RUNNING.name(),
                            contest.getActualStartTime() == null ? null
                                    : contest.getActualStartTime().atZone(ZoneId.systemDefault())
                                    .toInstant().toEpochMilli(),
                            null, null);
                    contestRankingMarkDirtyPort.markDirty(contest.getId());
                }
            } catch (RuntimeException e) {
                log.error("participant start recovery failed for contest {}", contest.getId(), e);
                continue;
            }

            LocalDateTime effectiveEndTime = contestClock.contestEndTime(contest).orElse(null);
            if (effectiveEndTime != null && !effectiveEndTime.isAfter(now)) {
                try {
                    transitionToFinishing(contest, now);
                } catch (Exception e) {
                    log.error("claim FINISHING failed for contest {}", contest.getId(), e);
                }
            }
        }

        // Step 3: retry every claimed contest. This query is also the crash-
        // recovery path: a process that died after the FINISHING claim leaves
        // the row here for the next tick or application restart.
        List<Contest> finishing = contestMapper.findByStatus(ContestStatus.FINISHING.name());
        for (Contest contest : finishing) {
            try {
                finalizeFinishing(contest, now);
            } catch (RuntimeException e) {
                log.error("finalization failed for contest {}", contest.getId(), e);
            }
        }

        // Step 4 (R3.1): auto-finish expired virtual participants whose
        // virtual_end_time has passed, even if the user is offline. Fault-
        // isolated so a virtual-finish failure never blocks the next tick.
        try {
            int finished = this.autoFinishVirtualParticipants();
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

        // Process T-24h reminders. The participant list comes from the
        // transitions seam so the lifecycle module has no direct dependency
        // on the participant mapper.
        if (!contests24h.isEmpty()) {
            List<String> contestIds24h = contests24h.stream().map(Contest::getId).toList();
            List<ContestParticipant> participants24h =
                    participantTransitions.findByContestIdsForReminder(contestIds24h);
            for (ContestParticipant p : participants24h) {
                sendContestReminder(p, "24h", contests24h);
            }
        }

        // Process T-1h reminders
        if (!contests1h.isEmpty()) {
            List<String> contestIds1h = contests1h.stream().map(Contest::getId).toList();
            List<ContestParticipant> participants1h =
                    participantTransitions.findByContestIdsForReminder(contestIds1h);
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
            contestNotificationPort.notifyContestStarting(
                    participant.getUserId(), contest.getId(), contest.getTitle(),
                    contest.getStartTime(), reminderType);
            log.debug("Sent {} reminder for contest {} to user {}",
                    reminderType, contest.getId(), participant.getUserId());
        } catch (Exception e) {
            log.warn("Failed to send {} reminder for contest {} to user {}: {}",
                    reminderType, contest.getId(), participant.getUserId(), e.getMessage());
        }
    }

    /**
     * Idempotently transition a contest to RUNNING via a conditional UPDATE
     * (WHERE status=UPCOMING). The affected-row count is the concurrency
     * invariant: 0 means another replica or an earlier tick already moved it,
     * so this caller skips all side effects. On a successful claim the
     * REGISTERED participants are batch-started (P0-2), the WS status is
     * broadcast, and the ranking is marked dirty. A participant-start failure
     * is rethrown rather than swallowed so the scheduler records the partial
     * transition instead of proceeding to emit/markDirty on half-applied state.
     */
    private void transitionToRunning(Contest contest, LocalDateTime now) {
        int transitioned = contestMapper.tryTransitionToRunning(contest.getId(), now);
        if (transitioned == 0) {
            log.debug("contest {} no longer UPCOMING, skip RUNNING transition", contest.getId());
            return;
        }

        try {
            // P0-2: batch-transition REGISTERED participants to STARTED so they can
            // submit. Rethrows on failure — see method Javadoc.
            int started = batchStartParticipants(contest.getId());
            if (started > 0) {
                log.info("P0-2: started {} participants for contest {}", started, contest.getId());
            }

            contest.setStatus(ContestStatus.RUNNING.name());
            contest.setActualStartTime(now);
            contestStatusPushPort.emitStatus(
                    contest.getId(),
                    ContestStatus.RUNNING.name(),
                    now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    null,
                    null
            );

            // Mark dirty so initial ranking appears on leaderboard
            contestRankingMarkDirtyPort.markDirty(contest.getId());

            log.info("Contest {} transitioned to RUNNING", contest.getId());
        } catch (RuntimeException failure) {
            // The claim is committed before this private method's side effects.
            // Compensate so the next tick retries instead of skipping RUNNING.
            contestMapper.revertRunningToUpcoming(contest.getId(), now);
            throw failure;
        }
    }

    /**
     * Atomically claim a due contest as FINISHING. No side effect is performed
     * before this conditional update succeeds, so competing ticks cannot both
     * own the transition.
     */
    private void transitionToFinishing(Contest contest, LocalDateTime now) {
        int transitioned = contestMapper.tryTransitionToFinishing(contest.getId(), now);
        if (transitioned == 0) {
            return;
        }
        // Keep the in-memory row aligned for deterministic tests; the
        // persisted actual_end_time is the source of
        // truth for retries loaded from FINISHING.
        contest.setStatus(ContestStatus.FINISHING.name());
        if (contest.getActualEndTime() == null) {
            contest.setActualEndTime(now);
        }
    }

    /**
     * Retry-safe FINISHING finalizer. Every operation before the final
     * conditional update is either guarded by the database (participant close
     * and rating receipt) or is an at-least-once notification/dirty signal.
     * FINISHED is published only after all of them return successfully, so a
     * failure leaves the contest visible to the next tick.
     */
    private void finalizeFinishing(Contest contest, LocalDateTime now) {
        // Hold the parent lock across participant close, rating, and the
        // FINISHED claim. Adjudication takes the same lock first, so a late
        // verdict either lands before rating or reopens the published contest
        // for a retryable finalization pass.
        Contest locked = contestMapper.selectByIdForUpdate(contest.getId());
        if (locked != null) {
            if (!ContestStatus.FINISHING.name().equals(locked.getStatus())) {
                return;
            }
            contest = locked;
        }
        LocalDateTime endTime = contest.getActualEndTime() != null
                ? contest.getActualEndTime()
                : now;

        // Do not publish ratings while a real contest submission is still
        // pending or has a terminal user verdict without its receipt. The
        // parent lock serializes this drain check with adjudication; FINISHING
        // remains retryable until the judge pipeline has drained.
        if (countUnadjudicatedRealSubmissions(contest.getId()) > 0) {
            return;
        }

        // Close real participants first. Virtual sessions have their own
        // participant clock and are intentionally handled by step 4.
        int finished = participantTransitions.finishStartedReal(contest.getId(), endTime);
        if (finished > 0) {
            log.info("R3.1: closed {} real participants for contest {}", finished, contest.getId());
        }

        // RatingCalculationService owns its durable contest receipt, so a
        // retry after a committed rating update is a no-op.
        ratingService.calculateAndUpdate(contest.getId());
        contestRankingMarkDirtyPort.markDirty(contest.getId());

        // Emit before publishing FINISHED. A throwing adapter leaves the row
        // FINISHING and therefore retryable; duplicate delivery on a retry is
        // acceptable for this best-effort push seam.
        contestStatusPushPort.emitStatus(
                contest.getId(),
                ContestStatus.FINISHED.name(),
                null,
                endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                null
        );

        int transitioned = contestMapper.tryFinalizeFinished(contest.getId(), endTime);
        if (transitioned == 0) {
            return;
        }
    }

    /**
     * Combines App-owned contest associations/receipts with current
     * Submission-owner status and generation facts. No App SQL may join the
     * Submission-owned table.
     */
    private long countUnadjudicatedRealSubmissions(String contestId) {
        List<String> submissionIds = adjudicationReceiptMapper
                .findRealSubmissionIdsByContestId(contestId);
        if (submissionIds == null || submissionIds.isEmpty()) {
            return 0L;
        }
        submissionIds = submissionIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (submissionIds.isEmpty()) {
            return 0L;
        }
        List<SubmissionAdjudicationFact> facts = new ArrayList<>();
        List<ContestAdjudicationReceiptMapper.ReceiptGeneration> receipts = new ArrayList<>();
        for (int start = 0; start < submissionIds.size(); start += SUBMISSION_FACT_BATCH_SIZE) {
            List<String> batch = submissionIds.subList(start,
                    Math.min(start + SUBMISSION_FACT_BATCH_SIZE, submissionIds.size()));
            facts.addAll(safeList(submissionAdjudicationReadPort.findByIds(batch)));
            receipts.addAll(safeList(adjudicationReceiptMapper
                    .findReceiptGenerationsBySubmissionIds(batch)));
        }
        Map<String, SubmissionAdjudicationFact> factById = facts
                .stream()
                .filter(fact -> fact != null && fact.submissionId() != null)
                .collect(Collectors.toMap(SubmissionAdjudicationFact::submissionId,
                        fact -> fact, (left, right) -> right, HashMap::new));
        Set<String> adjudicated = receipts
                .stream()
                .filter(receipt -> receipt != null && receipt.submissionId() != null
                        && receipt.generation() != null)
                .map(receipt -> receipt.submissionId() + "\u0000" + receipt.generation())
                .collect(Collectors.toSet());

        long pending = 0L;
        for (String submissionId : submissionIds) {
            SubmissionAdjudicationFact fact = factById.get(submissionId);
            if (fact == null || fact.generation() == null) {
                pending++;
            } else if (awaitsAdjudication(fact.status())
                    && !adjudicated.contains(submissionId + "\u0000" + fact.generation())) {
                pending++;
            }
        }
        return pending;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean awaitsAdjudication(String wireStatus) {
        SubmissionStatus status = SubmissionStatus.fromDbName(wireStatus);
        return status == null || status.getKind() != SubmissionStatus.Kind.TERMINAL_INFRA;
    }
}
