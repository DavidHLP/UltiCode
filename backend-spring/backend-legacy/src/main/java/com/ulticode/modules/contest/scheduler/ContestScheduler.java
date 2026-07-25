package com.ulticode.modules.contest.scheduler;

import com.ulticode.modules.contest.service.ContestLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Thin trigger adapter over the {@link ContestLifecycleService} deep seam.
 *
 * <p>Owns nothing but the scheduling annotations and the wall-clock
 * {@code now}: the 10-second heartbeat, the per-minute reminder fan-out, and
 * the startup crash-recovery sweep each delegate every lifecycle policy
 * (due selection, idempotent transitions, participant closure, push/ranking,
 * rating handoff, reminder dispatch) to the lifecycle module. That is where
 * the invariants live and where the deterministic {@link Clock}-based tests
 * live — see {@code ContestLifecycleServiceImplTest}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestScheduler {

    private final ContestLifecycleService contestLifecycleService;
    private final Clock clock;

    /**
     * 10-second heartbeat: advance due UPCOMING→RUNNING and RUNNING→FINISHED
     * contests, then auto-finish expired virtual participants. All policy and
     * fault isolation live in {@link ContestLifecycleService#tick}.
     */
    @Scheduled(fixedRate = 10_000)
    public void run() {
        contestLifecycleService.tick(LocalDateTime.now(clock));
    }

    /**
     * R7.2 / F-31: sweep expired virtual sessions on app start so
     * crash-recovery leaves the participant table clean. Idempotent: the 10s
     * tick will keep this state fresh anyway, so a failure here never blocks
     * startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void sweepOnStartup() {
        try {
            int swept = contestLifecycleService.autoFinishVirtualParticipants();
            if (swept > 0) {
                log.info("R7.2 / F-31: startup sweep closed {} expired virtual sessions", swept);
            }
        } catch (Exception e) {
            // Never block startup; scheduler tick will retry.
            log.warn("R7.2 / F-31: startup sweep failed, will retry on next 10s tick: {}",
                    e.getMessage());
        }
    }

    /**
     * Per-minute T-24h / T-1h contest-start reminder fan-out. All window
     * selection, participant resolution, and typed-intent dispatch live in
     * {@link ContestLifecycleService#sendReminders}.
     */
    @Scheduled(fixedRate = 60_000)
    public void sendContestReminders() {
        contestLifecycleService.sendReminders(LocalDateTime.now(clock));
    }
}
