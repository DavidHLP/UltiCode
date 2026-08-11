package com.ulticode.modules.admin.service.comment;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.app.api.service.SolutionCommentReadPort;
import com.ulticode.app.api.service.SolutionCommentReadPort.SolutionCommentRow;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Solution-branch implementation of {@link CommentModerator}.
 *
 * <p>Owns the entire {@code "solution"} arm of the five moderated operations.
 * Was previously the {@code else if ("solution".equals(type)) ...} block
 * inside {@code AdminCommentServiceImpl}; extracted to keep the service
 * layer a thin router and to make the cross-mapper enrichment contract
 * local to the solution side of the seam.
 *
 * <p>ADMIN-006: the solution-comment entity and mapper are no longer
 * imported. Reads (list / single) go through
 * {@link SolutionCommentReadPort} and writes (flag / unflag / soft-delete)
 * through {@link SolutionCommentOwnerPort}; author summaries and solution
 * titles are enriched by {@link AdminCommentReadPort}. The deletion flow
 * keeps the shared {@link CurrentUserProvider} to stamp {@code deleted_by},
 * matching the contract
 * {@code AdminCommentServiceImpl#deleteComment} used to enforce.
 *
 * <p>Field differences from {@link ForumCommentModerator}: the row is a
 * {@link SolutionCommentRow} (with {@code content} / {@code userId} /
 * {@code solutionId} / {@code updatedAt} instead of {@code body} /
 * {@code authorId} / {@code postId} / {@code editedAt}); the
 * {@link #toAdminVO} projection handles the rename.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolutionCommentModerator implements CommentModerator {

    static final String TYPE = "solution";

    private final SolutionCommentReadPort solutionCommentReadPort;
    private final SolutionCommentOwnerPort solutionCommentOwnerPort;
    private final AdminCommentReadPort adminCommentReadPort;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public PageResult<AdminCommentVO> listComments(AdminCommentQueryDTO query, int page, int limit) {
        SolutionCommentReadPort.SolutionCommentPage commentPage = solutionCommentReadPort.page(
                query.getIsFlagged(), query.getIsDeleted(), query.getSearch(),
                query.getParentEntityId(), query.getSortBy(), query.getSortOrder(), page, limit);

        Set<String> authorIds = commentPage.rows().stream()
                .map(SolutionCommentRow::userId).collect(Collectors.toSet());
        Set<String> solutionIds = commentPage.rows().stream()
                .map(SolutionCommentRow::solutionId).collect(Collectors.toSet());

        Map<String, AdminCommentReadPort.AuthorSummary> authorMap =
                adminCommentReadPort.findAuthorSummariesByIds(authorIds);
        Map<String, String> solutionTitleMap =
                adminCommentReadPort.findSolutionTitlesByIds(solutionIds);

        List<AdminCommentVO> vos = commentPage.rows().stream()
                .map(c -> toAdminVO(c, authorMap.get(c.userId()), solutionTitleMap.get(c.solutionId())))
                .toList();

        return PageResult.of(vos, commentPage.total(), page, limit);
    }

    @Override
    public AdminCommentVO getComment(String commentId) {
        SolutionCommentRow comment = solutionCommentReadPort.getById(commentId);
        if (comment == null) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        AdminCommentReadPort.AuthorSummary author = adminCommentReadPort
                .findAuthorSummariesByIds(Set.of(comment.userId()))
                .get(comment.userId());
        String solutionTitle = adminCommentReadPort
                .findSolutionTitlesByIds(Set.of(comment.solutionId()))
                .get(comment.solutionId());
        return toAdminVO(comment, author, solutionTitle);
    }

    @Override
    public void flagComment(String commentId, String reason) {
        SolutionCommentOwnerPort.FlagResult res = solutionCommentOwnerPort.flagComment(commentId, reason);
        if (res == null) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        AuditContext.setUserId(res.authorUserId());
        AuditContext.setOldValues(Map.of(
            "isFlagged", res.previousIsFlagged(),
            "flaggedReason", res.previousFlaggedReason(),
            "type", TYPE
        ));
        AuditContext.setNewValues(Map.of(
            "isFlagged", true,
            "flaggedReason", reason != null ? reason : "",
            "type", TYPE
        ));
        log.info("Solution comment flagged: {}", commentId);
    }

    @Override
    public void unflagComment(String commentId) {
        SolutionCommentOwnerPort.FlagResult res = solutionCommentOwnerPort.unflagComment(commentId);
        if (res == null) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        AuditContext.setUserId(res.authorUserId());
        AuditContext.setOldValues(Map.of(
            "isFlagged", res.previousIsFlagged(),
            "flaggedReason", res.previousFlaggedReason(),
            "type", TYPE
        ));
        AuditContext.setNewValues(Map.of(
            "isFlagged", false,
            "flaggedReason", "",
            "type", TYPE
        ));
        log.info("Solution comment unflagged: {}", commentId);
    }

    @Override
    public void deleteComment(String commentId) {
        String deletedBy = safeActorId();
        SolutionCommentOwnerPort.DeleteResult res =
                solutionCommentOwnerPort.deleteComment(commentId, deletedBy);
        if (res == null) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        AuditContext.setUserId(res.authorUserId());
        AuditContext.setOldValues(Map.of("isDeleted", res.previousIsDeleted(), "type", TYPE));
        AuditContext.setNewValues(Map.of("isDeleted", true, "type", TYPE));
        log.info("Solution comment deleted: {} by {}", commentId, deletedBy);
    }

    private String safeActorId() {
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        return actorId;
    }

    static AdminCommentVO toAdminVO(SolutionCommentRow comment,
                                     AdminCommentReadPort.AuthorSummary author,
                                     String solutionTitle) {
        return new AdminCommentVO(
            comment.id(),
            comment.content(),
            comment.createdAt(),
            comment.updatedAt() != null ? comment.updatedAt() : comment.createdAt(),
            comment.userId(),
            comment.parentId(),
            TYPE,
            comment.solutionId(),
            solutionTitle,
            author != null ? new AdminCommentVO.AuthorInfo(author.id(), author.username(), author.avatar()) : null,
            comment.isFlagged(),
            comment.flaggedReason(),
            comment.flaggedAt(),
            comment.isDeleted(),
            comment.deletedAt(),
            comment.deletedBy()
        );
    }
}
