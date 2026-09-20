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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

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
    @CacheEvict(value = "userStats", allEntries = true)
    public UserVO updateProfile(String userId, UpdateUserDTO updateDTO) {
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }

        UserProfile profile = userProfileMapper.selectById(userId);
        boolean isNew = profile == null;
        if (isNew) {
            profile = new UserProfile();
            profile.setAccountId(userId);
        }

        if (updateDTO.getName() != null) {
            profile.setName(updateDTO.getName());
        }
        if (updateDTO.getAvatar() != null) {
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

        publishUserDocument(userId);
        log.info("User profile updated: {}", userId);
        return toVO(profile);
    }

    @Override
    @Transactional
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

        UserProfile profile = userProfileMapper.selectById(userId);
        boolean isNew = profile == null;
        String previousAvatar = isNew ? null : profile.getAvatar();
        try {
            fileStorage.put(key, new ByteArrayInputStream(content), content.length, detected.contentType());
        } catch (RuntimeException exception) {
            log.warn("Avatar upload failed for user {}: {}", userId, exception.getMessage());
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Failed to save avatar");
        }

        try {
            if (isNew) {
                profile = new UserProfile();
                profile.setAccountId(userId);
            }
            profile.setAvatar(key);
            int affectedRows;
            if (isNew) {
                affectedRows = userProfileMapper.insert(profile);
            } else {
                affectedRows = userProfileMapper.updateById(profile);
            }
            if (affectedRows != 1) {
                throw new IllegalStateException("Avatar profile update affected " + affectedRows + " rows");
            }
        } catch (RuntimeException exception) {
            deleteQuietly(key);
            throw exception;
        }

        String previousKey = AvatarUrls.objectKey(userId, previousAvatar);
        if (previousKey != null && !previousKey.equals(key)) {
            deleteAfterCommit(previousKey);
        }
        publishUserDocument(userId);
        String displayUrl = AvatarUrls.resolve(userId, key);
        log.info("Avatar uploaded for user {}", userId);
        return displayUrl;
    }

    /**
     * Deletes the replaced object only after commit and outside the request thread.
     *
     * <p>Deleting earlier would lose data when a later step (for example the search-document outbox write)
     * rolls the transaction back: the row would still point at the old key, but the object would be gone.
     * Failures are logged and never fail the request.
     */
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

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            fileStorage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Failed to delete stale avatar object: {}", exception.getMessage());
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
        vo.setGithub(profile.getGithub());
        vo.setLocation(profile.getLocation());
        vo.setTwitter(profile.getTwitter());
        vo.setWebsite(profile.getWebsite());
        vo.setPreferredLanguage(profile.getPreferredLanguage());
        return vo;
    }
}
