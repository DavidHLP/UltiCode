package com.ulticode.modules.moderation.port;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.forum.port.ForumCommentOwnerPort;
import com.ulticode.modules.forum.port.ForumOwnerPort;
import com.ulticode.modules.problem.port.ProblemOwnerPort;
import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.app.api.service.SolutionOwnerPort;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * Adapter implementing {@link ContentModerationPort} that dispatches
 * moderation operations (author resolution + flag status updates) by
 * entity type.
 *
 * <p><b>P7-MODERATION-CUTOVER-001:</b> all five entity types delegate to
 * their respective owner ports — {@link ForumOwnerPort},
 * {@link ForumCommentOwnerPort}, {@link SolutionOwnerPort},
 * {@link SolutionCommentOwnerPort}, {@link ProblemOwnerPort} — instead of
 * importing family mappers directly. This decouples the moderation adapter
 * from all family mapper types so those families can relocate to
 * backend-app without breaking compilation here.
 *
 * <p><b>Contract translation (F1 fix):</b> {@code forum_post} and
 * {@code solution} owner ports throw {@link BusinessException} when the
 * target entity has already been deleted (correct for controllers serving
 * 404). The moderation service, however, processes queue items
 * asynchronously and must treat a missing entity as a silent no-op — the
 * same semantics the original mapper SQL had (UPDATE ... WHERE id = ?
 * returns 0 rows without error). This adapter absorbs that translation:
 * {@code BusinessException} from forum/solution flag paths is caught and
 * silently ignored, restoring the original no-op behavior without
 * affecting owner-port callers that rely on the throw.
 */
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
        if (entityType == null || entityId == null) {
            return null;
        }
        switch (entityType) {
            case "forum_post":
                return forumOwnerPort.resolveAuthorId(entityId);
            case "forum_comment":
                return forumCommentOwnerPort.resolveAuthorId(entityId);
            case "solution":
                return solutionOwnerPort.resolveAuthorId(entityId);
            case "solution_comment":
                return solutionCommentOwnerPort.resolveAuthorId(entityId);
            case "problem":
                return problemOwnerPort.resolveAuthorId(entityId);
            default:
                return null;
        }
    }

    @Override
    public void updateFlagStatus(String entityType, String entityId, boolean isFlagged, String reason) {
        if (entityType == null || entityId == null) {
            return;
        }
        switch (entityType) {
            case "forum_post":
                try {
                    if (isFlagged) {
                        forumOwnerPort.flagPost(entityId, reason, LocalDateTime.now());
                    } else {
                        forumOwnerPort.unflagPost(entityId);
                    }
                } catch (BusinessException e) {
                    // Original mapper SQL returned 0 rows on missing entity;
                    // preserve silent no-op semantics here.
                }
                break;
            case "forum_comment":
                if (isFlagged) {
                    forumCommentOwnerPort.flagComment(entityId, reason);
                } else {
                    forumCommentOwnerPort.unflagComment(entityId);
                }
                break;
            case "solution":
                try {
                    if (isFlagged) {
                        solutionOwnerPort.flagSolution(entityId, reason, LocalDateTime.now());
                    } else {
                        solutionOwnerPort.unflagSolution(entityId);
                    }
                } catch (BusinessException e) {
                    // Original mapper SQL returned 0 rows on missing entity;
                    // preserve silent no-op semantics here.
                }
                break;
            case "solution_comment":
                if (isFlagged) {
                    solutionCommentOwnerPort.flagComment(entityId, reason);
                } else {
                    solutionCommentOwnerPort.unflagComment(entityId);
                }
                break;
            case "problem":
                problemOwnerPort.updateModerationFlag(entityId, isFlagged, reason);
                break;
            default:
                break;
        }
    }
}
