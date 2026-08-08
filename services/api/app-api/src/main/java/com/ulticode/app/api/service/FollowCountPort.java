package com.ulticode.app.api.service;

/**
 * Provider-owned port that exposes the follower/following count for a user.
 */
public interface FollowCountPort {
    /**
     * Count how many followers a target user has.
     */
    long countFollowers(String userId);

    /**
     * Count how many users a given user is following.
     */
    long countFollowing(String userId);
}
