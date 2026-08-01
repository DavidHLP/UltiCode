package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

/**
 * Intent emitted when {@code currentUserId} follows {@code targetUserId}.
 */
public record FollowReceivedIntent(
        String userId,
        String followerUserId,
        String followerUsername,
        LocalDate followDay,
        NotificationCategory category
) implements NotificationIntent {

    @Override
    public String intentId() {
        return "follow:" + userId + ":" + followerUserId + ":" + followDay;
    }

    @Override
    public String wireType() {
        return "FOLLOW";
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
     * Build from primitive parameters without needing a User entity instance.
     */
    public static FollowReceivedIntent of(String followerId, String followerUsername, String targetUserId, Clock clock) {
        return new FollowReceivedIntent(
                targetUserId,
                followerId,
                followerUsername != null ? followerUsername : followerId,
                LocalDate.now(clock != null ? clock : Clock.systemUTC()),
                NotificationCategory.COMMUNICATION
        );
    }

    /**
     * Build from the actor {@link User}, the target user id, and the {@link Clock}.
     */
    public static FollowReceivedIntent of(User follower, String targetUserId, Clock clock) {
        return of(follower.getId(), follower.getUsername(), targetUserId, clock);
    }
}
