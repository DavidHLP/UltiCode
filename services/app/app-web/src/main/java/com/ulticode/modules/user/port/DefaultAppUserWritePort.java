package com.ulticode.modules.user.port;

import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.common.storage.ImageContent;
import com.ulticode.common.storage.StorageKeys;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * App-side adapter for {@link AppUserWritePort}.
 *
 * <p>Profile mutations write exclusively to the App-owned
 * {@code user_profiles} table (canonical source). Avatar bytes are stored in
 * the mandatory shared object store before the profile row is updated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAppUserWritePort implements AppUserWritePort {


    private final UserProfileMapper userProfileMapper;
    private final UuidGenerator uuidGenerator;
    private final FileStoragePort fileStorage;
    private final com.ulticode.modules.search.port.UserDirectoryQueryPort userDirectoryQueryPort;
    private final com.ulticode.modules.search.source.SearchDocumentChangedPublisher searchPublisher;
    private final AvatarProfileMutationService avatarProfileMutationService;
    private final com.ulticode.app.storage.StorageCleanupOutbox storageCleanupOutbox;

    @org.springframework.beans.factory.annotation.Value("${app.storage.cleanup.upload-settle-seconds:900}")
    private int uploadSettleSeconds;

    /** Publish a complete user-document UPSERT after a profile write. */
    private void publishUserDocument(String userId) {
        var directoryRow = userDirectoryQueryPort.findById(userId);
        if (directoryRow == null) {
            return;
        }
        var row = directoryRow.row();
        searchPublisher.publishUser(row.getId(), row.getUsername(), row.getName(), row.getAvatar(), true);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"userStats", "contestRanking"}, allEntries = true)
    public UserVO updateProfile(String userId, UpdateUserDTO updateDTO) {
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }

        // Locking read: a concurrent avatar upload must not be overwritten by
        // this full-entity update with a stale avatar value.
        UserProfile profile = userProfileMapper.selectByIdForUpdate(userId);
        boolean isNew = profile == null;
        String previousAvatar = isNew ? null : profile.getAvatar();
        if (isNew) {
            profile = new UserProfile();
            profile.setAccountId(userId);
        }

        if (updateDTO.getName() != null) {
            profile.setName(updateDTO.getName());
        }
        if (updateDTO.getAvatar() != null) {
            if (AvatarUrls.reusesOwnedKey(userId, updateDTO.getAvatar(), profile.getAvatar())) {
                throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                        "Avatar changes must use the avatar upload endpoint");
            }
            profile.setAvatar(updateDTO.getAvatar());
        }
        if (updateDTO.getBio() != null) {
            profile.setBio(updateDTO.getBio());
        }
        if (updateDTO.getCompany() != null) {
            profile.setCompany(updateDTO.getCompany());
        }
        if (updateDTO.getGithub() != null) {
            profile.setGithub(updateDTO.getGithub());
        }
        if (updateDTO.getLocation() != null) {
            profile.setLocation(updateDTO.getLocation());
        }
        if (updateDTO.getTwitter() != null) {
            profile.setTwitter(updateDTO.getTwitter());
        }
        if (updateDTO.getWebsite() != null) {
            profile.setWebsite(updateDTO.getWebsite());
        }
        if (updateDTO.getPreferredLanguage() != null) {
            profile.setPreferredLanguage(updateDTO.getPreferredLanguage());
        }

        if (isNew) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
        // A generic profile update may carry the avatar field: the displaced
        // object needs the same durable cleanup intent as an upload replacement.
        String displacedKey = AvatarUrls.objectKey(userId, previousAvatar);
        if (displacedKey != null && !displacedKey.equals(profile.getAvatar())) {
            // Inside the transaction: a failed insert must roll the profile write
            // back, otherwise the displaced object loses its only cleanup intent.
            storageCleanupOutbox.enqueue(displacedKey);
        }

        publishUserDocument(userId);
        log.info("User profile updated: {}", userId);
        return toVO(profile);
    }

    @Override
    @CacheEvict(value = "contestRanking", allEntries = true)
    public String uploadAvatar(String userId, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "File is required");
        }
        long maxSize = 5L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "File size exceeds 5MB limit");
        }
        validateExtension(file.getOriginalFilename());

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Failed to read avatar");
        }
        if (content.length == 0) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "File is required");
        }
        if (content.length > maxSize) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "File size exceeds 5MB limit");
        }
        DetectedImage detected = detectImage(content);
        String objectName = uuidGenerator.newId() + "." + detected.extension();
        String key = StorageKeys.avatarKey(userId, objectName);

        try {
            fileStorage.put(key, new ByteArrayInputStream(content), content.length, detected.contentType());
        } catch (RuntimeException exception) {
            // The PUT may still commit server-side after this timeout: record the
            // intent with a settle grace so a late commit is not deleted, and the
            // dispatcher's current-avatar check protects an already-committed key.
            queueAmbiguousAvatarCleanup(key);
            log.warn("Avatar upload failed for user {}: {}", userId, exception.getMessage());
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Failed to save avatar");
        }

        try {
            avatarProfileMutationService.persistAvatar(userId, key);
        } catch (RuntimeException exception) {
            // The profile write may have committed before the error surfaced and
            // may not be visible yet, so the staged object enters the delayed
            // cleanup queue: the dispatcher deletes it only after the settle
            // window and only when no profile row references it.
            queueAmbiguousAvatarCleanup(key);
            throw exception;
        }

        String displayUrl = AvatarUrls.resolve(userId, key);
        log.info("Avatar uploaded for user {}", userId);
        return displayUrl;
    }

    private static void validateExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return;
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String extension = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!switch (extension) {
            case "jpg", "jpeg", "png", "gif", "webp" -> true;
            default -> false;
        }) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Invalid file extension");
        }
    }

    private static DetectedImage detectImage(byte[] content) {
        try {
            ImageContent.Detected detected = ImageContent.detect(content);
            if (detected == null) {
                throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                        "File content is not a supported image");
            }
            return new DetectedImage(detected.extension(), detected.contentType());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, exception.getMessage());
        }
    }

    /** Records an ambiguous PUT intent with the configured settle grace. */
    private void queueAmbiguousAvatarCleanup(String key) {
        if (key == null) {
            return;
        }
        try {
            storageCleanupOutbox.enqueueAfterGrace(key, uploadSettleSeconds);
        } catch (RuntimeException exception) {
            log.warn("Failed to queue the ambiguous avatar object: {}", exception.getMessage());
        }
    }

    private record DetectedImage(String extension, String contentType) {
    }

    private UserVO toVO(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(profile.getAccountId());
        vo.setName(profile.getName());
        vo.setAvatar(AvatarUrls.resolve(profile.getAccountId(), profile.getAvatar()));
        vo.setBio(profile.getBio());
        vo.setCompany(profile.getCompany());
        vo.setGithub(profile.getGithub());
        vo.setLocation(profile.getLocation());
        vo.setTwitter(profile.getTwitter());
        vo.setWebsite(profile.getWebsite());
        vo.setPreferredLanguage(profile.getPreferredLanguage());
        return vo;
    }
}
