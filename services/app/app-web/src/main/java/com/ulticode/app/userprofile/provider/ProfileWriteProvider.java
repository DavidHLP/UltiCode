package com.ulticode.app.userprofile.provider;

import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.app.security.TrustedAdminActor;
import com.ulticode.app.userprofile.ProfileMutationModule;
import com.ulticode.app.userprofile.ProfilePatch;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.cache.annotation.CacheEvict;

/**
 * Dubbo provider implementing {@link ProfileWriteService}.
 *
 * <p>Owns trusted-actor, receipt, command/result/error mapping, and RPC cache
 * concerns. Shared profile row mutation belongs to {@link ProfileMutationModule}.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0", timeout = 5000, retries = 0)
@RequiredArgsConstructor
public class ProfileWriteProvider implements ProfileWriteService {

    private static final String SERVICE_NAME = "ProfileWriteService";
    private static final String OP_UPDATE = "updateProfile";
    private static final String OP_UPLOAD_AVATAR = "uploadAvatar";

    private final ProfileMutationModule profileMutationModule;
    private final CommandReceiptExecutor receiptExecutor;
    private final AdminActorAuthorizer actorAuthorizer;

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
                    traceId -> RpcResult.success(profileMutationModule.update(toPatch(command)), traceId));
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
                    traceId -> RpcResult.success(
                            profileMutationModule.replaceAvatar(command.accountId(), command.avatarUrl()),
                            traceId));
        } catch (BusinessException exception) {
            return mapBusinessFailure(exception, CommandReceiptExecutor.traceId(command));
        } catch (Exception exception) {
            log.error("Avatar update failed for account: {}", accountId(command), exception);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE,
                    CommandReceiptExecutor.traceId(command));
        }
    }

    private static ProfilePatch toPatch(UpdateProfileCommand command) {
        return new ProfilePatch(
                command.accountId(),
                command.name(),
                command.avatar(),
                command.bio(),
                command.company(),
                command.github(),
                command.location(),
                command.twitter(),
                command.website(),
                command.preferredLanguage());
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

    private static <T> RpcResult<T> mapBusinessFailure(BusinessException exception, String traceId) {
        if (exception.getErrorCode() instanceof AppErrorCode appErrorCode) {
            return RpcResult.failure(appErrorCode, traceId);
        }
        if (exception.getErrorCode() == BaseErrorCode.BAD_REQUEST) {
            return RpcResult.failure(AppErrorCode.BAD_REQUEST, traceId);
        }
        return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
    }
}
