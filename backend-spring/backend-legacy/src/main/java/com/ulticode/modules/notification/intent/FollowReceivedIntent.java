package com.ulticode.modules.notification.intent;

import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

/**
 * Intent emitted when {@code currentUserId} follows {@code targetUserId}.
 *
 * <p>Idempotency follows the D-10 "first follow per day" rule: the
 * {@link #intentId()} includes the follow day, so the first follow on a
 * given day is delivered and any same-day unfollow/refollow collapses
 * (already-delivered). A refollow on a later day produces a distinct id
 * and is delivered again — the intended business behavior. The day comes
 * from the producer-supplied {@link Clock} so it is deterministic in tests.
 *
 * <p>Reference: ADR-004 §2.1; D-10 first-follow idempotency (legacy).
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
        // D-10 "first follow per day": the day component makes a cross-day
        // unfollow/refollow produce a distinct id so the ledger does not
        // silently drop the later follow as already-delivered. A same-day
        // refollow still collapses, matching the documented business intent.
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
     * Build from the actor {@link User}, the target user id, and the
     * {@link Clock} that determines the idempotency day (D-10). The clock is
     * required so {@link #intentId()} is deterministic in tests.
     */
    public static FollowReceivedIntent of(User follower, String targetUserId, Clock clock) {
        return new FollowReceivedIntent(
                targetUserId,
                follower.getId(),
                follower.getUsername(),
                LocalDate.now(clock),
                NotificationCategory.COMMUNICATION
        );
    }
}
