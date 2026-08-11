package com.ulticode.app.api.service;

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
     * Soft-delete a solution comment, stamping the acting admin's user id.
     *
     * <p>Consumed by the Admin service's {@code SolutionCommentModerator}
     * (ADMIN-006) which previously reached for
     * {@code SolutionCommentMapper} directly.
     *
     * @param commentId target comment ID
     * @param deletedBy admin user ID performing the deletion
     * @return result wrapper with author id + pre-mutation deleted state, or
     *         {@code null} when the comment does not exist
     */
    DeleteResult deleteComment(String commentId, String deletedBy);

    /**
     * Result wrapper holding the author user ID and pre-mutation flag state.
     */
    record FlagResult(String authorUserId, boolean previousIsFlagged, String previousFlaggedReason) {}

    /**
     * Result wrapper holding the author user ID and pre-mutation deleted state.
     */
    record DeleteResult(String authorUserId, boolean previousIsDeleted) {}
}
