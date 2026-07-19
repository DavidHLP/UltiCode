package com.ulticode.modules.backup.service.impl;

import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.port.BackupProcessPort;
import com.ulticode.modules.backup.service.BackupExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Async execution lifecycle for a single backup run.
 *
 * <p>This bean is the {@code @Async} entrypoint that {@link BackupServiceImpl#createBackup}
 * dispatches to. Because it is a separate Spring bean, the call crosses the
 * AOP proxy and the {@code @Async} annotation actually takes effect &mdash;
 * the previous in-class self-invocation silently bypassed the proxy and
 * blocked the HTTP request thread until {@code mysqldump} returned. See
 * {@link BackupExecutionService} for the seam rationale.
 *
 * <p>Owns every lifecycle transition for the run:
 * <ul>
 *   <li>{@code PENDING &rarr; IN_PROGRESS} on entry,</li>
 *   <li>{@code IN_PROGRESS &rarr; COMPLETED} when {@link BackupProcessPort#dump}
 *       reports success and the file exists (records size + metadata),</li>
 *   <li>{@code IN_PROGRESS &rarr; FAILED} when dump reports failure, the file
 *       is missing, or any exception escapes (records the error message).</li>
 * </ul>
 * Process I/O itself stays behind {@link BackupProcessPort}; this class is
 * the lifecycle owner, not the subprocess spawner.
 *
 * @author ulticode
 */
@Slf4j
@Service
@Async
@RequiredArgsConstructor
public class BackupExecutionServiceImpl implements BackupExecutionService {

    private final BackupMapper backupMapper;
    private final Clock clock;
    private final BackupProcessPort backupProcessPort;

    @Value("${backup.dir:${BACKUP_DIR:/tmp/backups}}")
    private String backupDir;

    @Override
    public void executeBackup(String backupId) {
        Backup backup = backupMapper.selectById(backupId);
        if (backup == null) {
            log.error("Backup not found: {}", backupId);
            return;
        }

        try {
            // PENDING -> IN_PROGRESS
            backup.setStatus(BackupStatus.IN_PROGRESS);
            backupMapper.updateById(backup);

            // Ensure backup directory exists
            ensureBackupDirectoryExists();

            Path filePath = Paths.get(backupDir, backup.getFilename());

            // Delegate the mysqldump process I/O to the port — the
            // execution lifecycle no longer spawns the subprocess directly.
            boolean success = backupProcessPort.dump(filePath);

            if (success && Files.exists(filePath)) {
                long size = Files.size(filePath);
                backup.setSize(size);
                backup.setStatus(BackupStatus.COMPLETED);
                backup.setCompletedAt(LocalDateTime.now(clock));

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("databaseName", "see-port-adapter");
                metadata.put("backupType", backup.getType().name());
                backup.setMetadata(metadata);

                backupMapper.updateById(backup);
                log.info("Backup completed successfully: {}, size: {} bytes", backupId, size);
            } else {
                fail(backup, backupId, "mysqldump failed — see server logs");
                log.error("Backup failed: {}", backupId);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Backup execution failed for: {}", backupId, e);
            fail(backup, backupId, e.getMessage());
        }
    }

    /**
     * Transition a backup to FAILED and record the error message. Centralised
     * so every failure path (process failure, missing file, exception)
     * captures both the terminal status and the error string with the same
     * invariants.
     */
    private void fail(Backup backup, String backupId, String error) {
        backup.setStatus(BackupStatus.FAILED);
        backup.setCompletedAt(LocalDateTime.now(clock));
        backup.setError(error);
        backupMapper.updateById(backup);
    }

    private void ensureBackupDirectoryExists() {
        Path path = Paths.get(backupDir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (Exception e) {
                log.warn("Failed to create backup directory: {}", path, e);
            }
        }
    }
}
