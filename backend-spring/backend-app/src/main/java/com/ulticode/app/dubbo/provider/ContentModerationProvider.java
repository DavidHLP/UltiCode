package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContentModerationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.moderation.service.ContentModerationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo Provider implementation of {@link ContentModerationService} in {@code backend-app}.
 *
 * <p>Delegates to {@link ContentModerationDomainService} for canonical write-side domain logic.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ContentModerationProvider implements ContentModerationService {

    private final ContentModerationDomainService domainService;

    @Override
    public RpcResult<ModerationApplyResultDTO> apply(ApplyModerationCommand command) {
        log.info("ContentModerationProvider.apply case={} contentId={} type={} action={} commandId={}",
                command.moderationCaseId(), command.contentId(),
                command.contentType(), command.action(), command.commandId());
        try {
            ModerationApplyResultDTO dto = domainService.apply(command);
            return RpcResult.success(dto, command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ContentModerationProvider.apply unexpected error contentId={} type={}",
                    command.contentId(), command.contentType(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        if (e.getErrorCode() == null) {
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
        return switch (e.getErrorCode().code()) {
            case 40400 -> RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            case 40000 -> RpcResult.failure(AppErrorCode.CONTENT_STATE_CONFLICT, traceId);
            default -> RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        };
    }
}
