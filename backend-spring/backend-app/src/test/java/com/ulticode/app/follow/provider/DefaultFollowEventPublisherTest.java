package com.ulticode.app.follow.provider;

import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
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

    private NotificationDispatcher notificationDispatcher;
    private Clock clock;
    private DefaultFollowEventPublisher publisher;

    @BeforeEach
    void setUp() {
        notificationDispatcher = mock(NotificationDispatcher.class);
        clock = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneId.of("UTC"));
        publisher = new DefaultFollowEventPublisher(notificationDispatcher, clock);
    }

    @Test
    @DisplayName("publishFollowEvent dispatches FollowReceivedIntent to NotificationDispatcher")
    void publishFollowEventSuccess() {
        publisher.publishFollowEvent("user-follower", "follower_alice", "user-target", 10, 5);

        verify(notificationDispatcher).dispatch(any(FollowReceivedIntent.class));
    }

    @Test
    @DisplayName("publishUnfollowEvent executes cleanly without exception")
    void publishUnfollowEventSuccess() {
        publisher.publishUnfollowEvent("user-follower", "user-target", 9, 5);
    }
}
