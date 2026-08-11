package com.ulticode.modules.contest.scheduler;

import com.ulticode.modules.contest.service.ContestLifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContestScheduler")
class ContestSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

    @Mock
    private ContestLifecycleService lifecycleService;

    @Test
    @DisplayName("startup sweep retries FINISHING contests after a process restart")
    void sweepOnStartup_delegatesRecoveryTick() {
        ContestScheduler scheduler = new ContestScheduler(
                lifecycleService, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.sweepOnStartup();

        verify(lifecycleService).tick(LocalDateTime.of(2026, 3, 1, 12, 0));
    }

    @Test
    @DisplayName("scheduled run delegates the current clock instant")
    void run_delegatesHeartbeatTick() {
        ContestScheduler scheduler = new ContestScheduler(
                lifecycleService, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.run();

        verify(lifecycleService).tick(LocalDateTime.of(2026, 3, 1, 12, 0));
    }
}
