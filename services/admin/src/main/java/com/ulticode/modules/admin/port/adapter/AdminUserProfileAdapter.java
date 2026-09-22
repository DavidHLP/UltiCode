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
import com.ulticode.common.storage.ImageContent;
import com.ulticode.common.storage.StorageKeys;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.common.uuid.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
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
    private final com.ulticode.modules.admin.storage.AdminStorageCleanup adminStorageCleanup;

    @Override
    @CacheEvict(value = "userStats", allEntries = true)
    public ProfileWriteResult updateProfile(UpdateProfileCommand command) {
        if (command == null || command.accountId() == null) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED);
        }

        RpcResult<ProfileWriteResult> result;
        try {
            result = invoke(() -> profileWriteService.updateProfile(command));
        } catch (RpcTransportException exception) {
            throw transportFailure(exception);
        }
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
            // The PUT may have committed before the error surfaced: record the key
            // so the sweep reconciles it against App's profile instead of leaking it.
            queueForOwnerCheck(key);
            log.warn("Avatar upload failed for user {}: {}", userId, exception.getMessage());
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "Failed to save avatar");
        }

        try {
            updateAvatarUrlWithOutcome(userId, key);
        } catch (RpcTransportException exception) {
            if (exception.isPreDispatch() || isTransactionRollback(exception)) {
                log.warn("Avatar profile update did not dispatch for user {}; deleting object {}", userId, key);
                deleteQuietly(key);
            } else {
                // Ambiguous outcome: App may have committed this key, so the
                // object is recorded for an owner-checked cleanup instead of
                // being kept forever or deleted blindly.
                log.warn("Avatar profile update outcome is unknown for user {}; queueing object {} for reconciliation",
                        userId, key);
                queueForOwnerCheck(key);
            }
            throw transportFailure(exception);
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
        // Shared sniffer: rejects an image whose decoded raster would exceed the
        // pixel budget before any decode happens (ImageContent.MAX_PIXELS).
        ImageContent.Detected detected;
        try {
            detected = ImageContent.detect(content);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, exception.getMessage());
        }
        if (detected == null) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST, "File content is not a supported image");
        }
        return new DetectedImage(detected.extension(), detected.contentType());
    }

    private void queueForOwnerCheck(String key) {
        try {
            adminStorageCleanup.enqueueForOwnerCheck(key);
        } catch (RuntimeException enqueueFailure) {
            log.warn("Could not queue staged avatar object {} for reconciliation", key, enqueueFailure);
        }
    }

    private void deleteQuietly(String key) {
        try {
            fileStorage.delete(key);
        } catch (RuntimeException exception) {
            // Durable intent: the upload request may be retried while the object
            // store is unavailable, and each retry would otherwise leak another
            // object. The sweep deletes the key idempotently.
            try {
                adminStorageCleanup.enqueue(key);
                log.warn("Failed to clean up avatar object {}; queued for retry", key, exception);
            } catch (RuntimeException enqueueFailure) {
                log.warn("Failed to clean up avatar object {} and could not queue it for retry", key,
                        enqueueFailure);
            }
        }
    }

    private record DetectedImage(String extension, String contentType) {
    }

    @Override
    public void updateAvatarUrl(String userId, String avatarUrl) {
        if (userId == null) {
            return;
        }
        try {
            updateAvatarUrlWithOutcome(userId, avatarUrl);
        } catch (RpcTransportException exception) {
            throw transportFailure(exception);
        }
    }

    private void updateAvatarUrlWithOutcome(String userId, String avatarUrl) {
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
            throw new RpcTransportException("ProfileWriteService unavailable", null, true);
        }
        try {
            return call.call();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("ProfileWriteService RPC failed: {}", exception.getMessage());
            throw new RpcTransportException(
                    "Profile write RPC failed", exception, isPreDispatchFailure(exception));
        }
    }

    private static boolean isPreDispatchFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            // FORBIDDEN_EXCEPTION (empty-protection off, directory forbidden
            // before select) is raised by the consumer only before the request
            // is dispatched to a provider, like NO_INVOKER_AVAILABLE_AFTER_FILTER.
            if (current instanceof RpcException rpcException
                    && (rpcException.isNoInvokerAvailableAfterFilter()
                    || rpcException.isForbidden()
                    || rpcException.getCode() == RpcException.REGISTRY_EXCEPTION)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static BusinessException transportFailure(RpcTransportException exception) {
        return new BusinessException(AdminErrorCode.UNKNOWN_ERROR, exception.getMessage());
    }

    private static final class RpcTransportException extends RuntimeException {
        private final boolean preDispatch;

        private RpcTransportException(String message, Throwable cause, boolean preDispatch) {
            super(message, cause);
            this.preDispatch = preDispatch;
        }

        private boolean isPreDispatch() {
            return preDispatch;
        }
    }
    private static boolean isTransactionRollback(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof UnexpectedRollbackException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
