package com.ulticode.modules.backup.service.impl;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.common.storage.StorageKeys;
import com.ulticode.modules.backup.entity.Backup;
import com.ulticode.modules.backup.entity.enums.BackupStatus;
import com.ulticode.modules.backup.mapper.BackupDeletionTombstoneMapper;
import com.ulticode.modules.backup.mapper.BackupMapper;
import com.ulticode.modules.backup.port.BackupProcessPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Owns the complete lifecycle of Admin backup objects: execution, deletion,
 * durable cleanup intent and bounded reconciliation.
 */
@Slf4j
@Component
public class BackupObjectLifecycle {

    private static final int SWEEP_LIMIT = 100;
    private static final int MAX_ERROR_LENGTH = 500;

    private final BackupMapper backupMapper;
    private final BackupDeletionTombstoneMapper backupDeletionTombstoneMapper;
    private final Clock clock;
    private final BackupProcessPort backupProcessPort;
    private final FileStoragePort fileStorage;
    private final Executor adminBackupExecutor;
    private final String backupTempDir;
    private final int settleSeconds;

    public BackupObjectLifecycle(
            BackupMapper backupMapper,
            BackupDeletionTombstoneMapper backupDeletionTombstoneMapper,
            Clock clock,
            BackupProcessPort backupProcessPort,
            FileStoragePort fileStorage,
            @Qualifier("adminBackupExecutor") Executor adminBackupExecutor,
            @Value("${backup.temp-dir:${java.io.tmpdir}/ulticode-backups}") String backupTempDir,
            @Value("${backup.object-cleanup.settle-seconds:300}") int settleSeconds) {
        this.backupMapper = backupMapper;
        this.backupDeletionTombstoneMapper = backupDeletionTombstoneMapper;
        this.clock = clock;
        this.backupProcessPort = backupProcessPort;
        this.fileStorage = fileStorage;
        this.adminBackupExecutor = adminBackupExecutor;
        this.backupTempDir = backupTempDir;
        this.settleSeconds = settleSeconds;
    }

    /** Submits one backup run to the dedicated Admin executor. */
    public void start(String backupId) {
        try {
            adminBackupExecutor.execute(() -> execute(backupId));
        } catch (RejectedExecutionException rejection) {
            recordImmediateRejection(backupId, rejection);
            throw rejection;
        }
    }

    /**
     * Deletes the row and records its object cleanup intent atomically. The
     * object itself is only submitted after the transaction commits.
     */
    @Transactional
    public void delete(String backupId) {
        Backup backup = backupMapper.selectById(backupId);
        if (backup == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Backup not found");
        }

        String objectKey = null;
        if (backup.getObjectKey() != null && !backup.getObjectKey().isBlank()) {
            if (backup.getStatus() == BackupStatus.PENDING
                    || backup.getStatus() == BackupStatus.IN_PROGRESS) {
                throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                        "Backup is still running; retry the deletion after it reaches a terminal state");
            }
            objectKey = requireBackupObjectKey(backup);
        }

        int deletedRows = backupMapper.deleteIfNotRunning(backupId);
        if (deletedRows != 1) {
            throw new BusinessException(BaseErrorCode.UNKNOWN_ERROR,
                    "Failed to delete backup record; retry the operation");
        }
        backupDeletionTombstoneMapper.insert(backupId, objectKey);

