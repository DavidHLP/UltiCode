package com.ulticode.modules.admin.policy;

/**
 * Write policy that flips the moderation flag on a forum post.
 *
 * <p>Lives separately from {@link ForumPostFieldToggle} because flagging
 * touches three fields ({@code is_flagged}, {@code flagged_reason},
 * {@code flagged_at}) and carries an extra {@code reason} parameter plus a
 * {@link java.time.Clock} dependency. Folding it into the single-field
 * policy would force the enum to branch on which fields to snapshot,
 * defeating the depth gain.
 *
 * @author ulticode
 */
public interface ForumFlagPolicy {

    /**
     * Flag the post for moderation.
     *
     * @param postId target post id
     * @param reason human-readable reason (may be {@code null}, stored as empty string)
     */
    void flag(String postId, String reason);

    /**
     * Remove the moderation flag from the post.
     *
     * @param postId target post id
     */
    void unflag(String postId);
}