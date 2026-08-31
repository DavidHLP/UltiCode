package com.ulticode.modules.backup.scheduler;

import com.ulticode.common.lease.FencedLease;
import com.ulticode.modules.backup.service.BackupService;
import com.ulticode.modules.lease.FencedJobLeaseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BackupScheduler fenced singleton")
class BackupSchedulerTest {

    private static final FencedLease LEASE = new FencedLease(
            "admin:scheduled-backup", 1, "runner-a",
            Instant.parse("2026-08-31T00:00:00Z"),
            Instant.parse("2026-08-31T02:00:00Z"));

    @Mock
    private BackupService backupService;

    @Mock
    private FencedJobLeaseService leaseService;

    @Test
    @DisplayName("busy replica does not enqueue a backup")
    void busyReplicaSkips() {
        when(leaseService.tryAcquire(anyString(), any(Duration.class))).thenReturn(null);

        new BackupScheduler(backupService, leaseService).scheduledBackup();

        verifyNoInteractions(backupService);
        verify(leaseService, never()).release(any(FencedLease.class));
    }

    @Test
    @DisplayName("winner enqueues once and releases its own fence")
    void winnerEnqueuesAndReleases() {
        when(leaseService.tryAcquire(anyString(), any(Duration.class))).thenReturn(LEASE);

        new BackupScheduler(backupService, leaseService).scheduledBackup();

        verify(backupService).createBackup(anyString(), any());
        verify(leaseService).release(LEASE);
    }
}
