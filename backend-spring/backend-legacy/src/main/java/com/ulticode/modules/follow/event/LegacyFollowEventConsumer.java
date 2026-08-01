package com.ulticode.modules.follow.event;

import com.ulticode.app.api.event.FollowDomainEvent;
import com.ulticode.app.api.event.FollowEventIngestionPort;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Dubbo Provider running in backend-legacy that ingests cross-process
 * FollowDomainEvent from backend-app and triggers legacy notifications & achievements.
 */
@Slf4j
@Component
@DubboService(group = "backend-legacy", version = "1.0.0")
@RequiredArgsConstructor
public class LegacyFollowEventConsumer implements FollowEventIngestionPort {

    private final NotificationDispatcher notificationDispatcher;
    private final AchievementTriggerService achievementTriggerService;
    private final Clock clock;

    @Override
    public void ingestFollowEvent(FollowDomainEvent event) {
        if (event == null) {
            return;
        }
        log.info("Received cross-process follow event: follower={}, target={}, isFollow={}",
                event.followerId(), event.targetUserId(), event.isFollow());

        if (event.isFollow()) {
            dispatchNotification(event);
        }
        triggerAchievements(event);
    }

    private void dispatchNotification(FollowDomainEvent event) {
        try {
            FollowReceivedIntent intent = FollowReceivedIntent.of(
                    event.followerId(),
                    event.followerUsername(),
                    event.targetUserId(),
                    clock
            );
            notificationDispatcher.dispatch(intent);
            log.debug("Legacy notification dispatched for target user {}", event.targetUserId());
        } catch (Exception e) {
            log.warn("Failed to dispatch legacy follow notification for user {}: {}", event.targetUserId(), e.getMessage());
        }
    }

    private void triggerAchievements(FollowDomainEvent event) {
        try {
            achievementTriggerService.trigger(event.followerId(), AchievementType.FOLLOWER_COUNT, event.followerFollowingCount());
            achievementTriggerService.trigger(event.targetUserId(), AchievementType.FOLLOWER_COUNT, event.targetFollowerCount());
        } catch (Exception e) {
            log.warn("Failed to trigger legacy follow achievements: {}", e.getMessage());
        }
    }
}
