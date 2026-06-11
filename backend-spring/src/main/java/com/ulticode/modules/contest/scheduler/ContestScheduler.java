package com.ulticode.modules.contest.scheduler;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.RatingCalculationService;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus;
import com.ulticode.modules.websocket.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private final RealtimeService realtimeService;
    private final RatingCalculationService ratingService;
    private final NotificationService notificationService;
    private final NotificationDispatchService notificationDispatchService;
    private final ContestParticipantMapper participantMapper;

    @Scheduled(fixedRate = 10_000)
    public void run() {
        // Step 1: Find UPCOMING contests and transition those past start_time
        List<Contest> upcoming = contestMapper.findByStatus(
                com.ulticode.modules.contest.entity.enums.ContestStatus.UPCOMING.name());
        LocalDateTime now = LocalDateTime.now();
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
    }

    @Scheduled(fixedRate = 60_000)  // Check every minute
    public void sendContestReminders() {
        LocalDateTime now = LocalDateTime.now();

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
        try {
            notificationDispatchService.dispatch(
                    participant.getUserId(),
                    "CONTEST_REMINDER",
                    "SYSTEM",
                    title,
                    "",  // body empty per D-06
                    "/contest/" + contest.getId(),  // link per D-07
                    metadata,
                    false);
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
        contest.setActualStartTime(LocalDateTime.now());
        contestMapper.updateById(contest);

        // Emit WebSocket status
        realtimeService.emitContestStatus(
                contest.getId(),
                ContestStatus.RUNNING,
                contest.getActualStartTime() != null ? contest.getActualStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant() : null,
                null,
                null
        );

        // Mark dirty so initial ranking appears on leaderboard
        realtimeService.markDirty(contest.getId());

        log.info("Contest {} transitioned to RUNNING", contest.getId());
    }

    void transitionToFinished(Contest contest) {
        // Re-check: skip if already FINISHED (idempotent)
        if (com.ulticode.modules.contest.entity.enums.ContestStatus.FINISHED.name().equals(contest.getStatus())) {
            return;
        }
        contest.setStatus(com.ulticode.modules.contest.entity.enums.ContestStatus.FINISHED.name());
        contest.setActualEndTime(LocalDateTime.now());
        contestMapper.updateById(contest);

        // Emit WebSocket status
        realtimeService.emitContestStatus(
                contest.getId(),
                ContestStatus.ENDED,
                null,
                contest.getActualEndTime() != null ? contest.getActualEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant() : null,
                null
        );

        // Trigger rating calculation
        ratingService.calculateAndUpdate(contest.getId());

        log.info("Contest {} transitioned to FINISHED, ratings calculated", contest.getId());
    }
}
