package com.ulticode.modules.admin.storage;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.common.storage.StorageKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deletes Admin-uploaded objects that no owner row references.
 *
 * <p>An upload App rejects leaves its object behind; recording the key keeps the
 * cleanup retryable, so an unavailable object store or a crash cannot leak one
 * object per attempt. An upload whose RPC outcome is unknown is recorded with an
 * owner check instead: App may have committed the avatar, so the sweep asks for
 * the current value and keeps a referenced object rather than destroying it.
 * Deletion is idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminStorageCleanup {

    private static final int SWEEP_LIMIT = 100;
    private static final int MAX_ERROR_LENGTH = 500;

    private final AdminStorageCleanupOutboxMapper outboxMapper;
    private final FileStoragePort fileStorage;
    private final ObjectProvider<UserProfileQueryService> userProfileQueryService;

    /** Records a discarded staged object; the sweep deletes it. */
    public void enqueue(String objectKey) {
        outboxMapper.enqueue(objectKey);
    }

    /** Records a staged object whose write outcome is unknown; the sweep reconciles it first. */
    public void enqueueForOwnerCheck(String objectKey) {
        outboxMapper.enqueueForOwnerCheck(objectKey);
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

    /**
     * Resolves one ambiguous upload: keep the object while App's profile still
     * references it, delete it once App's answer proves it unreferenced, and
     * stay pending while App cannot answer.
     */
    public void verifyOwnerReference(String objectKey) {
        UserProfileQueryService profiles = userProfileQueryService.getIfAvailable();
        if (profiles == null) {
            log.warn("Owner check for staged Admin object {} is pending: no profile query service",
                    objectKey);
            return;
        }
        String accountId;
        String objectName;
        String displayPath;
        try {
            accountId = StorageKeys.avatarAccountId(objectKey);
            objectName = StorageKeys.avatarObjectName(objectKey);
            displayPath = StorageKeys.avatarDisplayPath(accountId, objectName);
        } catch (IllegalArgumentException exception) {
            // Not an avatar key this reconciler can reason about: leave it pending
            // rather than guessing about an object it does not understand.
            recordFailure(objectKey, exception);
            return;
        }
        RpcResult<UserProfileDTO> result;
        try {
            result = profiles.getProfileByAccountId(accountId);
        } catch (RuntimeException rpcFailure) {
            recordFailure(objectKey, rpcFailure);
            log.warn("Owner check for staged Admin object {} is pending: App is unavailable",
                    objectKey, rpcFailure);
            return;
        }
        if (result == null || !result.success()) {
            recordFailure(objectKey, new IllegalStateException(
                    result == null ? "empty profile response" : "profile query failed"));
            return;
        }
        UserProfileDTO profile = result.data();
        String storedAvatar = profile == null ? null : profile.avatar();
        if (objectKey.equals(storedAvatar) || displayPath.equals(storedAvatar)) {
            keep(objectKey);
            return;
        }
        deletePending(objectKey);
    }

    private void keep(String objectKey) {
        try {
            if (outboxMapper.markKept(objectKey) != 1) {
                log.warn("Kept staged Admin object {} but found no pending intent to clear", objectKey);
            }
        } catch (RuntimeException databaseFailure) {
            log.warn("Kept staged Admin object {} but could not clear its intent", objectKey,
                    databaseFailure);
        }
    }

    @Scheduled(scheduler = "adminBackupScheduler",
            fixedDelayString = "${admin.storage-cleanup.interval-ms:300000}",
            initialDelayString = "30000")
    public int sweep() {
        int swept = sweepPendingDeletions();
        swept += sweepPendingOwnerChecks();
        return swept;
    }

    private int sweepPendingDeletions() {
        List<String> pending;
        try {
            pending = outboxMapper.selectPendingDeletions(SWEEP_LIMIT);
        } catch (RuntimeException databaseFailure) {
            log.warn("Admin storage cleanup sweep could not read pending intents", databaseFailure);
            return 0;
        }
        for (String objectKey : pending) {
            deletePending(objectKey);
        }
        return pending.size();
    }

    private int sweepPendingOwnerChecks() {
        List<String> pending;
        try {
            pending = outboxMapper.selectPendingOwnerChecks(SWEEP_LIMIT);
        } catch (RuntimeException databaseFailure) {
            log.warn("Admin storage cleanup sweep could not read owner checks", databaseFailure);
            return 0;
        }
        for (String objectKey : pending) {
            verifyOwnerReference(objectKey);
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
