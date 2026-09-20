package com.ulticode.modules.admin.port.adapter;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.admin.port.UserProfilePort;
import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.common.auth.AdminActors;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.storage.FileStoragePort;
import com.ulticode.common.storage.StorageKeys;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.common.uuid.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/**
 * Admin-shell adapter for {@link UserProfilePort}.
 *
 * <p>Profile mutations are issued as App-owned commands to the public
 * {@link ProfileWriteService} (backend-app), the sole write owner of the
 * {@code user_profiles} table (canonical source). No local transaction wraps
 * the remote writes; provider unavailability or RPC failure fails closed with
 * an explicit {@link BusinessException} mapping the App error code.
 *
 * <p>Avatar bytes are validated using the same size, extension, magic-byte and
 * image-decoding rules as the App avatar path, then stored under the shared
 * object-storage key. The Dubbo command carries that key, never a local path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserProfileAdapter implements UserProfilePort {

    @Autowired(required = false)
    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ProfileWriteService profileWriteService;

    private final UuidGenerator uuidGenerator;
    private final CurrentUserProvider currentUserProvider;
    private final FileStoragePort fileStorage;

    @Override
    @CacheEvict(value = "userStats", allEntries = true)
    public ProfileWriteResult updateProfile(UpdateProfileCommand command) {
        if (command == null || command.accountId() == null) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED);
        }

        RpcResult<ProfileWriteResult> result = invoke(() -> profileWriteService.updateProfile(command));
        if (result == null || !result.success() || result.data() == null) {
            throw rpcFailure("Profile update failed on App provider", result);
        }

        log.info("User profile updated: {}", command.accountId());
        return result.data();
    }

    @Override
    public String uploadAvatar(String userId, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "File is required");
        }

        long maxSize = 5L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "File size exceeds 5MB limit");
        }
        validateExtension(file.getOriginalFilename());

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "Failed to read avatar");
        }
        if (content.length == 0) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "File is required");
        }
        if (content.length > maxSize) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "File size exceeds 5MB limit");
        }

        DetectedImage detected = detectImage(content);
        String objectName = uuidGenerator.newId() + "." + detected.extension();
        String key = StorageKeys.avatarKey(userId, objectName);
        try {
            fileStorage.put(key, new ByteArrayInputStream(content), content.length, detected.contentType());
        } catch (RuntimeException exception) {
            log.warn("Avatar upload failed for user {}: {}", userId, exception.getMessage());
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "Failed to save avatar");
        }

        try {
            updateAvatarUrl(userId, key);
        } catch (RuntimeException exception) {
            deleteQuietly(key);
            throw exception;
        }

        String displayUrl = StorageKeys.avatarDisplayPath(userId, objectName);
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
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "Invalid file extension");
        }
    }

    private static DetectedImage detectImage(byte[] content) {
        if (content.length >= 8
                && (content[0] & 0xff) == 0x89 && content[1] == 0x50 && content[2] == 0x4e
                && content[3] == 0x47 && content[4] == 0x0d && content[5] == 0x0a
                && (content[6] & 0xff) == 0x1a && content[7] == 0x0a
                && decodesImage(content)) {
            return new DetectedImage("png", "image/png");
        }
        if (content.length >= 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff && decodesImage(content)) {
            return new DetectedImage("jpg", "image/jpeg");
        }
        if (content.length >= 6
                && content[0] == 'G' && content[1] == 'I' && content[2] == 'F'
                && (content[3] == '8') && (content[4] == '7' || content[4] == '9') && content[5] == 'a'
                && decodesImage(content)) {
            return new DetectedImage("gif", "image/gif");
        }
        if (content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return new DetectedImage("webp", "image/webp");
        }
        throw new BusinessException(AdminErrorCode.BAD_REQUEST, "File content is not a supported image");
    }

    private static boolean decodesImage(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            return image != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private void deleteQuietly(String key) {
        try {
            fileStorage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Failed to clean up avatar object: {}", exception.getMessage());
        }
    }

    private record DetectedImage(String extension, String contentType) {
    }

    @Override
    public void updateAvatarUrl(String userId, String avatarUrl) {
        if (userId == null) {
            return;
        }
        UploadAvatarCommand command = new UploadAvatarCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                actor("admin avatar update"),
                trace(),
                userId,
                avatarUrl);
        RpcResult<ProfileWriteResult> result = invoke(() -> profileWriteService.uploadAvatar(command));
        if (result == null || !result.success() || result.data() == null) {
            throw rpcFailure("Avatar update failed on App provider", result);
        }
    }

    /**
     * Fail closed on provider unavailability or transport exceptions, applied
     * to every remote profile write path.
     */
    private RpcResult<ProfileWriteResult> invoke(RemoteCall call) {
        if (profileWriteService == null) {
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "ProfileWriteService unavailable");
        }
        try {
            return call.call();
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("ProfileWriteService RPC failed: {}", e.getMessage());
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "Profile write RPC failed");
        }
    }

    /**
     * Map an explicit App provider error payload onto the Admin error surface.
     */
    private BusinessException rpcFailure(String fallbackMessage, RpcResult<ProfileWriteResult> result) {
        if (result != null && result.error() != null) {
            int code = result.error().code();
            String message = result.error().message();
            String detail = message != null && !message.isBlank() ? message : fallbackMessage;
            AdminErrorCode adminCode = switch (code) {
                case 40000 -> AdminErrorCode.BAD_REQUEST;
                case 40100 -> AdminErrorCode.UNAUTHORIZED;
                case 40300 -> AdminErrorCode.FORBIDDEN;
                case 40401 -> AdminErrorCode.USER_NOT_FOUND;
                case 40901, 40902, 40903 -> AdminErrorCode.CONFLICT;
                case 50001 -> AdminErrorCode.UNKNOWN_ERROR;
                default -> AdminErrorCode.UNKNOWN_ERROR;
            };
            log.warn("Profile write rejected by App provider: code={} message={}", code, detail);
            return new BusinessException(adminCode, detail);
        }
        return new BusinessException(AdminErrorCode.UNKNOWN_ERROR, fallbackMessage);
    }

    private ActorDelegation actor(String rationale) {
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        return new ActorDelegation(
                AdminActors.typeOf(currentUserProvider),
                actorId, actorId, rationale);
    }

    private TraceMetadata trace() {
        String reqId = TraceIdUtil.current();
        if (reqId == null || reqId.isBlank()) {
            reqId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(reqId, null, null, null);
    }

    @FunctionalInterface
    private interface RemoteCall {
        RpcResult<ProfileWriteResult> call();
    }
}
