package com.ulticode.modules.contest.scheduler;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.RatingCalculationService;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.contest.port.ContestRankingMarkDirtyPort;
import com.ulticode.modules.contest.port.ContestStatusPushPort;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduler for contest lifecycle transitions.
 * Polls every 10 seconds to transition UPCOMING->RUNNING and RUNNING->FINISHED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestScheduler {

    private final ContestMapper contestMapper;
    private final ContestRankingMarkDirtyPort contestRankingMarkDirtyPort;
    private final ContestStatusPushPort contestStatusPushPort;
    private final RatingCalculationService ratingService;
    private final NotificationService notificationService;
    private final NotificationDispatchService notificationDispatchService;
    private final ContestParticipantMapper participantMapper;
    /**
     * ADR-004 M4c: typed intent dispatcher. Active when
     * {@code app.features.use-notification-intent=true}.
     */
    private final com.ulticode.modules.notification.dispatcher.NotificationDispatcher notificationDispatcher;
    private final com.ulticode.modules.submission.config.FeatureFlagsProperties featureFlags;
    private final Clock clock;

    @Scheduled(fixedRate = 10_000)
    public void run() {
        // Step 1: Find UPCOMING contests and transition those past start_time
        List<Contest> upcoming = contestMapper.findByStatus(
                com.ulticode.modules.contest.entity.enums.ContestStatus.UPCOMING.name());
        LocalDateTime now = LocalDateTime.now(clock);
        for (Contest contest : upcoming) {
            if (contest.getStartTime() != null && !contest.getStartTime().isAfter(now)) {
                transitionToRunning(contest);
            }
        }

        // Step 2: Find RUNNING contests and transition those past end_time
        List<Contest> running = contestMapper.findByStatus(
                com.ulticode.modules.contest.entity.enums.ContestStatus.RUNNING.name());
        for (Contest contest : running) {
            LocalDateTime effectiveEndTime = computeEffectiveEndTime(contest);
            if (effectiveEndTime != null && !effectiveEndTime.isAfter(now)) {
                transitionToFinished(contest);
            }
        }

        // Step 3 (R3.1): auto-finish expired virtual participants whose
        // virtual_end_time has passed, even if the user is offline. Runs
        // every 10s to keep latency low without overloading the DB.
        try {
            int finished = contestScoringService.autoFinishVirtualParticipants();
            if (finished > 0) {
                log.info("R3.1: auto-finished {} expired virtual participants", finished);
            }
        } catch (Exception e) {
            log.warn("R3.1 autoFinishVirtualParticipants failed: {}", e.getMessage());
        }
    }

    /**
     * R7.2 / F-31: sweep expired virtual sessions on app start so crash-recovery
     * leaves the participant table clean. Idempotent: ticks through 10s
     * scheduler will keep this state close to fresh anyway.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void sweepOnStartup() {
        try {
            int swept = contestScoringService.autoFinishVirtualParticipants();
            if (swept > 0) {
                log.info("R7.2 / F-31: startup sweep closed {} expired virtual sessions", swept);
            }
        } catch (Exception e) {
            // Never block startup; scheduler tick will retry.
            log.warn("R7.2 / F-31: startup sweep failed, will retry on next 10s tick: {}",
                    e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60_000)  // Check every minute
    public void sendContestReminders() {
        LocalDateTime now = LocalDateTime.now(clock);

        // Find UPCOMING contests starting in 24-25 hours from now (T-24h window)
        LocalDateTime window24hStart = now.plusHours(24);
        LocalDateTime window24hEnd = now.plusHours(25);
        List<Contest> contests24h = contestMapper.findByStatus(
                com.ulticode.modules.contest.entity.enums.ContestStatus.UPCOMING.name()).stream()
                .filter(c -> c.getStartTime() != null
                        && !c.getStartTime().isBefore(window24hStart)
                        && c.getStartTime().isBefore(window24hEnd))
                .toList();

        // Find UPCOMING contests starting in 1-2 hours from now (T-1h window)
        LocalDateTime window1hStart = now.plusHours(1);
        LocalDateTime window1hEnd = now.plusHours(2);
        List<Contest> contests1h = contestMapper.findByStatus(
                com.ulticode.modules.contest.entity.enums.ContestStatus.UPCOMING.name()).stream()
                .filter(c -> c.getStartTime() != null
                        && !c.getStartTime().isBefore(window1hStart)
                        && c.getStartTime().isBefore(window1hEnd))
                .toList();

        // Process T-24h reminders
        if (!contests24h.isEmpty()) {
            List<String> contestIds24h = contests24h.stream().map(Contest::getId).toList();
            List<ContestParticipant> participants24h = participantMapper.findByContestIds(contestIds24h);
            for (ContestParticipant p : participants24h) {
                sendContestReminder(p, "24h", contests24h);
            }
        }

        // Process T-1h reminders
        if (!contests1h.isEmpty()) {
            List<String> contestIds1h = contests1h.stream().map(Contest::getId).toList();
            List<ContestParticipant> participants1h = participantMapper.findByContestIds(contestIds1h);
            for (ContestParticipant p : participants1h) {
                sendContestReminder(p, "1h", contests1h);
            }
        }
    }

    private void sendContestReminder(ContestParticipant participant, String reminderType, List<Contest> matchingContests) {
        // Find the matching contest
        Contest contest = matchingContests.stream()
                .filter(c -> c.getId().equals(participant.getContestId()))
                .findFirst()
                .orElse(null);
        if (contest == null) return;

        // Build dedup key per D-10
        String dedupKey = participant.getUserId() + ":" + contest.getId() + ":" + reminderType;

        // Build metadata per D-08
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("contestId", contest.getId());
        metadata.put("contestTitle", contest.getTitle());
        metadata.put("startTime", contest.getStartTime() != null ? contest.getStartTime().toString() : "");
        metadata.put("dedupKey", dedupKey);

        // Title per D-04 and D-05
        String title;
        if ("24h".equals(reminderType)) {
            title = "Contest '" + contest.getTitle() + "' starts in 24 hours";
        } else {
            title = "Contest '" + contest.getTitle() + "' starts in 1 hour";
        }

        // Fire-and-notify per D-13.
        // Q20: respect SYSTEM category preference (contest reminders are
        // system-originated but not security-critical; users can opt out).
        // ADR-004 M4c: when the flag is on, dispatch the typed
        // ContestStartingIntent so the 24h and 1h reminders get separate
        // intent ids (the 24h and 1h path are deduped independently).
        try {
            if (featureFlags.isUseNotificationIntent()) {
                notificationDispatcher.dispatch(
                        com.ulticode.modules.notification.intent.ContestStartingIntent.of(
                                contest, participant, reminderType));
            } else {
                notificationDispatchService.dispatch(
                        participant.getUserId(),
                        "CONTEST_REMINDER",
                        "SYSTEM",
                        title,
                        "",  // body empty per D-06
                        "/contest/" + contest.getId(),  // link per D-07
                        metadata,
                        false);
            }
            log.debug("Sent {} reminder for contest {} to user {}", reminderType, contest.getId(), participant.getUserId());
        } catch (Exception e) {
            log.warn("Failed to send {} reminder for contest {} to user {}: {}",
                    reminderType, contest.getId(), participant.getUserId(), e.getMessage());
        }
    }

    private LocalDateTime computeEffectiveEndTime(Contest contest) {
        if (contest.getEndTime() != null) {
            return contest.getEndTime();
        }
        // Fallback: start_time + duration_minutes
        if (contest.getStartTime() != null && contest.getDurationMinutes() != null) {
            return contest.getStartTime().plusMinutes(contest.getDurationMinutes());
        }
        return null;
    }

    void transitionToRunning(Contest contest) {
        // Re-check: skip if already RUNNING (idempotent)
        if (com.ulticode.modules.contest.entity.enums.ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            return;
        }
        contest.setStatus(com.ulticode.modules.contest.entity.enums.ContestStatus.RUNNING.name());
        contest.setActualStartTime(LocalDateTime.now(clock));
        contestMapper.updateById(contest);

        // P0-2: batch-transition REGISTERED participants to STARTED so they can
        // submit. Runs after contest.status is committed; transitions are
        // idempotent (only REGISTERED rows are touched).
        try {
            int started = contestScoringService.batchStartParticipants(contest.getId());
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
                contest.getActualStartTime() != null ? contest.getActualStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant() : null,
                null,
                null
        );

        // Mark dirty so initial ranking appears on leaderboard
        contestRankingMarkDirtyPort.markDirty(contest.getId());

        log.info("Contest {} transitioned to RUNNING", contest.getId());
    }

    /**
     * The injected ContestScoringService. P0-2 wiring.
     */
    private final com.ulticode.modules.contest.service.ContestScoringService contestScoringService;

    void transitionToFinished(Contest contest) {
        // Re-check: skip if already FINISHED (idempotent)
        if (com.ulticode.modules.contest.entity.enums.ContestStatus.FINISHED.name().equals(contest.getStatus())) {
            return;
        }
        contest.setStatus(com.ulticode.modules.contest.entity.enums.ContestStatus.FINISHED.name());
        contest.setActualEndTime(LocalDateTime.now(clock));
        contestMapper.updateById(contest);

        // R3.1: close all real (is_virtual=0) participants so the rating
        // calculation below sees a stable, FINISHED set. Virtual participants
        // are managed by the per-user virtual session, not the contest clock.
        try {
            int finished = participantMapper.finishStartedRealParticipants(
                    contest.getId(), LocalDateTime.now(clock));
            if (finished > 0) {
                log.info("R3.1: closed {} real participants for contest {}",
                        finished, contest.getId());
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
                contest.getActualEndTime() != null ? contest.getActualEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant() : null,
                null
        );

        // Trigger rating calculation
        ratingService.calculateAndUpdate(contest.getId());

        log.info("Contest {} transitioned to FINISHED, ratings calculated", contest.getId());
    }
}
