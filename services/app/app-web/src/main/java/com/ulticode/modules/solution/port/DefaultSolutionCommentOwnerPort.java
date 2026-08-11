package com.ulticode.modules.solution.port;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Default implementation of {@link SolutionCommentOwnerPort}.
 *
 * <p><b>Behavioral parity:</b> flag/unflag delegate to
 * {@code SolutionCommentMapper.updateFlagStatus} — the same atomic
 * {@code @Update} SQL the moderation adapter previously called directly.
 * The mapper SQL uses {@code NOW()} for the timestamp, so no caller-supplied
 * time is needed. Pre-mutation state is captured via
 * {@code selectByIdIgnoreDeleted} so {@link SolutionCommentOwnerPort.FlagResult} is populated even
 * for soft-deleted comments. Author/parent resolves use {@code selectById}
 * to match the original adapter's read behavior.
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultSolutionCommentOwnerPort implements SolutionCommentOwnerPort {

    private final SolutionCommentMapper solutionCommentMapper;

    @Override
    @Transactional
    public SolutionCommentOwnerPort.FlagResult flagComment(String commentId, String reason) {
        SolutionComment comment = solutionCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            return null;
        }

        boolean previousIsFlagged = Boolean.TRUE.equals(comment.getIsFlagged());
        String previousReason = comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "";

        solutionCommentMapper.updateFlagStatus(commentId, true, reason != null ? reason : "");
        log.info("Flagged solution comment {}", commentId);

        return new SolutionCommentOwnerPort.FlagResult(comment.getUserId(), previousIsFlagged, previousReason);
    }

    @Override
    @Transactional
    public SolutionCommentOwnerPort.FlagResult unflagComment(String commentId) {
        SolutionComment comment = solutionCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            return null;
        }

        boolean previousIsFlagged = Boolean.TRUE.equals(comment.getIsFlagged());
        String previousReason = comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "";

        solutionCommentMapper.updateFlagStatus(commentId, false, null);
        log.info("Unflagged solution comment {}", commentId);

        return new SolutionCommentOwnerPort.FlagResult(comment.getUserId(), previousIsFlagged, previousReason);
    }

    @Override
    public String resolveAuthorId(String commentId) {
        SolutionComment comment = solutionCommentMapper.selectById(commentId);
        return comment != null ? comment.getUserId() : null;
    }

    @Override
    public String resolveSolutionId(String commentId) {
        SolutionComment comment = solutionCommentMapper.selectById(commentId);
        return comment != null ? comment.getSolutionId() : null;
    }

    @Override
    @Transactional
    public SolutionCommentOwnerPort.DeleteResult deleteComment(String commentId, String deletedBy) {
        SolutionComment comment = solutionCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            return null;
        }

        boolean previousIsDeleted = Boolean.TRUE.equals(comment.getIsDeleted());

        // LambdaUpdateWrapper bypasses MyBatis-Plus FieldStrategy so the
        // soft-delete + audit stamps are persisted in a single statement
        // (mirrors the admin moderator's former update semantics).
        solutionCommentMapper.update(null, new LambdaUpdateWrapper<SolutionComment>()
                .eq(SolutionComment::getId, commentId)
                .set(SolutionComment::getIsDeleted, true)
                .set(SolutionComment::getDeletedAt, LocalDateTime.now())
                .set(SolutionComment::getDeletedBy, deletedBy));
        log.info("Deleted solution comment {} by {}", commentId, deletedBy);

        return new SolutionCommentOwnerPort.DeleteResult(comment.getUserId(), previousIsDeleted);
    }
}
