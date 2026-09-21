package com.ulticode.modules.admin.storage;

import com.ulticode.common.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deletes Admin-uploaded objects that no owner row references.
 *
 * <p>An upload that App rejects leaves its object behind; recording the key in
 * the same request keeps the cleanup retryable, so an unavailable object store
 * or a crash cannot leak one object per retry attempt. Deletion is idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminStorageCleanup {

    private static final int SWEEP_LIMIT = 100;
    private static final int MAX_ERROR_LENGTH = 500;

    private final AdminStorageCleanupOutboxMapper outboxMapper;
    private final FileStoragePort fileStorage;

    public void enqueue(String objectKey) {
        outboxMapper.enqueue(objectKey);
    }

    /** Attempts one deletion; a failure leaves the key pending for the next sweep. */
    public void deletePending(String objectKey) {
        try {
            fileStorage.delete(objectKey);
        } catch (RuntimeException storageFailure) {
            recordFailure(objectKey, storageFailure);
            log.warn("Staged Admin object {} was not deleted; the cleanup outbox retries it",
                    objectKey, storageFailure);
            return;
        }
        try {
            if (outboxMapper.markDeleted(objectKey) != 1) {
                log.warn("Deleted staged Admin object {} but found no pending intent to clear",
                        objectKey);
            }
        } catch (RuntimeException databaseFailure) {
            // The bytes are gone; the stale intent costs one idempotent delete later.
            log.warn("Deleted staged Admin object {} but could not clear its intent", objectKey,
                    databaseFailure);
        }
    }

    @Scheduled(scheduler = "adminBackupScheduler",
            fixedDelayString = "${admin.storage-cleanup.interval-ms:300000}",
            initialDelayString = "30000")
    public int sweep() {
        List<String> pending;
        try {
            pending = outboxMapper.selectPendingObjectKeys(SWEEP_LIMIT);
        } catch (RuntimeException databaseFailure) {
            log.warn("Admin storage cleanup sweep could not read pending intents", databaseFailure);
            return 0;
        }
        for (String objectKey : pending) {
            deletePending(objectKey);
        }
        return pending.size();
    }

    private void recordFailure(String objectKey, RuntimeException failure) {
        try {
            outboxMapper.recordFailure(objectKey, describe(failure));
        } catch (RuntimeException databaseFailure) {
            log.warn("Could not record the pending deletion of staged Admin object {}", objectKey,
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
