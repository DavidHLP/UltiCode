package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContentModerationService;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.moderation.service.ContentModerationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo Provider implementation of {@link ContentModerationService} in
 * {@code backend-app}.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ContentModerationProvider implements ContentModerationService {

    private static final String SERVICE = "ContentModerationService";

    private final ContentModerationDomainService domainService;
    private final CommandReceiptExecutor receiptExecutor;
    private final AdminActorAuthorizer actorAuthorizer;

    @Override
    public RpcResult<ModerationApplyResultDTO> apply(ApplyModerationCommand command) {
        RpcResult<ModerationApplyResultDTO> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        log.info("ContentModerationProvider.apply case={} contentId={} type={} action={} commandId={}",
                command.moderationCaseId(), command.contentId(),
                command.contentType(), command.action(), command.commandId());
        try {
            return receiptExecutor.execute(
                    SERVICE,
                    command.action().name(),
                    command,
                    ModerationApplyResultDTO.class,
                    traceId -> {
                        try {
                            return RpcResult.success(domainService.apply(command), traceId);
                        } catch (BusinessException exception) {
                            return toFailure(exception, traceId);
                        } catch (Exception exception) {
                            log.error("ContentModerationProvider.apply unexpected error contentId={} type={}",
                                    command.contentId(), command.contentType(), exception);
                            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
                        }
                    });
        } catch (Exception exception) {
            log.error("ContentModerationProvider.apply receipt failure contentId={}",
                    command.contentId(), exception);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId(command));
        }
    }

    private <T> RpcResult<T> rejectIfNotAdmin(ApplyModerationCommand command) {
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

    private static <T> RpcResult<T> toFailure(BusinessException exception, String traceId) {
        if (exception.getErrorCode() == null) {
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
        return switch (exception.getErrorCode().code()) {
            case 40000 -> RpcResult.failure(AppErrorCode.CONTENT_STATE_CONFLICT, traceId);
            case 40400 -> RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            default -> RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        };
    }

    private static String traceId(ApplyModerationCommand command) {
        return command == null || command.trace() == null ? null : command.trace().traceId();
    }
}
