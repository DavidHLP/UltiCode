package com.ulticode.modules.follow.service;

import com.ulticode.modules.follow.dto.FollowStatsDTO;

/**
 * Write-path service for the follow graph.
 *
 * <p>Holds the two mutations — follow and unfollow — and nothing else.
 * Every read (paginated follower / following lists, aggregate counts,
 * per-viewer follow-status) lives on the matching deep module
 * {@link com.ulticode.modules.follow.inspector.FollowInspector}; this
 * service injects the inspector to fetch the post-mutation counts it
 * returns, so the read logic has a single owner.
 *
 * @see com.ulticode.modules.follow.inspector.FollowInspector
 *      the read-side deep module that owns every follow-graph read
 */
public interface FollowService {

    /**
     * Follow a user (idempotent).
     *
     * @param currentUserId the current authenticated user
     * @param targetUserId the user to follow
     * @return updated follow stats
     */
    FollowStatsDTO follow(String currentUserId, String targetUserId);

    /**
     * Unfollow a user.
     *
     * @param currentUserId the current authenticated user
     * @param targetUserId the user to unfollow
     * @return updated follow stats
     */
    FollowStatsDTO unfollow(String currentUserId, String targetUserId);
}
