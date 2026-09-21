package com.ulticode.modules.backup.service.impl;

import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.modules.backup.mapper.BackupDeletionTombstoneMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Removes dump bytes whose backup row is already deleted.
 *
 * <p>The deletion transaction writes the object key into the deletion
 * tombstone, so the intent outlives the request: the after-commit attempt is
 * only the fast path, and the sweep re-drives every intent that a crash or a
 * storage outage left pending. Object deletion is idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupObjectCleanup {

    private static final int SWEEP_LIMIT = 100;
    private static final int MAX_ERROR_LENGTH = 500;

    private final BackupDeletionTombstoneMapper backupDeletionTombstoneMapper;
    private final FileStoragePort fileStorage;

    /** Attempts one deletion; a failure leaves the tombstone pending for the next sweep. */
    public void deletePending(String objectKey) {
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
                log.warn("Deleted backup object {} but found no pending tombstone to clear for it",
                        objectKey);
            }
        } catch (RuntimeException databaseFailure) {
            // The bytes are gone; the stale intent only costs one more
            // idempotent delete on the next sweep.
            log.warn("Deleted backup object {} but could not clear its tombstone", objectKey,
                    databaseFailure);
        }
    }

    @Scheduled(scheduler = "adminBackupScheduler",
            fixedDelayString = "${backup.object-cleanup.interval-ms:300000}",
            initialDelayString = "30000")
    public int sweep() {
        List<String> pending;
        try {
            pending = backupDeletionTombstoneMapper.selectPendingObjectKeys(SWEEP_LIMIT);
        } catch (RuntimeException databaseFailure) {
            log.warn("Backup object cleanup sweep could not read pending tombstones", databaseFailure);
            return 0;
        }
        for (String objectKey : pending) {
            deletePending(objectKey);
        }
        return pending.size();
    }

    private void recordFailure(String objectKey, RuntimeException failure) {
        try {
            backupDeletionTombstoneMapper.recordObjectDeleteFailure(objectKey, describe(failure));
        } catch (RuntimeException databaseFailure) {
            log.warn("Could not record the pending deletion of backup object {}", objectKey,
                    databaseFailure);
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
