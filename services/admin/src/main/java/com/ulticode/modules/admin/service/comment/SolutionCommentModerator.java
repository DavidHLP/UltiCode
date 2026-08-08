package com.ulticode.modules.admin.service.comment;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
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
 * <p>Writes go through {@link SolutionCommentMapper}; reads are enriched by
 * {@link AdminCommentReadPort} with author summaries and solution titles.
 * The deletion flow uses the shared {@link CurrentUserProvider} to stamp
 * {@code deleted_by}, matching the contract
 * {@code AdminCommentServiceImpl#deleteComment} used to enforce.
 *
 * <p>Field differences from {@link ForumCommentModerator}: the entity is
 * {@code SolutionComment} (with {@code content} / {@code userId} /
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

    private final SolutionCommentMapper solutionCommentMapper;
    private final AdminCommentReadPort commentReadPort;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public PageResult<AdminCommentVO> listComments(AdminCommentQueryDTO query, int page, int limit) {
        Page<SolutionComment> pageResult = new Page<>(page, limit);
        List<SolutionComment> records = solutionCommentMapper.selectPageIgnoreDeleted(
                pageResult, query.getIsFlagged(), query.getIsDeleted(), query.getSearch(),
                query.getParentEntityId(), query.getSortBy(), query.getSortOrder());
        pageResult.setRecords(records);

        Set<String> authorIds = records.stream()
                .map(SolutionComment::getUserId).collect(Collectors.toSet());
        Set<String> solutionIds = records.stream()
                .map(SolutionComment::getSolutionId).collect(Collectors.toSet());

        Map<String, AdminCommentReadPort.AuthorSummary> authorMap =
                commentReadPort.findAuthorSummariesByIds(authorIds);
        Map<String, String> solutionTitleMap = commentReadPort.findSolutionTitlesByIds(solutionIds);

        List<AdminCommentVO> vos = records.stream()
                .map(c -> toAdminVO(c, authorMap.get(c.getUserId()), solutionTitleMap.get(c.getSolutionId())))
                .collect(Collectors.toList());

        return PageResult.of(vos, pageResult.getTotal(), page, limit);
    }

    @Override
    public AdminCommentVO getComment(String commentId) {
        SolutionComment comment = solutionCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        AdminCommentReadPort.AuthorSummary author = commentReadPort
                .findAuthorSummariesByIds(Set.of(comment.getUserId()))
                .get(comment.getUserId());
        String solutionTitle = commentReadPort
                .findSolutionTitlesByIds(Set.of(comment.getSolutionId()))
                .get(comment.getSolutionId());
        return toAdminVO(comment, author, solutionTitle);
    }

    @Override
    public void flagComment(String commentId, String reason) {
        SolutionComment comment = requireEntity(commentId);
        AuditContext.setUserId(comment.getUserId());
        AuditContext.setOldValues(Map.of(
            "isFlagged", comment.getIsFlagged(),
            "flaggedReason", comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "",
            "type", TYPE
        ));
        AuditContext.setNewValues(Map.of(
            "isFlagged", true,
            "flaggedReason", reason != null ? reason : "",
            "type", TYPE
        ));
        comment.setIsFlagged(true);
        comment.setFlaggedReason(reason);
        comment.setFlaggedAt(LocalDateTime.now(clock));
        solutionCommentMapper.updateById(comment);
        log.info("Solution comment flagged: {}", commentId);
    }

    @Override
    public void unflagComment(String commentId) {
        SolutionComment comment = requireEntity(commentId);
        AuditContext.setUserId(comment.getUserId());
        AuditContext.setOldValues(Map.of(
            "isFlagged", comment.getIsFlagged(),
            "flaggedReason", comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "",
            "type", TYPE
        ));
        AuditContext.setNewValues(Map.of(
            "isFlagged", false,
            "flaggedReason", "",
            "type", TYPE
        ));
        solutionCommentMapper.update(null, new LambdaUpdateWrapper<SolutionComment>()
            .eq(SolutionComment::getId, commentId)
            .set(SolutionComment::getIsFlagged, false)
            .set(SolutionComment::getFlaggedReason, null)
            .set(SolutionComment::getFlaggedAt, null));
        log.info("Solution comment unflagged: {}", commentId);
    }

    @Override
    public void deleteComment(String commentId) {
        SolutionComment comment = requireEntity(commentId);
        AuditContext.setUserId(comment.getUserId());
        AuditContext.setOldValues(Map.of("isDeleted", comment.getIsDeleted(), "type", TYPE));
        AuditContext.setNewValues(Map.of("isDeleted", true, "type", TYPE));
        // Use LambdaUpdateWrapper to bypass MyBatis-Plus FieldStrategy and entity
        // @TableField(updateStrategy=...) uncertainty, so is_deleted=true and other
        // audit fields are reliably persisted in a single statement.
        String currentUserId = currentUserProvider.getCurrentUserId();
        solutionCommentMapper.update(null, new LambdaUpdateWrapper<SolutionComment>()
            .eq(SolutionComment::getId, commentId)
            .set(SolutionComment::getIsDeleted, true)
            .set(SolutionComment::getDeletedAt, LocalDateTime.now(clock))
            .set(SolutionComment::getDeletedBy, currentUserId));
        log.info("Solution comment deleted: {} by {}", commentId, currentUserId);
    }

    private SolutionComment requireEntity(String commentId) {
        SolutionComment comment = solutionCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        return comment;
    }

    static AdminCommentVO toAdminVO(SolutionComment comment,
                                     AdminCommentReadPort.AuthorSummary author,
                                     String solutionTitle) {
        return new AdminCommentVO(
            comment.getId(),
            comment.getContent(),
            comment.getCreatedAt(),
            comment.getUpdatedAt() != null ? comment.getUpdatedAt() : comment.getCreatedAt(),
            comment.getUserId(),
            comment.getParentId(),
            TYPE,
            comment.getSolutionId(),
            solutionTitle,
            author != null ? new AdminCommentVO.AuthorInfo(author.id(), author.username(), author.avatar()) : null,
            comment.getIsFlagged(),
            comment.getFlaggedReason(),
            comment.getFlaggedAt(),
            comment.getIsDeleted(),
            comment.getDeletedAt(),
            comment.getDeletedBy()
        );
    }
}
