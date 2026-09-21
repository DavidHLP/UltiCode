package com.ulticode.modules.backup.service.impl;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.common.storage.StorageKeys;
import com.ulticode.modules.backup.dto.BackupVO;
import com.ulticode.modules.backup.dto.CreateBackupDTO;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.mapper.BackupDeletionTombstoneMapper;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.port.BackupProcessPort;
import com.ulticode.modules.backup.projection.BackupReadProjection;
import com.ulticode.modules.backup.service.BackupExecutionService;
import com.ulticode.modules.backup.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

/**
 * Write-side orchestration for backups. Durable dump bytes never live on a
 * permanent local filesystem path; restore is the only operation that creates
 * a short-lived local file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements BackupService {

    private final BackupMapper backupMapper;
    private final BackupDeletionTombstoneMapper backupDeletionTombstoneMapper;
    private final Clock clock;
    private final BackupProcessPort backupProcessPort;
    private final BackupReadProjection backupReadProjection;
    private final BackupExecutionService backupExecutionService;
    private final FileStoragePort fileStorage;

    @Value("${backup.temp-dir:${java.io.tmpdir}/ulticode-backups}")
    private String backupTempDir;

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public BackupVO createBackup(String userId, CreateBackupDTO dto) {
        String timestamp = LocalDateTime.now(clock).format(FILE_DATE_FORMAT);
        String filename = String.format("backup_%s_%s.sql", dto.getType().name().toLowerCase(), timestamp);

        Backup backup = new Backup();
        backup.setFilename(filename);
        backup.setSize(0L);
        backup.setType(dto.getType());
        backup.setStatus(BackupStatus.PENDING);
        backup.setCreatedBy(userId);

        backupMapper.insert(backup);
        try {
            CompletableFuture<Void> execution = backupExecutionService.executeBackup(backup.getId());
            if (execution != null) {
                execution.whenComplete((ignored, failure) -> {
                    if (isRejected(failure)) {
                        markExecutionRejected(backup, failure);
                    }
                });
            }
        } catch (RejectedExecutionException exception) {
            markExecutionRejected(backup, exception);
            throw exception;
        }
        return backupReadProjection.toVO(backup);
    }

    private boolean isRejected(Throwable failure) {
        while (failure != null) {
            if (failure instanceof RejectedExecutionException) {
                return true;
            }
            failure = failure.getCause();
        }
        return false;
    }

    private void markExecutionRejected(Backup backup, Throwable failure) {
        backup.setStatus(BackupStatus.FAILED);
        backup.setCompletedAt(LocalDateTime.now(clock));
        String message = failure.getMessage();
        backup.setError(message == null || message.isBlank()
                ? "Backup execution rejected: executor unavailable"
                : "Backup execution rejected: " + message);
        int failedRows = backupMapper.updateById(backup);
        if (failedRows != 1) {
            log.error("Failed to persist rejected backup state: {}, affected rows: {}",
                    backup.getId(), failedRows);
        }
    }

    @Override
    public BackupDownload getBackupFile(String id) {
        Backup backup = requireBackup(id);
        if (backup.getStatus() != BackupStatus.COMPLETED) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Backup is not completed yet");
        }
        validateBackupFilePath(backup.getFilename());
        String objectKey = requireBackupObjectKey(backup);
        FileStoragePort.StorageStream stream = fileStorage.openStream(objectKey)
                .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Backup object not found"));
        return new BackupDownload(
                backup.getFilename(),
                stream.content(),
                stream.contentLength(),
                stream.contentType());
    }

    @Override
    public BackupVO restoreBackup(String id, String userId) {
        Backup backup = requireBackup(id);
        if (backup.getStatus() != BackupStatus.COMPLETED) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Cannot restore from a non-completed backup");
        }
        validateBackupFilePath(backup.getFilename());
        String objectKey = requireBackupObjectKey(backup);
        Path tempFile = null;
        try {
            tempFile = createSecureTempFile("restore-", ".sql");
            FileStoragePort.StorageStream stored = fileStorage.openStream(objectKey)
                    .orElseThrow(() -> new BusinessException(BaseErrorCode.NOT_FOUND, "Backup object not found"));
            String checksum = copyAndChecksum(stored.content(), tempFile);
            if (backup.getChecksum() != null && !backup.getChecksum().isBlank()
                    && !checksum.equalsIgnoreCase(backup.getChecksum())) {
                throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR,
                        "Backup checksum mismatch; refusing to restore");
            }
            if (!backupProcessPort.restore(tempFile)) {
                throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR,
                        "Database restore failed. Check server logs for details.");
            }
            Map<String, Object> metadata = backup.getMetadata() == null
                    ? new HashMap<>()
                    : new HashMap<>(backup.getMetadata());
            metadata.put("lastRestoredAt", LocalDateTime.now(clock).toString());
            metadata.put("lastRestoredBy", userId);
            backup.setMetadata(metadata);
            int updatedRows;
            try {
                updatedRows = backupMapper.updateById(backup);
            } catch (Exception exception) {
                log.error(
                        "Partial restore success for backup {}: database restore completed, "
                                + "but metadata write-back failed; do not retry database restore",
                        id, exception);
                throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR,
                        "Database restore completed successfully, but metadata write-back failed. "
                                + "Do not retry the database restore automatically.");
            }
            if (updatedRows != 1) {
                log.error(
                        "Partial restore success for backup {}: database restore completed, "
                                + "but metadata write-back affected {} rows; do not retry database restore",
                        id, updatedRows);
                throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR,
                        "Database restore completed successfully, but metadata write-back failed. "
                                + "Do not retry the database restore automatically.");
            }
            log.info("Database restore completed successfully from backup: {}", id);
            return backupReadProjection.toVO(backup);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Database restore failed for backup: {}", id, exception);
            throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR,
                    "Database restore failed. Check server logs for details.");
        } finally {
            deleteTempFile(tempFile);
        }
    }
    @Override
    @Transactional
    public void deleteBackup(String id) {
        Backup backup = requireBackup(id);
        validateBackupFilePath(backup.getFilename());
        String objectKey = null;
        if (backup.getObjectKey() != null && !backup.getObjectKey().isBlank()) {
            objectKey = requireBackupObjectKey(backup);
        }
        int deletedRows = backupMapper.deleteById(id);
        if (deletedRows != 1) {
            throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR,
                    "Failed to delete backup record; retry the operation");
        }
        backupDeletionTombstoneMapper.insert(id);
        if (objectKey != null) {
            deleteObjectAfterCommit(objectKey);
        }
        log.info("Deleted backup: {}", id);
    }

    /** Delete object bytes only after the database row has committed. */
    private void deleteObjectAfterCommit(String objectKey) {
        Runnable cleanup = () -> {
            try {
                fileStorage.delete(objectKey);
            } catch (RuntimeException exception) {
                log.warn("Failed to delete backup object after row deletion: {}", objectKey, exception);
            }
        };
        Runnable submitCleanup = () -> {
            try {
                CompletableFuture.runAsync(cleanup).exceptionally(exception -> {
                    log.warn("Async backup object cleanup failed: {}", objectKey, exception);
                    return null;
                });
            } catch (RuntimeException exception) {
                log.warn("Failed to schedule backup object cleanup: {}", objectKey, exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitCleanup.run();
                }
            });
        } else {
            submitCleanup.run();
        }
    }

    @Override
    public BackupVO toVO(Backup backup) {
        return backupReadProjection.toVO(backup);
    }

    private Backup requireBackup(String id) {
        Backup backup = backupMapper.selectById(id);
        if (backup == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Backup not found");
        }
        return backup;
    }

    /**
     * Keep the existing display-name validation even though the bytes now come
     * from object storage. A filename is never treated as an object key.
     */
    private Path validateBackupFilePath(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Invalid backup filename");
        }
        Path root = Paths.get(backupTempDir).toAbsolutePath().normalize();
        Path displayPath = root.resolve(filename).normalize();
        if (!displayPath.startsWith(root)) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Backup path traversal detected");
        }
        return displayPath;
    }

    private String requireBackupObjectKey(Backup backup) {
        String objectKey = backup.getObjectKey();
        // Legacy metadata rows stay COMPLETED with a null key until their dump is
        // migrated; their bytes are unavailable, which the download/restore
        // contract documents as NOT_FOUND, not a malformed-key BAD_REQUEST.
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Backup object not found");
        }
        try {
            StorageKeys.validate(objectKey);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Invalid backup object key");
        }
        if (!objectKey.startsWith(StorageKeys.BACKUP_PREFIX)) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Invalid backup object key");
        }
        return objectKey;
    }

    private Path createSecureTempFile(String prefix, String suffix) throws IOException {
        Path directory = Paths.get(backupTempDir).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        restrictPermissions(directory, "rwx------");
        Path file = Files.createTempFile(directory, prefix, suffix);
        restrictPermissions(file, "rw-------");
        return file;
    }

    private static void restrictPermissions(Path path, String permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (UnsupportedOperationException ignored) {
            // POSIX permissions are unavailable on some local development hosts.
        }
    }

    private static void deleteTempFile(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Failed to clean up backup temp file: {}", file, exception);
        }
    }

    private static String copyAndChecksum(InputStream content, Path target) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (DigestInputStream digestInput = new DigestInputStream(content, digest);
             OutputStream output = Files.newOutputStream(target)) {
            digestInput.transferTo(output);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
