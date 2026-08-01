package com.ulticode.app.api.service;

import java.time.LocalDateTime;

/**
 * Write-side port for {@code forum_posts} moderation operations.
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
public interface ForumOwnerPort {

    /**
     * Flag a forum post for moderation review.
     *
     * @param postId    the post to flag
     * @param reason    reason for flagging (may be null)
     * @param flaggedAt timestamp to record (pass null to let the
     *                  implementation supply the current time)
     * @return flag result with author identity and prior state
     */
    FlagResult flagPost(String postId, String reason, LocalDateTime flaggedAt);

    /** Remove the flag from a previously flagged post. */
    FlagResult unflagPost(String postId);

    /** Set the pinned state of a post. */
    ToggleResult setPinned(String postId, boolean pinned);

    /** Set the locked state of a post. */
    ToggleResult setLocked(String postId, boolean locked);

    /**
     * Resolve the author ID of a post, or {@code null} if the post
     * does not exist or is soft-deleted.
     */
    String resolveAuthorId(String postId);

    // ─── Result records ───────────────────────────────────────────────────

    record FlagResult(String authorUserId, boolean previousIsFlagged, String previousReason) {}

    record ToggleResult(String authorId, boolean previousState) {}
}
