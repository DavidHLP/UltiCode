package com.ulticode.app.follow.provider;

import com.ulticode.modules.follow.port.FollowEventPublisher;
import com.ulticode.modules.notification.event.NotificationIntentEventPublisher;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Event publisher that records follow notification intents in App's durable
 * integration outbox. The follow write transaction supplies the atomic
 * boundary; delivery happens later through the notification inbox worker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultFollowEventPublisher implements FollowEventPublisher {

    private final NotificationIntentEventPublisher notificationIntentEventPublisher;
    private final Clock clock;

    @Override
    public void publishFollowEvent(String followerId, String followerUsername, String targetUserId, int targetFollowerCount, int followerFollowingCount) {
        log.info("Follow event published: followerId={}, followerUsername={}, targetUserId={}", followerId, followerUsername, targetUserId);

        FollowReceivedIntent intent = FollowReceivedIntent.of(
                followerId,
                followerUsername,
                targetUserId,
                clock
        );
        notificationIntentEventPublisher.publish(intent);
        log.debug("Durable notification intent recorded for target user {}", targetUserId);
    }

    @Override
    public void publishUnfollowEvent(String followerId, String targetUserId, int targetFollowerCount, int followerFollowingCount) {
        log.info("Unfollow event published: followerId={}, targetUserId={}", followerId, targetUserId);
    }
}
