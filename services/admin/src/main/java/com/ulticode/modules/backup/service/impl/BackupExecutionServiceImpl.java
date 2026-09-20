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

/**
 * Async backup lifecycle. Dump bytes exist only in a secure container-local
 * temp file until they are streamed to object storage.
 */
@Slf4j
@Service
@Async
@RequiredArgsConstructor
public class BackupExecutionServiceImpl implements BackupExecutionService {

    private final BackupMapper backupMapper;
    private final Clock clock;
    private final BackupProcessPort backupProcessPort;
    private final FileStoragePort fileStorage;

    @Value("${backup.temp-dir:${java.io.tmpdir}/ulticode-backups}")
    private String backupTempDir;

    @Override
    public void executeBackup(String backupId) {
        Backup backup = backupMapper.selectById(backupId);
        if (backup == null) {
            log.error("Backup not found: {}", backupId);
            return;
        }

        Path tempFile = null;
        String objectKey = null;
        try {
            backup.setStatus(BackupStatus.IN_PROGRESS);
            backupMapper.updateById(backup);

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
            backupMapper.updateById(backup);
            log.info("Backup completed successfully: {}, size: {} bytes", backupId, size);
        } catch (Exception exception) {
            if (objectKey != null) {
                deleteUploadedObject(objectKey);
            }
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Backup execution failed for: {}", backupId, exception);
            fail(backup, exception.getMessage());
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private void fail(Backup backup, String error) {
        backup.setStatus(BackupStatus.FAILED);
        backup.setCompletedAt(LocalDateTime.now(clock));
        backup.setError(error == null || error.isBlank() ? "Backup execution failed" : error);
        backupMapper.updateById(backup);
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
