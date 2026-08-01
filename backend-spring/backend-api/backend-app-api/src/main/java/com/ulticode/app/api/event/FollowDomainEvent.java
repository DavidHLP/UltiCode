package com.ulticode.app.api.event;

/**
 * Domain event payload for follow / unfollow actions.
 */
public record FollowDomainEvent(
        String followerId,
        String followerUsername,
        String targetUserId,
        int targetFollowerCount,
        int followerFollowingCount,
        boolean isFollow
) {}
