package com.ulticode.modules.solution.port;

import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link SolutionCommentOwnerPort}.
 *
 * <p><b>Behavioral parity:</b> flag/unflag delegate to
 * {@code SolutionCommentMapper.updateFlagStatus} — the same atomic
 * {@code @Update} SQL the moderation adapter previously called directly.
 * The mapper SQL uses {@code NOW()} for the timestamp, so no caller-supplied
 * time is needed. Pre-mutation state is captured via
 * {@code selectByIdIgnoreDeleted} so {@link FlagResult} is populated even
 * for soft-deleted comments. Author/parent resolves use {@code selectById}
 * to match the original adapter's read behavior.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSolutionCommentOwnerPort implements SolutionCommentOwnerPort {

    private final SolutionCommentMapper solutionCommentMapper;

    @Override
    @Transactional
    public FlagResult flagComment(String commentId, String reason) {
        SolutionComment comment = solutionCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            return null;
        }

        boolean previousIsFlagged = Boolean.TRUE.equals(comment.getIsFlagged());
        String previousReason = comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "";

        solutionCommentMapper.updateFlagStatus(commentId, true, reason != null ? reason : "");
        log.info("Flagged solution comment {}", commentId);

        return new FlagResult(comment.getUserId(), previousIsFlagged, previousReason);
    }

    @Override
    @Transactional
    public FlagResult unflagComment(String commentId) {
        SolutionComment comment = solutionCommentMapper.selectByIdIgnoreDeleted(commentId);
        if (comment == null) {
            return null;
        }

        boolean previousIsFlagged = Boolean.TRUE.equals(comment.getIsFlagged());
        String previousReason = comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "";

        solutionCommentMapper.updateFlagStatus(commentId, false, null);
        log.info("Unflagged solution comment {}", commentId);

        return new FlagResult(comment.getUserId(), previousIsFlagged, previousReason);
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
}
