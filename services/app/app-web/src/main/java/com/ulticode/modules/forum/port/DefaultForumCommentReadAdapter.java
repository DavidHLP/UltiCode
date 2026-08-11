package com.ulticode.modules.forum.port;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.service.ForumCommentReadPort;
import com.ulticode.app.api.service.ForumCommentReadPort.ForumCommentPage;
import com.ulticode.app.api.service.ForumCommentReadPort.ForumCommentRow;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ADMIN-007: provider implementing {@link ForumCommentReadPort} inside
 * {@code backend-app} (forum implementation module) so the Admin
 * service's {@code ForumCommentModerator} lists / reads comments without
 * importing {@code ForumComment} or {@code ForumCommentMapper}.
 *
 * <p>All reads ignore logical delete, matching the former
 * {@code selectPageIgnoreDeleted} / {@code selectByIdIgnoreDeleted}
 * contract the admin moderator used to call directly.
 *
 * @author ulticode
 */
@Component
@Primary
@RequiredArgsConstructor
public class DefaultForumCommentReadAdapter implements ForumCommentReadPort {

    private final ForumCommentMapper forumCommentMapper;

    @Override
    public ForumCommentPage page(Boolean isFlagged, Boolean isDeleted, String search, String postId,
                                 String sortBy, String sortOrder, int page, int limit) {
        Page<ForumComment> pageResult = new Page<>(page, limit);
        List<ForumComment> records = forumCommentMapper.selectPageIgnoreDeleted(
                pageResult, isFlagged, isDeleted, search, postId, sortBy, sortOrder);
        pageResult.setRecords(records);

        List<ForumCommentRow> rows = records.stream().map(this::toRow).toList();
        return new ForumCommentPage(rows, pageResult.getTotal());
    }

    @Override
    public ForumCommentRow getById(String commentId) {
        ForumComment comment = forumCommentMapper.selectByIdIgnoreDeleted(commentId);
        return comment != null ? toRow(comment) : null;
    }

    private ForumCommentRow toRow(ForumComment comment) {
        return new ForumCommentRow(
                comment.getId(),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.getEditedAt(),
                comment.getAuthorId(),
                comment.getParentId(),
                comment.getPostId(),
                comment.getIsFlagged(),
                comment.getFlaggedReason(),
                comment.getFlaggedAt(),
                comment.getIsDeleted(),
                comment.getDeletedAt(),
                comment.getDeletedBy());
    }
}
