package com.ulticode.modules.forum.port;

import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link ForumCommentOwnerPort}.
 *
 * <p><b>Behavioral parity:</b> flag/unflag delegate to
 * {@code ForumCommentMapper.updateFlagStatus} — the same atomic
 * {@code @Update} SQL the moderation adapter previously called directly.
 * The mapper SQL uses {@code NOW()} for the timestamp, so no caller-supplied
 * time is needed. Pre-mutation state is captured via
 * {@code selectByIdIgnoreDeleted} so {@link FlagResult} is populated even
 * for soft-deleted comments (the raw SQL has no {@code is_deleted = 0}
 * predicate). Author resolution uses {@code selectById} to match the
 * original adapter's read behavior.
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

        forumCommentMapper.updateFlagStatus(commentId, true, reason != null ? reason : "");
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

        forumCommentMapper.updateFlagStatus(commentId, false, null);
        log.info("Unflagged forum comment {}", commentId);

        return new FlagResult(comment.getAuthorId(), previousIsFlagged, previousReason);
    }

    @Override
    public String resolveAuthorId(String commentId) {
        ForumComment comment = forumCommentMapper.selectById(commentId);
        return comment != null ? comment.getAuthorId() : null;
    }
}
