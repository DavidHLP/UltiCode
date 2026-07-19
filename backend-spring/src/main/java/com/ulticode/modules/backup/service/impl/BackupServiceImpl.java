package com.ulticode.modules.backup.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.port.BackupProcessPort;
import com.ulticode.modules.backup.projection.BackupReadProjection;
import com.ulticode.modules.backup.service.BackupExecutionService;
import com.ulticode.modules.backup.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Write-side service for the backup module: create, restore (delegates dump
 * / restore to {@link BackupProcessPort}), delete, file download, and the
 * view-shape delegate. This is HTTP / scheduler orchestration only &mdash;
 * the async execution lifecycle (status transitions, file-size recording,
 * failure capture) lives behind {@link BackupExecutionService}.
 *
 * <p>Read paths (paginated list, detail by id) and the entity-to-VO shaping
 * live behind {@link BackupReadProjection}. The public {@link #toVO(Backup)}
 * stays for backwards compatibility and now delegates to the projection so
 * write paths return the same view shape the controller's read path serves.
 *
 * <p><strong>Async dispatch.</strong> {@link #createBackup} dispatches the
 * run by calling {@link BackupExecutionService#executeBackup} on the injected
 * bean, not by self-invoking a {@code @Async} method. The previous shape
 * ({@code this.executeBackup(id)} in-class) silently bypassed the Spring AOP
 * proxy and ran the dump on the request thread &mdash; see
 * {@link BackupExecutionService} for the deep-module rationale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements BackupService {

    private final BackupMapper backupMapper;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;
    private final BackupProcessPort backupProcessPort;
    private final BackupReadProjection backupReadProjection;
    private final BackupExecutionService backupExecutionService;

    @Value("${backup.dir:${BACKUP_DIR:/tmp/backups}}")
    private String backupDir;
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public BackupVO createBackup(String userId, CreateBackupDTO dto) {
        // Pre-create the backup directory so a misconfigured path fails fast
        // at the request boundary instead of degrading to PENDING -> FAILED.
        // The execution service also tolerates a missing directory, but this
        // earlier check gives the operator an immediate actionable error.
        ensureBackupDirectoryExists();

        // Generate filename
        String timestamp = LocalDateTime.now(clock).format(FILE_DATE_FORMAT);
        String filename = String.format("backup_%s_%s.sql", dto.getType().name().toLowerCase(), timestamp);

        // Create backup record with PENDING status
        Backup backup = new Backup();
        backup.setFilename(filename);
        backup.setSize(0L);
        backup.setType(dto.getType());
        backup.setStatus(BackupStatus.PENDING);
        backup.setCreatedBy(userId);

        backupMapper.insert(backup);
        log.info("Created backup record: {} by user: {}", backup.getId(), userId);

        // Dispatch the async lifecycle via the injected bean so the call
        // crosses the Spring AOP proxy. The previous self-invocation
        // (this.executeBackup(id)) bypassed the proxy and ran synchronously
        // on this thread — see BackupExecutionService.
        backupExecutionService.executeBackup(backup.getId());

        return backupReadProjection.toVO(backup);
    }

    @Override
    public File getBackupFile(String id) {
        Backup backup = backupMapper.selectById(id);
        if (backup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup not found");
        }
        if (backup.getStatus() != BackupStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Backup is not completed yet");
        }

        Path filePath = validateBackupFilePath(backup.getFilename());
        File file = filePath.toFile();

        if (!file.exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup file not found");
        }

        return file;
    }

    @Override
    public BackupVO restoreBackup(String id, String userId) {
        Backup backup = backupMapper.selectById(id);
        if (backup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup not found");
        }
        if (backup.getStatus() != BackupStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot restore from a non-completed backup");
        }

        Path filePath = validateBackupFilePath(backup.getFilename());
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup file not found");
        }

        log.warn("Starting database restore from backup: {} by user: {}", id, userId);

        boolean success = backupProcessPort.restore(filePath);
        if (success) {
            log.info("Database restore completed successfully from backup: {}", id);
            Map<String, Object> metadata = backup.getMetadata();
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            metadata.put("lastRestoredAt", LocalDateTime.now(clock).toString());
            metadata.put("lastRestoredBy", userId);
            backup.setMetadata(metadata);
            backupMapper.updateById(backup);
            return backupReadProjection.toVO(backup);
        }
        throw new BusinessException(ErrorCode.UNKNOWN_ERROR,
                "Database restore failed. Check server logs for details.");
    }

    @Override
    public void deleteBackup(String id) {
        Backup backup = backupMapper.selectById(id);
        if (backup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Backup not found");
        }

        // Delete the file from disk
        Path filePath = validateBackupFilePath(backup.getFilename());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete backup file: {}", filePath, e);
        }

        // Delete the record from database
        backupMapper.deleteById(id);
        log.info("Deleted backup: {}", id);
    }

    @Override
    public BackupVO toVO(Backup backup) {
        return backupReadProjection.toVO(backup);
    }

    /**
     * Pre-create the backup directory at the request boundary so a
     * misconfigured path fails fast with a 4xx instead of degrading the
     * async run to PENDING &rarr; FAILED. The execution service also
     * tolerates a missing directory; this is the fast-fail for operators.
     */
    private void ensureBackupDirectoryExists() {
        Path path = Paths.get(backupDir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                log.info("Created backup directory: {}", backupDir);
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.UNKNOWN_ERROR, "Failed to create backup directory: " + e.getMessage());
            }
        }
    }

    /**
     * Validate backup filename and ensure the resolved path stays within the backup directory.
     */
    private Path validateBackupFilePath(String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid backup filename");
        }
        Path backupRoot = Paths.get(backupDir).normalize();
        Path filePath = Paths.get(backupDir, filename).normalize();
        if (!filePath.startsWith(backupRoot)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Backup path traversal detected");
        }
        return filePath;
    }
}
