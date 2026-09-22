package com.ulticode.app.userprofile.provider;

import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.app.security.TrustedAdminActor;
import com.ulticode.app.storage.StorageCleanupOutbox;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.storage.StorageKeys;
import com.ulticode.modules.search.port.UserDirectoryQueryPort;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import com.ulticode.modules.user.port.AvatarUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;


/**
 * Dubbo provider implementing {@link ProfileWriteService}.
 *
 * <p>Writes exclusively to the {@code user_profiles} table. Never reads
 * or writes the Auth-owned {@code users} table.
 * <p>The RPC adapter delegates receipt claim, replay, conflict handling,
 * payload encoding, and transaction-owned finalization to
 * {@link CommandReceiptExecutor}. Profile mutation remains local until D2.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0", timeout = 5000, retries = 0)
@RequiredArgsConstructor
public class ProfileWriteProvider implements ProfileWriteService {

    private static final String SERVICE_NAME = "ProfileWriteService";
    private static final String OP_UPDATE = "updateProfile";
    private static final String OP_UPLOAD_AVATAR = "uploadAvatar";

    private final UserProfileMapper userProfileMapper;
    private final CommandReceiptExecutor receiptExecutor;
    private final AdminActorAuthorizer actorAuthorizer;
    private final StorageCleanupOutbox storageCleanupOutbox;
    private final ObjectProvider<UserDirectoryQueryPort> userDirectoryQueryPort;
    private final ObjectProvider<SearchDocumentChangedPublisher> searchPublisher;

    @Override
    @CacheEvict(value = "contestRanking", allEntries = true)
    public RpcResult<ProfileWriteResult> updateProfile(UpdateProfileCommand command) {
        RpcResult<ProfileWriteResult> rejected = rejectUntrustedActor(command);
        if (rejected != null) {
            return rejected;
        }
        try {
            return receiptExecutor.execute(
                    SERVICE_NAME,
                    OP_UPDATE,
                    command,
                    ProfileWriteResult.class,
                    traceId -> mutateProfile(command, traceId));
        } catch (BusinessException exception) {
            return mapBusinessFailure(exception, CommandReceiptExecutor.traceId(command));
        } catch (Exception exception) {
            log.error("Profile update failed for account: {}", accountId(command), exception);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE,
                    CommandReceiptExecutor.traceId(command));
        }
    }

    @Override
    @CacheEvict(value = "contestRanking", allEntries = true)
    public RpcResult<ProfileWriteResult> uploadAvatar(UploadAvatarCommand command) {
        RpcResult<ProfileWriteResult> rejected = rejectUntrustedActor(command);
        if (rejected != null) {
            return rejected;
        }
        try {
            return receiptExecutor.execute(
                    SERVICE_NAME,
                    OP_UPLOAD_AVATAR,
                    command,
                    ProfileWriteResult.class,
                    traceId -> mutateAvatar(command, traceId));
        } catch (BusinessException exception) {
            return mapBusinessFailure(exception, CommandReceiptExecutor.traceId(command));
        } catch (Exception exception) {
            log.error("Avatar update failed for account: {}", accountId(command), exception);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE,
                    CommandReceiptExecutor.traceId(command));
        }
    }

    private RpcResult<ProfileWriteResult> mutateProfile(
            UpdateProfileCommand command, String traceId) {
        String accountId = command.accountId();
        UserProfile profile = userProfileMapper.selectByIdForUpdate(accountId);
        boolean isNew = profile == null;
        String previousAvatar = isNew ? null : profile.getAvatar();
        if (isNew) {
            profile = new UserProfile();
            profile.setAccountId(accountId);
        }

        if (command.name() != null) {
            profile.setName(command.name());
        }
        if (command.avatar() != null) {
            if (AvatarUrls.reusesOwnedKey(accountId, command.avatar(), profile.getAvatar())) {
                log.warn("Rejected avatar reuse of a displaced object key for account {}", accountId);
                return RpcResult.failure(AppErrorCode.BAD_REQUEST, traceId);
            }
            profile.setAvatar(command.avatar());
        }
        if (command.bio() != null) {
            profile.setBio(command.bio());
        }
        if (command.company() != null) {
            profile.setCompany(command.company());
        }
        if (command.github() != null) {
            profile.setGithub(command.github());
        }
        if (command.location() != null) {
            profile.setLocation(command.location());
        }
        if (command.twitter() != null) {
            profile.setTwitter(command.twitter());
        }
        if (command.website() != null) {
            profile.setWebsite(command.website());
        }
        if (command.preferredLanguage() != null) {
            profile.setPreferredLanguage(command.preferredLanguage());
        }

        if (isNew) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
        queuePreviousAvatarCleanup(accountId, previousAvatar, profile.getAvatar());
        publishUserDocument(accountId);
        log.info("Profile updated for account: {}", accountId);
        return RpcResult.success(toResult(profile), traceId);
    }

    private RpcResult<ProfileWriteResult> mutateAvatar(
            UploadAvatarCommand command, String traceId) {
        String accountId = command.accountId();
        UserProfile profile = userProfileMapper.selectByIdForUpdate(accountId);
        boolean isNew = profile == null;
        String previousAvatar = isNew ? null : profile.getAvatar();
        if (isNew) {
            profile = new UserProfile();
            profile.setAccountId(accountId);
        }
        profile.setAvatar(command.avatarUrl());

        int affectedRows = isNew
                ? userProfileMapper.insert(profile)
                : userProfileMapper.updateById(profile);
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "Avatar profile write affected " + affectedRows + " rows");
        }

        publishUserDocument(accountId);
        queuePreviousAvatarCleanup(accountId, previousAvatar, profile.getAvatar());
        log.info("Avatar updated for account: {}", accountId);
        return RpcResult.success(toResult(profile), traceId);
    }

    /** Publish a complete user UPSERT from the row visible in this transaction. */
    private void publishUserDocument(String accountId) {
        UserDirectoryQueryPort directory = userDirectoryQueryPort.getIfAvailable();
        SearchDocumentChangedPublisher publisher = searchPublisher.getIfAvailable();
        if (directory == null || publisher == null) {
            return;
        }
        var directoryRow = directory.findById(accountId);
        if (directoryRow == null) {
            return;
        }
        var row = directoryRow.row();
        publisher.publishUser(row.getId(), row.getUsername(), row.getName(), row.getAvatar(), true);
    }

    private void queuePreviousAvatarCleanup(
            String accountId, String previousAvatar, String currentAvatar) {
        if (previousAvatar == null || previousAvatar.equals(currentAvatar)
                || !StorageKeys.isAvatarKey(previousAvatar)
                || !accountId.equals(StorageKeys.avatarAccountId(previousAvatar))) {
            return;
        }
        storageCleanupOutbox.enqueue(previousAvatar);
    }

    private <C extends WriteCommand> RpcResult<ProfileWriteResult> rejectUntrustedActor(C command) {
        if (command == null || hasMissingActorMetadata(command)) {
            return null;
        }
        ActorDelegation actor = command.actor();
        if (!TrustedAdminActor.isTrusted(actorAuthorizer, actor, "profile write")) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, CommandReceiptExecutor.traceId(command));
        }
        return null;
    }

    private static boolean hasMissingActorMetadata(WriteCommand command) {
        ActorDelegation actor = command.actor();
        return actor == null
                || actor.actorId() == null || actor.actorId().isBlank()
                || actor.delegatorId() == null || actor.delegatorId().isBlank();
    }

    private static String accountId(WriteCommand command) {
        if (command instanceof UpdateProfileCommand profile) {
            return profile.accountId();
        }
        if (command instanceof UploadAvatarCommand avatar) {
            return avatar.accountId();
        }
        return null;
    }

    private static ProfileWriteResult toResult(UserProfile profile) {
        return new ProfileWriteResult(
                profile.getAccountId(),
                profile.getName(),
                profile.getAvatar(),
                profile.getBio(),
                profile.getCompany(),
                profile.getGithub(),
                profile.getLocation(),
                profile.getTwitter(),
                profile.getWebsite(),
                profile.getPreferredLanguage());
    }

    private static <T> RpcResult<T> mapBusinessFailure(BusinessException exception, String traceId) {
        if (exception.getErrorCode() instanceof AppErrorCode appErrorCode) {
            return RpcResult.failure(appErrorCode, traceId);
        }
        return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
    }
}
