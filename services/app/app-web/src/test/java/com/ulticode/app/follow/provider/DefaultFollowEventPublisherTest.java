package com.ulticode.app.follow.provider;

import com.ulticode.modules.notification.event.NotificationIntentEventPublisher;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultFollowEventPublisherTest {

    private NotificationIntentEventPublisher notificationIntentEventPublisher;
    private Clock clock;
    private DefaultFollowEventPublisher publisher;

    @BeforeEach
    void setUp() {
        notificationIntentEventPublisher = mock(NotificationIntentEventPublisher.class);
        clock = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("UTC"));
        publisher = new DefaultFollowEventPublisher(notificationIntentEventPublisher, clock);
    }

    @Test
    @DisplayName("publishFollowEvent records a durable FollowReceivedIntent")
    void publishFollowEventSuccess() {
        publisher.publishFollowEvent("user-follower", "follower_alice", "user-target", 10, 5);

        verify(notificationIntentEventPublisher).publish(any(FollowReceivedIntent.class));
    }

    @Test
    @DisplayName("publishUnfollowEvent executes cleanly without exception")
    void publishUnfollowEventSuccess() {
        publisher.publishUnfollowEvent("user-follower", "user-target", 9, 5);
    }
}
