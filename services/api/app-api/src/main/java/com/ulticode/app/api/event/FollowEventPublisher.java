package com.ulticode.app.api.event;

/**
 * Decoupled event publisher port for follow/unfollow domain events.
 */
public interface FollowEventPublisher {
    /**
     * Publish a follow event with complete context for notifications and achievements.
     *
     * @param followerId              the follower's user ID
     * @param followerUsername        the follower's username
     * @param targetUserId            the target user's ID receiving the follow
     * @param targetFollowerCount     the target user's updated follower count
     * @param followerFollowingCount  the follower's updated following count
     */
    void publishFollowEvent(String followerId, String followerUsername, String targetUserId, int targetFollowerCount, int followerFollowingCount);

    /**
     * Publish an unfollow event with complete context.
     *
     * @param followerId              the follower's user ID
     * @param targetUserId            the target user's ID
     * @param targetFollowerCount     the target user's updated follower count
     * @param followerFollowingCount  the follower's updated following count
     */
    void publishUnfollowEvent(String followerId, String targetUserId, int targetFollowerCount, int followerFollowingCount);
}