        if (objectKey != null && backup.getStatus() != BackupStatus.FAILED) {
            registerAfterCommitCleanup(objectKey);
        }
        log.info("Deleted backup: {}", backupId);
    }

    /** Retries at most 100 settled tombstones and returns the number observed. */
    @Scheduled(scheduler = "adminBackupScheduler",
            fixedDelayString = "${backup.object-cleanup.interval-ms:300000}",
            initialDelayString = "30000")
    public int sweep() {
        List<String> pending;
        try {
            pending = backupDeletionTombstoneMapper.selectPendingObjectKeys(SWEEP_LIMIT, settleSeconds);
        } catch (RuntimeException databaseFailure) {
            log.warn("Backup object cleanup sweep could not read pending tombstones", databaseFailure);
            return 0;
        }
        for (String objectKey : pending) {
            deletePending(objectKey);
        }
        return pending.size();
    }

    private void execute(String backupId) {
        Backup backup;
        try {
            backup = backupMapper.selectById(backupId);
        } catch (RuntimeException databaseFailure) {
            log.error("Could not read backup before execution: {}", backupId, databaseFailure);
            return;
        }
        if (backup == null) {
            log.error("Backup not found: {}", backupId);
            return;
        }

        Path tempFile = null;
        String objectKey = null;
        try {
            backup.setStatus(BackupStatus.IN_PROGRESS);
            int inProgressRows = backupMapper.updateById(backup);
            if (inProgressRows != 1) {
                throw new IllegalStateException(
                        "Failed to persist IN_PROGRESS backup state; affected rows: " + inProgressRows);
            }

            tempFile = createSecureTempFile("dump-", ".sql");
            if (!backupProcessPort.dump(tempFile)
                    || !Files.isRegularFile(tempFile)
                    || Files.size(tempFile) == 0) {
                throw new IllegalStateException("mysqldump failed — see server logs");
            }

            long size = Files.size(tempFile);
            String checksum = sha256(tempFile);
            LocalDate createdDate = backup.getCreatedAt() == null
                    ? LocalDateTime.now(clock).toLocalDate()
                    : backup.getCreatedAt().toLocalDate();
            objectKey = StorageKeys.backupKey(backupId, createdDate);
            backup.setObjectKey(objectKey);
            int plannedRows = backupMapper.updateById(backup);
            if (plannedRows != 1) {
                throw new IllegalStateException(
                        "Failed to persist the planned backup object key; affected rows: " + plannedRows);
            }
            fileStorage.putFile(objectKey, tempFile, "application/sql");

            backup.setSize(size);
            backup.setChecksum(checksum);
            backup.setStatus(BackupStatus.COMPLETED);
            backup.setCompletedAt(LocalDateTime.now(clock));
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("databaseName", "see-port-adapter");
            metadata.put("backupType", backup.getType().name());
            backup.setMetadata(metadata);
            int completedRows = backupMapper.updateById(backup);
            if (completedRows != 1) {
                throw new IllegalStateException(
                        "Failed to persist COMPLETED backup state; affected rows: " + completedRows);
            }
            log.info("Backup completed successfully: {}, size: {} bytes", backupId, size);
        } catch (Exception failure) {
            handleFailure(backup, objectKey, failure);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private void handleFailure(Backup backup, String objectKey, Exception failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        CompletionOutcome outcome = objectKey == null
                ? CompletionOutcome.DEFINITE_FAILURE
                : classifyCompletionOutcome(backup.getId(), objectKey);
        switch (outcome) {
            case PERSISTED -> log.info("Backup {} completion update raced a database error but the "
                    + "COMPLETED state persisted; keeping the uploaded object {}",
                    backup.getId(), objectKey);
            case UNKNOWN -> {
                log.error("Backup {} completion outcome is unknown after a database failure; "
                        + "preserving uploaded object {} instead of deleting it", backup.getId(), objectKey,
                        failure);
                persistFailure(backup,
                        "Backup completion outcome unknown after a database failure; "
                                + "uploaded object preserved for reconciliation: " + objectKey);
            }
            case DEFINITE_FAILURE -> {
                int failedRows = persistFailure(backup, failure.getMessage());
                if (objectKey != null) {
                    if (failedRows == 1) {
                        deferUploadedObjectCleanup(backup.getId(), objectKey);
                    } else {
                        log.warn("Backup {} kept a durable COMPLETED row or has no guarded FAILED state; "
                                        + "preserving uploaded object {}", backup.getId(), objectKey);
                    }
                }
                log.error("Backup execution failed for: {}", backup.getId(), failure);
            }
        }
    }

    private enum CompletionOutcome {
        PERSISTED,
        UNKNOWN,
        DEFINITE_FAILURE
    }

    private CompletionOutcome classifyCompletionOutcome(String backupId, String objectKey) {
        Backup persisted;
        try {
            persisted = backupMapper.selectById(backupId);
        } catch (RuntimeException readFailure) {
            return CompletionOutcome.UNKNOWN;
        }
        if (persisted == null) {
            return CompletionOutcome.DEFINITE_FAILURE;
        }
        boolean completed = persisted.getStatus() == BackupStatus.COMPLETED
                && objectKey.equals(persisted.getObjectKey());
        return completed ? CompletionOutcome.PERSISTED : CompletionOutcome.DEFINITE_FAILURE;
    }

    private int persistFailure(Backup backup, String error) {
        backup.setStatus(BackupStatus.FAILED);
        backup.setCompletedAt(LocalDateTime.now(clock));
        backup.setError(error == null || error.isBlank() ? "Backup execution failed" : error);
        try {
            int failedRows = backupMapper.failUnlessCompleted(
                    backup.getId(), backup.getCompletedAt(), backup.getError(), backup.getObjectKey());
            if (failedRows == 0) {
                log.warn("Backup {} already holds a durable COMPLETED row; FAILED transition skipped",
                        backup.getId());
            } else if (failedRows != 1) {
                log.error("Failed to persist FAILED backup state: {}, affected rows: {}",
                        backup.getId(), failedRows);
            }
            return failedRows;
        } catch (RuntimeException stateFailure) {
            log.error("Failed to persist FAILED backup state: {}", backup.getId(), stateFailure);
            return -1;
        }
    }

    private void recordImmediateRejection(String backupId, RejectedExecutionException rejection) {
        String objectKey = null;
        try {
            Backup backup = backupMapper.selectById(backupId);
            if (backup == null) {
                log.error("Backup not found while recording executor rejection: {}", backupId);
                return;
            }
            objectKey = backup.getObjectKey();
            String message = rejection.getMessage();
            String error = message == null || message.isBlank()
                    ? "Backup execution rejected: executor unavailable"
                    : "Backup execution rejected: " + message;
            int failedRows = backupMapper.failUnlessCompleted(
                    backupId, LocalDateTime.now(clock), error, objectKey);
            if (failedRows != 1) {
                log.error("Failed to persist rejected backup state: {}, affected rows: {}",
                        backupId, failedRows);
            }
        } catch (RuntimeException stateFailure) {
            log.error("Failed to persist rejected backup state: {}", backupId, stateFailure);
        }
    }

    private void deferUploadedObjectCleanup(String backupId, String objectKey) {
        try {
            backupDeletionTombstoneMapper.insert(backupId, objectKey);
        } catch (RuntimeException tombstoneFailure) {
            log.error("Could not record the pending deletion of backup object {}; delete backup {} manually",
                    objectKey, backupId, tombstoneFailure);
        }
    }

    private void registerAfterCommitCleanup(String objectKey) {
        Runnable submitCleanup = () -> {
            try {
                adminBackupExecutor.execute(() -> deletePending(objectKey));
            } catch (RuntimeException rejection) {
                log.warn("Failed to schedule backup object cleanup: {}", objectKey, rejection);
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

    private void deletePending(String objectKey) {
        try {
            fileStorage.delete(objectKey);
        } catch (RuntimeException storageFailure) {
            recordFailure(objectKey, storageFailure);
            log.warn("Backup object {} was not deleted; the deletion tombstone retries it", objectKey,
                    storageFailure);
            return;
        }
        try {
            if (backupDeletionTombstoneMapper.markObjectDeleted(objectKey) != 1) {
                log.warn("Deleted backup object {} but found no pending tombstone to clear for it", objectKey);
            }
        } catch (RuntimeException databaseFailure) {
            log.warn("Deleted backup object {} but could not clear its tombstone", objectKey,
                    databaseFailure);
        }
    }

    private void recordFailure(String objectKey, RuntimeException failure) {
        try {
            backupDeletionTombstoneMapper.recordObjectDeleteFailure(objectKey, describe(failure));
        } catch (RuntimeException databaseFailure) {
            log.warn("Could not record the pending deletion of backup object {}", objectKey,
                    databaseFailure);
        }
    }

    private String requireBackupObjectKey(Backup backup) {
        String objectKey = backup.getObjectKey();
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

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
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

    private static String describe(RuntimeException failure) {
        String message = failure.getMessage();
        String description = message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
        return description.length() > MAX_ERROR_LENGTH
                ? description.substring(0, MAX_ERROR_LENGTH)
                : description;
    }
}
