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
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
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
 * Forum-branch implementation of {@link CommentModerator}.
 *
 * <p>Owns the entire {@code "forum"} arm of the five moderated operations.
 * Was previously the {@code if ("forum".equals(type)) ...} block inside
 * {@code AdminCommentServiceImpl}; extracted to keep the service layer a
 * thin router and to make the cross-mapper enrichment contract local to
 * the forum side of the seam.
 *
 * <p>Writes go through {@link ForumCommentMapper}; reads are enriched by
 * {@link AdminCommentReadPort} with author summaries and forum-post titles.
 * The deletion flow uses the shared {@link CurrentUserProvider} to stamp
 * {@code deleted_by}, matching the contract
 * {@code AdminCommentServiceImpl#deleteComment} used to enforce.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForumCommentModerator implements CommentModerator {

    static final String TYPE = "forum";

    private final ForumCommentMapper forumCommentMapper;
    private final AdminCommentReadPort commentReadPort;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public PageResult<AdminCommentVO> listComments(AdminCommentQueryDTO query, int page, int limit) {
        Page<ForumComment> pageResult = new Page<>(page, limit);
        List<ForumComment> records = forumCommentMapper.selectPageIgnoreDeleted(
                pageResult, query.getIsFlagged(), query.getIsDeleted(), query.getSearch(),
                query.getParentEntityId(), query.getSortBy(), query.getSortOrder());
        pageResult.setRecords(records);

        Set<String> authorIds = records.stream()
                .map(ForumComment::getAuthorId).collect(Collectors.toSet());
        Set<String> postIds = records.stream()
                .map(ForumComment::getPostId).collect(Collectors.toSet());

        Map<String, AdminCommentReadPort.AuthorSummary> authorMap =
                commentReadPort.findAuthorSummariesByIds(authorIds);
        Map<String, String> postTitleMap = commentReadPort.findForumPostTitlesByIds(postIds);

        List<AdminCommentVO> vos = records.stream()
                .map(c -> toAdminVO(c, authorMap.get(c.getAuthorId()), postTitleMap.get(c.getPostId())))
                .collect(Collectors.toList());

        return PageResult.of(vos, pageResult.getTotal(), page, limit);
    }

    @Override
    public AdminCommentVO getComment(String commentId) {
        ForumComment comment = forumCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        AdminCommentReadPort.AuthorSummary author = commentReadPort
                .findAuthorSummariesByIds(Set.of(comment.getAuthorId()))
                .get(comment.getAuthorId());
        String postTitle = commentReadPort
                .findForumPostTitlesByIds(Set.of(comment.getPostId()))
                .get(comment.getPostId());
        return toAdminVO(comment, author, postTitle);
    }

    @Override
    public void flagComment(String commentId, String reason) {
        ForumComment comment = requireEntity(commentId);
        AuditContext.setUserId(comment.getAuthorId());
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
        forumCommentMapper.updateById(comment);
        log.info("Forum comment flagged: {}", commentId);
    }

    @Override
    public void unflagComment(String commentId) {
        ForumComment comment = requireEntity(commentId);
        AuditContext.setUserId(comment.getAuthorId());
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
        // Use LambdaUpdateWrapper with explicit set() so null values are written
        // (entity updateById is silently dropped by FieldStrategy.NOT_NULL).
        forumCommentMapper.update(null, new LambdaUpdateWrapper<ForumComment>()
            .eq(ForumComment::getId, commentId)
            .set(ForumComment::getIsFlagged, false)
            .set(ForumComment::getFlaggedReason, null)
            .set(ForumComment::getFlaggedAt, null));
        log.info("Forum comment unflagged: {}", commentId);
    }

    @Override
    public void deleteComment(String commentId) {
        ForumComment comment = requireEntity(commentId);
        AuditContext.setUserId(comment.getAuthorId());
        AuditContext.setOldValues(Map.of("isDeleted", comment.getIsDeleted(), "type", TYPE));
        AuditContext.setNewValues(Map.of("isDeleted", true, "type", TYPE));
        // Use LambdaUpdateWrapper to bypass MyBatis-Plus FieldStrategy and entity
        // @TableField(updateStrategy=...) uncertainty, so is_deleted=true and other
        // audit fields are reliably persisted in a single statement.
        String currentUserId = currentUserProvider.getCurrentUserId();
        forumCommentMapper.update(null, new LambdaUpdateWrapper<ForumComment>()
            .eq(ForumComment::getId, commentId)
            .set(ForumComment::getIsDeleted, true)
            .set(ForumComment::getDeletedAt, LocalDateTime.now(clock))
            .set(ForumComment::getDeletedBy, currentUserId));
        log.info("Forum comment deleted: {} by {}", commentId, currentUserId);
    }

    private ForumComment requireEntity(String commentId) {
        ForumComment comment = forumCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        return comment;
    }

    static AdminCommentVO toAdminVO(ForumComment comment,
                                     AdminCommentReadPort.AuthorSummary author,
                                     String postTitle) {
        return new AdminCommentVO(
            comment.getId(),
            comment.getBody(),
            comment.getCreatedAt(),
            comment.getEditedAt() != null ? comment.getEditedAt() : comment.getCreatedAt(),
            comment.getAuthorId(),
            comment.getParentId(),
            TYPE,
            comment.getPostId(),
            postTitle,
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
