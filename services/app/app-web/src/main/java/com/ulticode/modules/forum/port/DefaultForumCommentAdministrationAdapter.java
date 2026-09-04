package com.ulticode.modules.forum.port;

import com.ulticode.app.api.command.ForumCommentModerationCommand;
import com.ulticode.app.api.dto.ForumCommentModerationResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ForumCommentAdministrationService;
import com.ulticode.modules.forum.port.ForumCommentOwnerPort;
import com.ulticode.modules.forum.port.ForumCommentOwnerPort.DeleteResult;
import com.ulticode.modules.forum.port.ForumCommentOwnerPort.FlagResult;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ADMIN-007: app-side implementation of
 * {@link ForumCommentAdministrationService}, delegating the actual writes
 * to the forum module's {@link ForumCommentOwnerPort} (which owns
 * {@code ForumCommentMapper}). Missing comments surface as
 * {@code CONTENT_NOT_FOUND} failures so the Admin consumer can map them
 * onto its own error codes.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultForumCommentAdministrationAdapter implements ForumCommentAdministrationService {

    private final ForumCommentOwnerPort forumCommentOwnerPort;

    @Override
    public RpcResult<ForumCommentModerationResultDTO> moderate(ForumCommentModerationCommand command) {
        String traceId = command.trace() != null ? command.trace().traceId() : null;
        switch (command.action()) {
            case FLAG -> {
                FlagResult result = forumCommentOwnerPort.flagComment(command.commentId(), command.reason());
                if (result == null) {
                    return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
                }
                return RpcResult.success(new ForumCommentModerationResultDTO(
                        command.commentId(), command.action(), result.authorId(),
                        result.previousWasFlagged(), result.previousReason(), false), traceId);
            }
            case UNFLAG -> {
                FlagResult result = forumCommentOwnerPort.unflagComment(command.commentId());
                if (result == null) {
                    return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
                }
                return RpcResult.success(new ForumCommentModerationResultDTO(
                        command.commentId(), command.action(), result.authorId(),
                        result.previousWasFlagged(), result.previousReason(), false), traceId);
            }
            case DELETE -> {
                DeleteResult result = forumCommentOwnerPort.deleteComment(
                        command.commentId(), command.actor().actorId());
                if (result == null) {
                    return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId);
                }
                return RpcResult.success(new ForumCommentModerationResultDTO(
                        command.commentId(), command.action(), result.authorUserId(),
                        false, null, result.previousIsDeleted()), traceId);
            }
            default -> throw new IllegalArgumentException("Unsupported action: " + command.action());
        }
    }
}
