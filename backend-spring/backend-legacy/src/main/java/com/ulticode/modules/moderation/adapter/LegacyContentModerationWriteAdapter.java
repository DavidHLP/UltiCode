package com.ulticode.modules.moderation.adapter;

import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.service.AdminForumService;
import com.ulticode.modules.admin.service.AdminSolutionService;
import com.ulticode.modules.moderation.port.ContentModerationWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyContentModerationWriteAdapter implements ContentModerationWritePort {

    private final AdminForumService forumService;
    private final AdminSolutionService solutionService;

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
                forumService.deletePost(command.contentId());
                yield ContentLifecycleState.DELETED;
            }
            case "solution" -> {
                solutionService.deleteSolution(command.contentId());
                yield ContentLifecycleState.DELETED;
            }
            default -> throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Unknown contentType: " + contentType);
        };
    }
}
