package com.ulticode.modules.achievement.listener;

import com.ulticode.modules.achievement.event.AchievementEarnedEvent;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.AchievementEarnedIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AchievementNotificationListenerTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private AchievementNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new AchievementNotificationListener(notificationDispatcher);
    }

    @Test
    @DisplayName("onAchievementEarned dispatches a typed AchievementEarnedIntent")
    void onAchievementEarned_dispatchesTypedIntent() {
        AchievementEarnedEvent event = AchievementEarnedEvent.of(
            "user-123", "ach-001", "badge-first-solve", "First Solve",
            "Solved your first problem", null, 3, 100
        );

        listener.onAchievementEarned(event);

        ArgumentCaptor<AchievementEarnedIntent> captor =
                ArgumentCaptor.forClass(AchievementEarnedIntent.class);
        verify(notificationDispatcher).dispatch(captor.capture());

        AchievementEarnedIntent intent = captor.getValue();
        assertThat(intent.userId()).isEqualTo("user-123");
        assertThat(intent.achievementKey()).isEqualTo("badge-first-solve");
    }

    @Test
    @DisplayName("onAchievementEarned swallows dispatcher failures (fire-and-forget)")
    void onAchievementEarned_swallowsDispatcherFailure() {
        AchievementEarnedEvent event = AchievementEarnedEvent.of(
            "user-456", "ach-002", "badge-daily", "Daily Streak",
            "7 day streak", null, 2, 50
        );
        doThrow(new RuntimeException("dispatcher unavailable"))
                .when(notificationDispatcher).dispatch(any(AchievementEarnedIntent.class));

        // Must not propagate — the listener is async and must never break the caller.
        listener.onAchievementEarned(event);

        verify(notificationDispatcher).dispatch(any(AchievementEarnedIntent.class));
    }
}
