package com.ulticode.modules.admin.port.adapter;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.admin.port.UserProfilePort;
import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Admin-shell adapter for {@link UserProfilePort}.
 *
 * <p>Profile mutations are issued as App-owned commands to the public
 * {@link ProfileWriteService} (backend-app), the sole write owner of the
 * {@code user_profiles} table (canonical source). No local transaction wraps
 * the remote writes; provider unavailability or RPC failure fails closed with
 * an explicit {@link BusinessException} mapping the App error code.
 *
 * <p>Avatar upload preserves the legacy file-storage semantics (uploads
 * directory, UUID filename, content-type + size + extension validation);
 * the stored URL is then pushed via {@code ProfileWriteService.uploadAvatar}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserProfileAdapter implements UserProfilePort {

    private static final String ACTOR_TYPE = "ADMIN";

    @Autowired(required = false)
    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ProfileWriteService profileWriteService;

    private final UuidGenerator uuidGenerator;
    private final CurrentUserProvider currentUserProvider;

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

        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "File size exceeds 5MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "Only image files are allowed");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            String rawExt = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            ext = rawExt.replaceAll("[^a-z0-9]", "");
            if (!ext.isEmpty() && !ext.equals("jpg") && !ext.equals("jpeg") &&
                !ext.equals("png") && !ext.equals("gif") && !ext.equals("webp")) {
                throw new BusinessException(AdminErrorCode.BAD_REQUEST, "Invalid file extension");
            }
            ext = "." + ext;
        }
        String filename = uuidGenerator.newId() + ext;

        Path uploadDir = Paths.get("uploads/avatars");
        Path filePath = uploadDir.resolve(filename);
        try {
            Files.createDirectories(uploadDir);
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            log.error("Failed to save avatar for user {}: {}", userId, e.getMessage());
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "Failed to save avatar");
        }

        String avatarUrl = "/uploads/avatars/" + filename;
        try {
            updateAvatarUrl(userId, avatarUrl);
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(filePath);
            } catch (Exception cleanupException) {
                log.warn("Failed to clean up avatar file after profile update failure: {}",
                        filePath, cleanupException);
            }
            throw e;
        }

        log.info("Avatar uploaded for user {}: {}", userId, avatarUrl);
        return avatarUrl;
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
        return new ActorDelegation(ACTOR_TYPE, actorId, actorId, rationale);
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
