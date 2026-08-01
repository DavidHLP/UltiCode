package com.ulticode.modules.follow.event;

import com.ulticode.app.api.event.FollowDomainEvent;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("LegacyFollowEventConsumer — cross-process follow event consumer")
class LegacyFollowEventConsumerTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Mock
    private AchievementTriggerService achievementTriggerService;

    private Clock clock;
    private LegacyFollowEventConsumer consumer;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneId.of("UTC"));
        consumer = new LegacyFollowEventConsumer(notificationDispatcher, achievementTriggerService, clock);
    }

    @Test
    @DisplayName("ingestFollowEvent dispatches notification intent and triggers follower count achievement for follow event")
    void ingestFollowEvent_follow_dispatchesNotificationAndTriggersAchievement() {
        FollowDomainEvent event = new FollowDomainEvent("follower-1", "followerUser", "target-123", 5, 2, true);

        consumer.ingestFollowEvent(event);

        ArgumentCaptor<FollowReceivedIntent> intentCaptor = ArgumentCaptor.forClass(FollowReceivedIntent.class);
        verify(notificationDispatcher).dispatch(intentCaptor.capture());
        FollowReceivedIntent intent = intentCaptor.getValue();
        assertThat(intent.userId()).isEqualTo("target-123");
        assertThat(intent.followerUserId()).isEqualTo("follower-1");
        assertThat(intent.followerUsername()).isEqualTo("followerUser");

        verify(achievementTriggerService).trigger("follower-1", AchievementType.FOLLOWER_COUNT, 2);
        verify(achievementTriggerService).trigger("target-123", AchievementType.FOLLOWER_COUNT, 5);
    }

    @Test
    @DisplayName("ingestFollowEvent triggers achievement but skips notification for unfollow event")
    void ingestFollowEvent_unfollow_triggersAchievementOnly() {
        FollowDomainEvent event = new FollowDomainEvent("follower-1", "followerUser", "target-123", 4, 1, false);

        consumer.ingestFollowEvent(event);

        verify(achievementTriggerService).trigger("follower-1", AchievementType.FOLLOWER_COUNT, 1);
        verify(achievementTriggerService).trigger("target-123", AchievementType.FOLLOWER_COUNT, 4);
    }
}
