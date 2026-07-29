package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContentModerationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.service.AdminForumService;
import com.ulticode.modules.admin.service.AdminSolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * P4-CUTOVER-004: Dubbo Provider implementation of
 * {@link ContentModerationService}.
 *
 * <p>Dispatches {@link #apply} by {@code contentType} to the appropriate
 * App-side admin service. Currently supports:
 * <ul>
 *   <li>{@code forum_post} → {@link AdminForumService#deletePost} for DELETE</li>
 *   <li>{@code solution} → {@link AdminSolutionService#deleteSolution} for DELETE</li>
 * </ul>
 *
 * <p>HIDE / RESTORE / UNDELETE actions are not yet implemented on the admin
 * services (only DELETE / soft-delete exists); the provider returns
 * {@code CONTENT_STATE_CONFLICT} for unsupported actions.
 *
 * <p>The Admin side keeps authoritative moderation case records
 * ({@code moderation_queue}, {@code moderation_actions}); the App side
 * merely enforces the lifecycle effect on the targeted content and returns
 * the new state.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ContentModerationProvider implements ContentModerationService {

    private final AdminForumService forumService;
    private final AdminSolutionService solutionService;

    @Override
    public RpcResult<ModerationApplyResultDTO> apply(ApplyModerationCommand command) {
        log.info("ContentModerationProvider.apply case={} contentId={} type={} action={} commandId={}",
                command.moderationCaseId(), command.contentId(),
                command.contentType(), command.action(), command.commandId());
        try {
            ContentLifecycleState newState = dispatch(command);
            ModerationApplyResultDTO dto = new ModerationApplyResultDTO(
                    command.moderationCaseId(),
                    command.contentId(),
                    command.action(),
                    newState);
            return RpcResult.success(dto, command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ContentModerationProvider.apply unexpected error contentId={} type={}",
                    command.contentId(), command.contentType(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    private ContentLifecycleState dispatch(ApplyModerationCommand command) {
        String contentType = command.contentType();
        ModerationAction action = command.action();

        // Only DELETE is currently supported; HIDE/RESTORE/UNDELETE
        // require admin-service methods that don't exist yet.
        if (action != ModerationAction.DELETE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Unsupported moderation action: " + action
                            + " (only DELETE is implemented)");
        }

        return switch (contentType) {
            case "forum_post", "forum" -> {
                forumService.deletePost(command.contentId());
                yield ContentLifecycleState.DELETED;
            }
            case "solution" -> {
                solutionService.deleteSolution(command.contentId());
                yield ContentLifecycleState.DELETED;
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Unknown contentType: " + contentType);
        };
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        return switch (e.getErrorCode().code()) {
            case 40400 -> // NOT_FOUND
                    RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
            case 40000 -> // BAD_REQUEST (unsupported action/type)
                    RpcResult.failure(AppErrorCode.CONTENT_STATE_CONFLICT, traceId);
            default -> RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        };
    }
}
