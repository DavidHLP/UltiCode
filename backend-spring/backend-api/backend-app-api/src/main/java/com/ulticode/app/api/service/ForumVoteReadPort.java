package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.VoteStatusDTO;

/**
 * Read-side port for vote status queries owned by the App service.
 *
 * <p>Consumed by the forum module (post projections) that previously imported
 * {@code VoteService} directly. This port returns a DTO — never the internal
 * {@code VoteResultVO} entity.
 *
 * <p>P7-RELOCATE-FORUM-001: extracted when the forum family relocated
 * from backend-legacy to backend-app.
 */
public interface ForumVoteReadPort {

    /**
     * Get the current user's vote status for a target entity.
     *
     * @param userId     user ID
     * @param targetId   target entity ID
     * @param targetType target type string (e.g. "FORUM_POST", "FORUM_COMMENT")
     * @return vote status DTO
     */
    VoteStatusDTO getVoteStatus(String userId, String targetId, String targetType);
}
