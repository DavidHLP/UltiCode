package com.ulticode.modules.forum.port;

/**
 * Write-side port for {@code forum_comments} moderation operations.
 *
 * <p>Declared in the provider-owned API module so that
 * {@code backend-legacy} consumers (moderation, search) can depend on
 * the contract without importing any forum entity or mapper classes.
 *
 * <p>P7-RELOCATE-FORUM-001: promoted from the forum implementation
 * module to the shared API contract.
 *
 * @author ulticode
 */
public interface ForumCommentOwnerPort {

    /**
     * Flag a forum comment for moderation review.
     *
     * @param commentId the comment to flag
     * @param reason    reason for flagging (may be null)
     * @return flag result with author identity and prior state
     */
    FlagResult flagComment(String commentId, String reason);

    /** Remove the flag from a previously flagged comment. */
    FlagResult unflagComment(String commentId);

    /**
     * Resolve the author ID of a comment, or {@code null} if the comment
     * does not exist or is soft-deleted.
     */
    String resolveAuthorId(String commentId);

    // ─── Result record ────────────────────────────────────────────────────

    record FlagResult(String authorId, boolean previousWasFlagged, String previousReason) {}

    /**
     * Soft-delete a forum comment, stamping the acting admin's user id.
     *
     * <p>ADMIN-007: consumed by the Admin service's
     * {@code ForumCommentModerator} which previously reached for
     * {@code ForumCommentMapper} directly.
     *
     * @param commentId target comment ID
     * @param deletedBy admin user ID performing the deletion
     * @return result wrapper with author id + pre-mutation deleted state,
     *         or {@code null} when the comment does not exist
     */
    DeleteResult deleteComment(String commentId, String deletedBy);

    /**
     * Result wrapper holding the author user ID and pre-mutation deleted
     * state.
     */
    record DeleteResult(String authorUserId, boolean previousIsDeleted) {}
}
