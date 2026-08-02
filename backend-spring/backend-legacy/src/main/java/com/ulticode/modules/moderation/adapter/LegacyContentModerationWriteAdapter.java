package com.ulticode.modules.moderation.adapter;

import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.service.AdminForumService;
import com.ulticode.modules.admin.service.AdminSolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyContentModerationWriteAdapter implements com.ulticode.app.api.service.ModerationContentActionPort {

    private final AdminForumService forumService;
    private final AdminSolutionService solutionService;

    @Override
    public ContentLifecycleState deleteContent(String contentType, String contentId) {
        return switch (contentType) {
            case "forum_post", "forum" -> {
                forumService.deletePost(contentId);
                yield ContentLifecycleState.DELETED;
            }
            case "solution" -> {
                solutionService.deleteSolution(contentId);
                yield ContentLifecycleState.DELETED;
            }
            default -> throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Unknown contentType: " + contentType);
        };
    }
}
