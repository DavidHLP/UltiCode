package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Lightweight DTO for vote status queries.
 *
 * <p>Replaces the legacy {@code VoteResultVO} for cross-module consumption.
 *
 * @param targetId target entity ID
 * @param targetType target type string (e.g. "FORUM_POST", "FORUM_COMMENT")
 * @param userVote current user's vote direction: 0 = none, 1 = upvote, -1 = downvote
 * @param likes number of upvotes
 * @param dislikes number of downvotes
 * @param score derived like count minus dislike count
 */
public record VoteStatusDTO(
        String targetId,
        String targetType,
        int userVote,
        long likes,
        long dislikes
) implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Net score: likes minus dislikes. */
    public long score() {
        return likes - dislikes;
    }
}
