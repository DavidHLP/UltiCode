package com.ulticode.modules.moderation.port.adapter;

import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.service.ForumOwnerPort;
import com.ulticode.app.api.service.SolutionOwnerPort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.moderation.port.ContentModerationWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * App-side production adapter for the moderation domain module's
 * {@link ContentModerationWritePort} (P7-RELOCATE).
 *
 * <p>Mirrors the legacy {@code LegacyContentModerationWriteAdapter}
 * semantics exactly — only the {@code DELETE} action is supported, and the
 * dispatch goes to the app-api owner ports ({@link ForumOwnerPort},
 * {@link SolutionOwnerPort}) instead of the legacy admin services, which
 * are not on the backend-app classpath.
 */
@Component
@RequiredArgsConstructor
public class ContentModerationWriteAdapter implements ContentModerationWritePort {

    private final ForumOwnerPort forumOwnerPort;
    private final SolutionOwnerPort solutionOwnerPort;

    @Override
    public ModerationApplyResultDTO apply(ApplyModerationCommand command) {
        ContentLifecycleState newState = dispatch(command);
        return new ModerationApplyResultDTO(
                command.moderationCaseId(),
                command.contentId(),
                command.action(),
                newState);
    }

    private ContentLifecycleState dispatch(ApplyModerationCommand command) {
        String contentType = command.contentType();
        ModerationAction action = command.action();

        if (action != ModerationAction.DELETE) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Unsupported moderation action: " + action
                            + " (only DELETE is implemented)");
        }

        return switch (contentType) {
            case "forum_post", "forum" -> {
                forumOwnerPort.deletePost(command.contentId());
                yield ContentLifecycleState.DELETED;
            }
            case "solution" -> {
                solutionOwnerPort.deleteSolution(command.contentId());
                yield ContentLifecycleState.DELETED;
            }
            default -> throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Unknown contentType: " + contentType);
        };
    }
}
