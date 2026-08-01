package com.ulticode.modules.forum.port;

import java.time.LocalDateTime;

/**
 * P3-OWNER-001-D: owner-only write surface for the {@code forum_posts} table
 * that lives in the forum module.
 *
 * @author ulticode
 */
public interface ForumOwnerPort {

    /**
     * Flag a post for moderation.
     *
     * @param postId target post ID
     * @param reason human-readable reason
     * @param flaggedAt wall-clock time of flagging
     * @return result wrapper containing author user ID and pre-mutation flag state for audit
     */
    FlagResult flagPost(String postId, String reason, LocalDateTime flaggedAt);

    /**
     * Remove the moderation flag from a post.
     *
     * @param postId target post ID
     * @return result wrapper containing author user ID and pre-mutation flag state for audit
     */
    FlagResult unflagPost(String postId);

    /**
     * Set the pinned status of a post.
     *
     * @param postId target post ID
     * @param pinned new pinned status
     * @return author user ID + previous boolean value wrapper
     */
    ToggleResult setPinned(String postId, boolean pinned);

    /**
     * Set the locked status of a post.
     *
     * @param postId target post ID
     * @param locked new locked status
     * @return author user ID + previous boolean value wrapper
     */
    ToggleResult setLocked(String postId, boolean locked);

    /**
     * Resolve the author of a post without mutating it.
     *
     * @param postId target post ID
     * @return author user ID, or {@code null} when the post does not exist
     */
    String resolveAuthorId(String postId);

    /**
     * Result wrapper holding the author user ID and pre-mutation flag state
     * (used by admin policies for audit recording).
     */
    record FlagResult(String authorUserId, boolean previousIsFlagged, String previousFlaggedReason) {}

    /**
     * Result wrapper holding the author user ID and previous boolean value
     * before a toggle update (used by admin policies for audit recording).
     */
    record ToggleResult(String authorUserId, boolean previousValue) {}
}
