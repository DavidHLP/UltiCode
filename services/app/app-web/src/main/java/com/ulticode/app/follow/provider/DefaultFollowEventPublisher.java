package com.ulticode.app.follow.provider;

import com.ulticode.app.api.event.FollowDomainEvent;
import com.ulticode.app.api.event.FollowEventPublisher;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Event publisher that dispatches follow domain events in-process in backend-app,
 * executing notification triggers directly without relying on backend-legacy RPC.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultFollowEventPublisher implements FollowEventPublisher {

    private final NotificationDispatcher notificationDispatcher;
    private final Clock clock;

    @Override
    public void publishFollowEvent(String followerId, String followerUsername, String targetUserId, int targetFollowerCount, int followerFollowingCount) {
        log.info("Follow event published: followerId={}, followerUsername={}, targetUserId={}", followerId, followerUsername, targetUserId);

        try {
            FollowReceivedIntent intent = FollowReceivedIntent.of(
                    followerId,
                    followerUsername,
                    targetUserId,
                    clock
            );
            if (notificationDispatcher != null) {
                notificationDispatcher.dispatch(intent);
                log.debug("In-process notification dispatched for target user {}", targetUserId);
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch in-process follow notification for user {}: {}", targetUserId, e.getMessage());
        }
    }

    @Override
    public void publishUnfollowEvent(String followerId, String targetUserId, int targetFollowerCount, int followerFollowingCount) {
        log.info("Unfollow event published: followerId={}, targetUserId={}", followerId, targetUserId);
    }
}
