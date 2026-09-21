package com.ulticode.app.storage;

import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.outbox.OutboxDispatcher;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.common.storage.StorageKeys;
import com.ulticode.modules.user.port.AvatarUrls;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Deletes queued avatar objects with retry until each intent reaches a terminal state. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.cleanup.dispatcher.enabled",
        havingValue = "true", matchIfMissing = true)
public class StorageCleanupDispatcher {

    private static final int RETRY_BACKOFF_SECONDS = 30;

    private final OutboxDispatcher<StorageCleanupOutboxRecord> dispatcher;

    public StorageCleanupDispatcher(
            StorageCleanupOutboxMapper outboxMapper,
            FileStoragePort fileStorage,
            UserProfileMapper userProfileMapper) {
        this.dispatcher = new OutboxDispatcher<>(
                "app-storage-cleanup",
                new OutboxDispatcher.Adapter<>() {
                    @Override
                    public void reclaimStaleClaimed() {
                        outboxMapper.reclaimStaleClaimed();
                    }

                    @Override
                    public int claimPending(String claimOwner, int limit) {
                        return outboxMapper.claimPending(claimOwner, limit);
                    }

                    @Override
                    public List<StorageCleanupOutboxRecord> selectClaimed(String claimOwner) {
                        return outboxMapper.selectClaimed(claimOwner);
                    }

                    @Override
                    public String publish(StorageCleanupOutboxRecord record) {
                        return deleteUnlessCurrent(
                                record.getObjectKey(), fileStorage, userProfileMapper);
                    }

                    @Override
                    public int markDelivered(
                            StorageCleanupOutboxRecord record,
                            String claimOwner,
                            String publicationId) {
                        return outboxMapper.markDelivered(record.getId(), claimOwner);
                    }

                    @Override
                    public int markFailed(
                            StorageCleanupOutboxRecord record,
                            String claimOwner,
                            String error,
                            int maxAttempts) {
                        return outboxMapper.markRetry(
                                record.getId(), claimOwner, error, maxAttempts,
                                RETRY_BACKOFF_SECONDS);
                    }

                    @Override
                    public String recordId(StorageCleanupOutboxRecord record) {
                        return record.getObjectKey();
                    }
                });
    }

    private static String deleteUnlessCurrent(String key,
                                              FileStoragePort fileStorage,
                                              UserProfileMapper userProfileMapper) {
        if (StorageKeys.isAvatarKey(key)) {
            String accountId = StorageKeys.avatarAccountId(key);
            UserProfile profile = userProfileMapper.selectById(accountId);
            if (profile != null && key.equals(AvatarUrls.objectKey(accountId, profile.getAvatar()))) {
                log.warn("Storage cleanup intent {} still matches the current avatar of {}; skipping delete",
                        key, accountId);
                return "still-current";
            }
        }
        fileStorage.delete(key);
        return "deleted";
    }

    @Scheduled(fixedDelayString = "${app.storage.cleanup.dispatcher.interval-ms:2000}",
            initialDelayString = "5000")
    public int dispatch() {
        return dispatcher.dispatch();
    }

    @EventListener
    public void onContextClosed(ContextClosedEvent ignored) {
        dispatcher.beginDrain();
    }
}
