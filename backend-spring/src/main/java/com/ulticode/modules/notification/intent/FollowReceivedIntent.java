package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;

import java.util.Map;

/**
 * Intent emitted when {@code currentUserId} follows {@code targetUserId}.
 *
 * <p>Legacy dispatch in {@code FollowServiceImpl} used
 * {@code targetUserId + ":follow:" + currentUserId + ":" + startOfDay()} as a
 * dedup key (per D-10 "first follow per day"). This intent uses
 * {@code currentUserId} as part of the natural key directly: a user that
 * unfollows and re-follows the same target in the same day still produces
 * the same intent id, which is what the existing business semantics want.
 * If the team later wants "only the first follow ever", tighten the id
 * derivation to drop the time component and add a separate counter.
 *
 * <p>Reference: ADR-004 §2.1; D-10 first-follow idempotency (legacy).
 */
public record FollowReceivedIntent(
        String userId,
        String followerUserId,
        String followerUsername,
        NotificationCategory category
) implements NotificationIntent {

    @Override
    public String intentId() {
        return "follow:" + userId + ":" + followerUserId;
    }

    @Override
    public NotificationPayload toPushPayload() {
        return NotificationPayload.of(
                intentId(),
                "FOLLOW",
                followerUsername + " followed you",
                "",
                Map.of(
                        "followerUserId", followerUserId,
                        "followerUsername", followerUsername));
    }

    /**
     * Build from the actor {@link User} and the target user id.
     */
    public static FollowReceivedIntent of(User follower, String targetUserId) {
        return new FollowReceivedIntent(
                targetUserId,
                follower.getId(),
                follower.getUsername(),
                NotificationCategory.COMMUNICATION
        );
    }
}
