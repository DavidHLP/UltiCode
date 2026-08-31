package com.ulticode.modules.backup.scheduler;

import com.ulticode.common.lease.FencedLease;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.enums.BackupType;
import com.ulticode.modules.backup.service.BackupService;
import com.ulticode.modules.lease.FencedJobLeaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import com.ulticode.common.lifecycle.DrainGate;

/**
 * Scheduler for automated backup tasks
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private static final String BACKUP_LEASE = "admin:scheduled-backup";
    private static final Duration BACKUP_LEASE_TTL = Duration.ofHours(2);

    private final BackupService backupService;
    private final FencedJobLeaseService fencedJobLeaseService;
    private final DrainGate drainGate = new DrainGate();

    /**
     * Scheduled backup task that runs at 2 AM daily
     * Creates a full backup of the database
     */
    @Scheduled(scheduler = "adminBackupScheduler", cron = "0 0 2 * * ?")
    public void scheduledBackup() {
        if (!drainGate.tryEnter()) {
            return;
        }
        try {
            runScheduledBackup();
        } finally {
            drainGate.leave();
        }
    }

    private void runScheduledBackup() {
        FencedLease lease;
        try {
            lease = fencedJobLeaseService.tryAcquire(BACKUP_LEASE, BACKUP_LEASE_TTL);
        } catch (RuntimeException e) {
            log.error("Scheduled backup lease acquisition failed", e);
            return;
        }
        if (lease == null) {
            log.info("Scheduled backup skipped: another replica owns {}", BACKUP_LEASE);
            return;
        }
        log.info("Starting scheduled backup...");
        try {
            CreateBackupDTO dto = new CreateBackupDTO();
            dto.setType(BackupType.FULL);
            backupService.createBackup("system", dto);
            log.info("Scheduled backup enqueued successfully");
        // broad catch: scheduler resilience -- log and continue
        } catch (Exception e) {
            log.error("Scheduled backup failed: {}", e.getMessage(), e);
        } finally {
            try {
                fencedJobLeaseService.release(lease);
            } catch (RuntimeException e) {
                log.error("Scheduled backup lease release failed", e);
            }
        }
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        drainGate.beginDrain();
    }
}
