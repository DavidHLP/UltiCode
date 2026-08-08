package com.ulticode.modules.backup.scheduler;

import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.enums.BackupType;
import com.ulticode.modules.backup.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for automated backup tasks
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService backupService;

    /**
     * Scheduled backup task that runs at 2 AM daily
     * Creates a full backup of the database
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledBackup() {
        log.info("Starting scheduled backup...");
        try {
            CreateBackupDTO dto = new CreateBackupDTO();
            dto.setType(BackupType.FULL);
            backupService.createBackup("system", dto);
            log.info("Scheduled backup completed successfully");
        // broad catch: scheduler resilience -- log and continue
        } catch (Exception e) {
            log.error("Scheduled backup failed: {}", e.getMessage(), e);
        }
    }
}
