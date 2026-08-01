package com.ulticode.modules.moderation.port;

import com.ulticode.app.api.service.ForumCommentOwnerPort;
import com.ulticode.common.exception.BusinessException;
import java.time.LocalDateTime;
import com.ulticode.app.api.service.ForumOwnerPort;
import com.ulticode.modules.problem.port.ProblemOwnerPort;
import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.app.api.service.SolutionOwnerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link ContentModerationPort}.
 *
 * <p>Dispatches flag/author operations to the appropriate content-owner
 * port based on {@code entityType} string. This preserves the original
 * {@code ModerationServiceImpl} contract (string dispatch, no entity
 * types added to the port interface) while using the promoted app-api
 * ports for forum content.
 *
 * <p>P7-RELOCATE-FORUM-001: forum post/comment operations now delegate
 * to {@link ForumOwnerPort} and {@link ForumCommentOwnerPort} from
 * {@code com.ulticode.app.api.service} instead of directly using forum
 * mappers (which have moved to {@code backend-app}).
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultContentModerationAdapter implements ContentModerationPort {

    private final ForumOwnerPort forumOwnerPort;
    private final ForumCommentOwnerPort forumCommentOwnerPort;
    private final SolutionOwnerPort solutionOwnerPort;
    private final SolutionCommentOwnerPort solutionCommentOwnerPort;
    private final ProblemOwnerPort problemOwnerPort;

    @Override
    public String resolveAuthorId(String entityType, String entityId) {
        if (entityType == null) return null;
        if (entityId == null) return null;
        return switch (entityType.toUpperCase()) {
            case "FORUM_POST" -> forumOwnerPort.resolveAuthorId(entityId);
            case "FORUM_COMMENT" -> forumCommentOwnerPort.resolveAuthorId(entityId);
            case "SOLUTION" -> solutionOwnerPort.resolveAuthorId(entityId);
            case "SOLUTION_COMMENT" -> solutionCommentOwnerPort.resolveAuthorId(entityId);
            case "PROBLEM" -> problemOwnerPort.resolveAuthorId(entityId);
            default -> null;
        };
    }

    @Override
    public void updateFlagStatus(String entityType, String entityId, boolean isFlagged, String reason) {
        if (entityType == null) return;
        if (entityId == null) return;
        switch (entityType.toUpperCase()) {
            case "FORUM_POST" -> {
                try {
                    if (isFlagged) {
                        forumOwnerPort.flagPost(entityId, reason, LocalDateTime.now());
                    } else {
                        forumOwnerPort.unflagPost(entityId);
                    }
                } catch (BusinessException ignored) {
                    // Post already deleted — silently no-op (preserves original mapper SQL semantics)
                }
            }
            case "FORUM_COMMENT" -> {
                if (isFlagged) {
                    forumCommentOwnerPort.flagComment(entityId, reason);
                } else {
                    forumCommentOwnerPort.unflagComment(entityId);
                }
            }
            case "SOLUTION" -> {
                try {
                    if (isFlagged) {
                        solutionOwnerPort.flagSolution(entityId, reason, LocalDateTime.now());
                    } else {
                        solutionOwnerPort.unflagSolution(entityId);
                    }
                } catch (BusinessException ignored) {
                    // Solution already deleted — silently no-op (preserves original mapper SQL semantics)
                }
            }
            case "SOLUTION_COMMENT" -> {
                if (isFlagged) {
                    solutionCommentOwnerPort.flagComment(entityId, reason);
                } else {
                    solutionCommentOwnerPort.unflagComment(entityId);
                }
            }
            case "PROBLEM" -> problemOwnerPort.updateModerationFlag(entityId, isFlagged, reason);
            default -> log.warn("Unknown entity type in updateFlagStatus: {}", entityType);
        }
    }
}
