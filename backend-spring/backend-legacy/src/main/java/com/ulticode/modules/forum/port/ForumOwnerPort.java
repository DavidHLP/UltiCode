package com.ulticode.modules.forum.port;

import java.time.LocalDateTime;

/**
 * P3-OWNER-001-D: owner-only write surface for the {@code forum_posts} table
 * that lives in the forum module.
 *
 * <p>Before this port, admin moderation policies ({@code ForumFlagPolicyImpl}
 * and {@code ForumPostFieldToggleImpl}) reached directly into
 * {@link com.ulticode.modules.forum.mapper.ForumPostMapper}.
 * The Admin module's P3-OWNER-001-D boundary forbids foreign-mapper WRITE
 * methods (ArchUnit rule P3-OWNER-001-F), so every admin caller of these writes
 * must go through this port.
 *
 * @author ulticode
 */
public interface ForumOwnerPort {

    /**
     * Flag a post for moderation. Sets {@code is_flagged = true},
     * {@code flagged_reason = reason}, and {@code flagged_at = flaggedAt}.
     *
     * @param postId target post ID
     * @param reason human-readable reason
     * @param flaggedAt wall-clock time of flagging
     * @return author user ID of the post
     */
    String flagPost(String postId, String reason, LocalDateTime flaggedAt);

    /**
     * Remove the moderation flag from a post.
     *
     * @param postId target post ID
     * @return author user ID of the post
     */
    String unflagPost(String postId);

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
     * Result wrapper holding the author user ID and previous boolean value
     * before a toggle update (used by admin policies for audit recording).
     */
    record ToggleResult(String authorUserId, boolean previousValue) {}
}
