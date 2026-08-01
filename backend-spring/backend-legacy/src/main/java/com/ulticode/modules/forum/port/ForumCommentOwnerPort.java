package com.ulticode.modules.forum.port;

/**
 * P7-MODERATION-CUTOVER-001: owner-only write + author-resolve surface for
 * {@code forum_comments} that lives in the forum module.
 *
 * <p>Extracted so that {@code DefaultContentModerationAdapter} can flag/unflag
 * forum comments and resolve their authors without importing
 * {@code ForumCommentMapper} directly.
 *
 * @author ulticode
 */
public interface ForumCommentOwnerPort {

    /**
     * Flag a comment for moderation.
     *
     * @param commentId target comment ID
     * @param reason human-readable reason
     * @return result wrapper containing author user ID and pre-mutation flag state
     */
    FlagResult flagComment(String commentId, String reason);

    /**
     * Remove the moderation flag from a comment.
     *
     * @param commentId target comment ID
     * @return result wrapper containing author user ID and pre-mutation flag state
     */
    FlagResult unflagComment(String commentId);

    /**
     * Resolve the author of a comment without mutating it.
     *
     * @param commentId target comment ID
     * @return author user ID, or {@code null} when the comment does not exist
     */
    String resolveAuthorId(String commentId);

    /**
     * Result wrapper holding the author user ID and pre-mutation flag state.
     */
    record FlagResult(String authorUserId, boolean previousIsFlagged, String previousFlaggedReason) {}
}
