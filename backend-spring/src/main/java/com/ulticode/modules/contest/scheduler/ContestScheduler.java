package com.ulticode.modules.contest.scheduler;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.service.RatingCalculationService;
import com.ulticode.modules.websocket.event.ContestStatusEvent.ContestStatus;
import com.ulticode.modules.websocket.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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
