package com.ulticode.modules.forum.port;

import com.ulticode.modules.forum.port.ForumCommentOwnerPort;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link ForumCommentOwnerPort}.
 *
 * <p>Located in {@code backend-app} (forum implementation module) so it
 * can use the forum entity and mapper directly. Spring component scan
 * makes the bean available to any consumer that injects
 * {@link ForumCommentOwnerPort}.
 *
 * <p><b>Behavioral parity:</b> flag/unflag delegate to
 * {@code ForumCommentMapper.updateFlagStatus} — the same atomic
 * {@code @Update} SQL the moderation adapter previously called directly.
 *
 * <p>P7-RELOCATE-FORUM-001: relocated from {@code backend-legacy} when the
 * forum family moved to {@code backend-app}.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultForumCommentOwnerPort implements ForumCommentOwnerPort {

    private final ForumCommentMapper forumCommentMapper;

    @Override
    @Transactional
    public FlagResult flagComment(String commentId, String reason) {
        ForumComment comment = forumCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            return null;
        }

        boolean previousIsFlagged = Boolean.TRUE.equals(comment.getIsFlagged());
        String previousReason = comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "";

        int updated = forumCommentMapper.updateFlagStatus(
                commentId, true, reason != null ? reason : "");
        if (updated == 0) {
            return null;
        }
        log.info("Flagged forum comment {}", commentId);

        return new FlagResult(comment.getAuthorId(), previousIsFlagged, previousReason);
    }

    @Override
    @Transactional
    public FlagResult unflagComment(String commentId) {
        ForumComment comment = forumCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            return null;
        }

        boolean previousIsFlagged = Boolean.TRUE.equals(comment.getIsFlagged());
        String previousReason = comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "";

        int updated = forumCommentMapper.updateFlagStatus(commentId, false, null);
        if (updated == 0) {
            return null;
        }
        log.info("Unflagged forum comment {}", commentId);

        return new FlagResult(comment.getAuthorId(), previousIsFlagged, previousReason);
    }

    @Override
    public String resolveAuthorId(String commentId) {
        ForumComment comment = forumCommentMapper.selectById(commentId);
        return comment != null ? comment.getAuthorId() : null;
    }

    @Override
    @Transactional
    public DeleteResult deleteComment(String commentId, String deletedBy) {
        ForumComment comment = forumCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            return null;
        }

        boolean previousIsDeleted = Boolean.TRUE.equals(comment.getIsDeleted());

        int updated = forumCommentMapper.softDelete(commentId, deletedBy);
        if (updated == 0) {
            return null;
        }
        log.info("Deleted forum comment {} by {}", commentId, deletedBy);

        return new DeleteResult(comment.getAuthorId(), previousIsDeleted);
    }
}
