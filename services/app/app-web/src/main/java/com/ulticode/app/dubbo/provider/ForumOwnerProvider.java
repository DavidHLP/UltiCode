package com.ulticode.app.dubbo.provider;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.ForumPostModerationCommand;
import com.ulticode.app.api.dto.ForumPostModerationResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ForumPostAdministrationService;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.forum.port.DefaultForumOwnerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Exposes the App-owned forum-post moderation command boundary.
 *
 * <p>The raw {@code ForumOwnerPort} is an internal App port and is not
 * exported over Dubbo. Commands are authorized and deduplicated before the
 * owner mutation runs in the same local transaction.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ForumOwnerProvider implements ForumPostAdministrationService {

    private static final String SERVICE = "ForumPostAdministrationService";

    private final DefaultForumOwnerPort delegate;
    private final CommandReceiptExecutor receiptExecutor;
    private final AdminActorAuthorizer actorAuthorizer;

    @Override
    public RpcResult<ForumPostModerationResultDTO> moderate(ForumPostModerationCommand command) {
        RpcResult<ForumPostModerationResultDTO> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        try {
            return receiptExecutor.execute(
                    SERVICE,
                    command.action().name(),
                    command,
                    ForumPostModerationResultDTO.class,
                    traceId -> delegate.moderate(command));
        } catch (BusinessException exception) {
            return toFailure(exception, traceId(command));
        } catch (Exception exception) {
            log.error("Forum post moderation failed postId={} action={}",
                    command.postId(), command.action(), exception);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId(command));
        }
    }

    private static <T> RpcResult<T> toFailure(BusinessException exception, String traceId) {
        if (exception.getErrorCode() == null) {
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
        return switch (exception.getErrorCode().code()) {
            case 40400 -> RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            case 40900 -> RpcResult.failure(AppErrorCode.CONTENT_STATE_CONFLICT, traceId);
            default -> RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        };
    }

    private <T> RpcResult<T> rejectIfNotAdmin(ForumPostModerationCommand command) {
        if (command == null) {
            return RpcResult.failure(AppErrorCode.BAD_REQUEST, null);
        }
        ActorDelegation actor = command.actor();
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()
                || actor.delegatorId() == null || actor.delegatorId().isBlank()
                || !actor.actorId().equals(actor.delegatorId())
                || (!"ADMIN".equalsIgnoreCase(actor.actorType())
                && !"SUPER_ADMIN".equalsIgnoreCase(actor.actorType()))) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId(command));
        }
        if (!actorAuthorizer.isAuthorized(actor)) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId(command));
        }
        return null;
    }

    private static String traceId(ForumPostModerationCommand command) {
        return command == null || command.trace() == null ? null : command.trace().traceId();
    }
}
