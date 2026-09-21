package com.ulticode.modules.user.port;

import com.ulticode.app.storage.StorageCleanupOutbox;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.modules.search.port.UserDirectoryQueryPort;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final StorageCleanupOutbox storageCleanupOutbox;
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
            // Durable with the same transaction: the dispatcher deletes the
            // replaced object only after this row change commits.
            storageCleanupOutbox.enqueue(previousKey);
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
}
