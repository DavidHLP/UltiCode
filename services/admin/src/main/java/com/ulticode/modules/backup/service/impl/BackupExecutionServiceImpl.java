package com.ulticode.modules.backup.service.impl;

import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.common.storage.StorageKeys;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async backup lifecycle. Dump bytes exist only in a secure container-local
 * temp file until they are streamed to object storage.
 */
@Slf4j
@Service
@Async("adminBackupExecutor")
@RequiredArgsConstructor
public class BackupExecutionServiceImpl implements BackupExecutionService {

    private final BackupMapper backupMapper;
    private final Clock clock;
    private final BackupProcessPort backupProcessPort;
    private final FileStoragePort fileStorage;

    @Value("${backup.temp-dir:${java.io.tmpdir}/ulticode-backups}")
    private String backupTempDir;
    @Override
    public CompletableFuture<Void> executeBackup(String backupId) {
        Backup backup = backupMapper.selectById(backupId);
        if (backup == null) {
            log.error("Backup not found: {}", backupId);
            return CompletableFuture.completedFuture(null);
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
            if (!backupProcessPort.dump(tempFile) || !Files.isRegularFile(tempFile) || Files.size(tempFile) == 0) {
                throw new IllegalStateException("mysqldump failed — see server logs");
            }

            long size = Files.size(tempFile);
            String checksum = sha256(tempFile);
            LocalDate createdDate = backup.getCreatedAt() == null
                    ? LocalDateTime.now(clock).toLocalDate()
                    : backup.getCreatedAt().toLocalDate();
            objectKey = StorageKeys.backupKey(backupId, createdDate);
            fileStorage.putFile(objectKey, tempFile, "application/sql");

            backup.setObjectKey(objectKey);
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
        } catch (Exception exception) {
            CompletionOutcome outcome = objectKey == null
                    ? CompletionOutcome.DEFINITE_FAILURE
                    : classifyCompletionOutcome(backupId, objectKey);
            switch (outcome) {
                case PERSISTED -> log.info("Backup {} completion update raced a database error but the "
                        + "COMPLETED state persisted; keeping the uploaded object {}", backupId, objectKey);
                case UNKNOWN -> {
                    log.error("Backup {} completion outcome is unknown after a database failure; "
                            + "preserving uploaded object {} instead of deleting it", backupId, objectKey,
                            exception);
                    // The drain gate only tolerates terminal rows. Best-effort
                    // FAILED write naming the preserved key; if the database is
                    // still unreachable the row stays IN_PROGRESS and the gate
                    // fails closed, which is the designed outcome.
                    try {
                        fail(backup, "Backup completion outcome unknown after a database failure; "
                                + "uploaded object preserved for reconciliation: " + objectKey);
                    } catch (RuntimeException stateFailure) {
                        log.error("Backup {} terminal FAILED state could not be persisted", backupId,
                                stateFailure);
                    }
                }
                case DEFINITE_FAILURE -> {
                    if (objectKey != null) {
                        deleteUploadedObject(objectKey);
                    }
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    log.error("Backup execution failed for: {}", backupId, exception);
                    fail(backup, exception.getMessage());
                }
            }
        } finally {
            deleteTempFile(tempFile);
        }
        return CompletableFuture.completedFuture(null);
    }

    private enum CompletionOutcome {
        PERSISTED,
        UNKNOWN,
        DEFINITE_FAILURE
    }

    /**
     * A completion-state write can commit even when the client observes a
     * connection error. Re-read the durable row before discarding uploaded
     * bytes; when the state cannot be read at all, keep the object so a later
     * reconciliation can adopt it rather than destroying a valid backup.
     */
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

    private void fail(Backup backup, String error) {
        backup.setStatus(BackupStatus.FAILED);
        backup.setCompletedAt(LocalDateTime.now(clock));
        backup.setError(error == null || error.isBlank() ? "Backup execution failed" : error);
        int failedRows = backupMapper.failUnlessCompleted(
                backup.getId(), backup.getCompletedAt(), backup.getError(), backup.getObjectKey());
        if (failedRows == 0) {
            log.warn("Backup {} already holds a durable COMPLETED row; FAILED transition skipped",
                    backup.getId());
        } else if (failedRows != 1) {
            log.error("Failed to persist FAILED backup state: {}, affected rows: {}",
                    backup.getId(), failedRows);
        }
    }

    private void deleteUploadedObject(String objectKey) {
        try {
            fileStorage.delete(objectKey);
        } catch (RuntimeException cleanupException) {
            log.warn("Failed to clean up uploaded backup object: {}", objectKey, cleanupException);
        }
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
}
