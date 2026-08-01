package com.ulticode.modules.solution.port;

/**
 * P7-MODERATION-CUTOVER-001: owner-only write + author-resolve surface for
 * {@code solution_comments} that lives in the solution module.
 *
 * <p>Extracted so that {@code DefaultContentModerationAdapter} and
 * {@code DefaultModerationProjection} can flag/unflag solution comments
 * and resolve their authors/parents without importing
 * {@code SolutionCommentMapper} directly.
 *
 * @author ulticode
 */
public interface SolutionCommentOwnerPort {

    /**
     * Flag a solution comment for moderation.
     *
     * @param commentId target comment ID
     * @param reason human-readable reason
     * @return result wrapper containing author user ID and pre-mutation flag state
     */
    FlagResult flagComment(String commentId, String reason);

    /**
     * Remove the moderation flag from a solution comment.
     *
     * @param commentId target comment ID
     * @return result wrapper containing author user ID and pre-mutation flag state
     */
    FlagResult unflagComment(String commentId);

    /**
     * Resolve the author of a solution comment without mutating it.
     *
     * @param commentId target comment ID
     * @return author user ID, or {@code null} when the comment does not exist
     */
    String resolveAuthorId(String commentId);

    /**
     * Resolve the parent solution ID of a comment without mutating it.
     *
     * @param commentId target comment ID
     * @return solution ID, or {@code null} when the comment does not exist
     */
    String resolveSolutionId(String commentId);

    /**
     * Result wrapper holding the author user ID and pre-mutation flag state.
     */
    record FlagResult(String authorUserId, boolean previousIsFlagged, String previousFlaggedReason) {}
}
