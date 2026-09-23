package com.ulticode.modules.user.port;

import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.storage.StorageCleanupOutbox;
import com.ulticode.app.userprofile.ProfileMutationModule;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * App-side adapter for {@link AppUserWritePort}.
 *
 * <p>Profile row mutation belongs to {@link ProfileMutationModule}; this
 * adapter owns the HTTP user, multipart, object-store, and display-URL work.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAppUserWritePort implements AppUserWritePort {

    private final UuidGenerator uuidGenerator;
    private final FileStoragePort fileStorage;
    private final ProfileMutationModule profileMutationModule;
    private final StorageCleanupOutbox storageCleanupOutbox;

    @Value("${app.storage.cleanup.upload-settle-seconds:900}")
    private int uploadSettleSeconds;

    @Override
    @CacheEvict(value = {
            ProfileMutationModule.USER_STATS_CACHE, ProfileMutationModule.CONTEST_RANKING_CACHE
    }, allEntries = true)
    public UserVO updateProfile(String userId, UpdateUserDTO updateDTO) {
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }
        ProfileWriteResult result = profileMutationModule.update(ProfileMutationModule.profilePatch(
                userId, updateDTO.getName(), updateDTO.getAvatar(), updateDTO.getBio(), updateDTO.getCompany(),
                updateDTO.getGithub(), updateDTO.getLocation(), updateDTO.getTwitter(), updateDTO.getWebsite(),
                updateDTO.getPreferredLanguage()));
        log.info("User profile updated: {}", userId);
        return toVO(result);
    }

    @Override
    @CacheEvict(value = ProfileMutationModule.CONTEST_RANKING_CACHE, allEntries = true)
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
            ProfileWriteResult result = profileMutationModule.replaceAvatar(userId, key);
            String displayUrl = AvatarUrls.resolve(userId, result.avatar());
            log.info("Avatar uploaded for user {}", userId);
            return displayUrl;
        } catch (RuntimeException exception) {
            // The profile write may have committed before the error surfaced and
            // may not be visible yet, so the staged object enters the delayed
            // cleanup queue: the dispatcher deletes it only after the settle
            // window and only when no profile row references it.
            queueAmbiguousAvatarCleanup(key);
            throw exception;
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

    private UserVO toVO(ProfileWriteResult result) {
        if (result == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(result.accountId());
        vo.setName(result.name());
        vo.setAvatar(AvatarUrls.resolve(result.accountId(), result.avatar()));
        vo.setBio(result.bio());
        vo.setCompany(result.company());
        vo.setGithub(result.github());
        vo.setLocation(result.location());
        vo.setTwitter(result.twitter());
        vo.setWebsite(result.website());
        vo.setPreferredLanguage(result.preferredLanguage());
        return vo;
    }
}
