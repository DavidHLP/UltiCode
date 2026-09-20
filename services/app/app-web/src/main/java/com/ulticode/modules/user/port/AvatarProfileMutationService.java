package com.ulticode.modules.user.port;

import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.modules.search.port.UserDirectoryQueryPort;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

/**
 * Performs the database and search side of an avatar upload in a short
 * transaction. The object-store PUT is intentionally owned by the caller so
 * storage latency never holds an App database connection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarProfileMutationService {

    private final UserProfileMapper userProfileMapper;
    private final FileStoragePort fileStorage;
    private final UserDirectoryQueryPort userDirectoryQueryPort;
    private final SearchDocumentChangedPublisher searchPublisher;

    @Transactional
    public void persistAvatar(String userId, String key) {
        UserProfile profile = userProfileMapper.selectById(userId);
        boolean isNew = profile == null;
        String previousAvatar = isNew ? null : profile.getAvatar();
        if (isNew) {
            profile = new UserProfile();
            profile.setAccountId(userId);
        }
        profile.setAvatar(key);

        int affectedRows = isNew
                ? userProfileMapper.insert(profile)
                : userProfileMapper.updateById(profile);
        if (affectedRows != 1) {
            throw new IllegalStateException("Avatar profile update affected " + affectedRows + " rows");
        }

        String previousKey = AvatarUrls.objectKey(userId, previousAvatar);
        if (previousKey != null && !previousKey.equals(key)) {
            deleteAfterCommit(previousKey);
        }
        publishUserDocument(userId);
    }

    private void publishUserDocument(String userId) {
        var directoryRow = userDirectoryQueryPort.findById(userId);
        if (directoryRow == null) {
            return;
        }
        var row = directoryRow.row();
        searchPublisher.publishUser(row.getId(), row.getUsername(), row.getName(), row.getAvatar(), true);
    }

    /** Delete the replaced object only after the profile transaction commits. */
    private void deleteAfterCommit(String key) {
        Runnable cleanup = () -> deleteQuietly(key);
        Runnable submitCleanup = () -> {
            try {
                CompletableFuture.runAsync(cleanup).exceptionally(exception -> {
                    log.warn("Async cleanup failed for replaced avatar object {}: {}",
                            key, exception.getMessage());
                    return null;
                });
            } catch (RuntimeException exception) {
                log.warn("Failed to schedule cleanup for replaced avatar object {}: {}",
                        key, exception.getMessage());
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

    private void deleteQuietly(String key) {
        try {
            fileStorage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Failed to delete stale avatar object: {}", exception.getMessage());
        }
    }
}
