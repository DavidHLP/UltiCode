package com.ulticode.modules.follow.service.impl;

import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.inspector.FollowInspector;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.follow.port.UserReadPort;
import com.ulticode.modules.notification.intent.FollowReceivedIntent;
import com.ulticode.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Write-path tests for {@link FollowServiceImpl}.
 *
 * <p>The follow path now contributes only a typed {@link FollowReceivedIntent}
 * to the notification delivery module — the dispatcher owns preference
 * gating and channel fan-out — so these tests assert the intent dispatch
 * seam rather than a legacy envelope.
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock
    private FollowMapper followMapper;

    @Mock
    private UserReadPort userReadPort;

    @Mock
    private AchievementTriggerService achievementTriggerService;

    @Mock
    private com.ulticode.modules.notification.dispatcher.NotificationDispatcher notificationDispatcher;

    @Mock
    private FollowInspector followInspector;

    private FollowServiceImpl followService;

    private User testUser;

    @BeforeEach
    void setUp() {
        followService = new FollowServiceImpl(
            followMapper,
            userReadPort,
            achievementTriggerService,
            notificationDispatcher,
            followInspector
        );

        testUser = new User();
        testUser.setId("user-target");
        testUser.setUsername("alice");
        testUser.setAvatar("https://example.com/avatar.png");
    }

    private FollowStatsDTO stats(int followers, int following) {
        FollowStatsDTO s = new FollowStatsDTO();
        s.setFollowerCount(followers);
        s.setFollowingCount(following);
        return s;
    }

    @Test
    @DisplayName("follow dispatches a typed FollowReceivedIntent on first follow")
    void follow_firstFollow_dispatchesIntent() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userReadPort.findById(targetUserId)).thenReturn(testUser);
        when(userReadPort.findById(currentUserId)).thenReturn(testUser);
        when(followMapper.exists(eq(currentUserId), eq(targetUserId))).thenReturn(false);
        when(followInspector.getFollowStats(targetUserId)).thenReturn(stats(1, 1));

        FollowStatsDTO result = followService.follow(currentUserId, targetUserId);

        assertThat(result).isNotNull();
        verify(notificationDispatcher).dispatch(any(FollowReceivedIntent.class));
    }

    @Test
    @DisplayName("follow does NOT dispatch when already following")
    void follow_alreadyFollowing_doesNotDispatch() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userReadPort.findById(targetUserId)).thenReturn(testUser);
        when(followMapper.exists(eq(currentUserId), eq(targetUserId))).thenReturn(true);
        when(followInspector.getFollowStats(targetUserId)).thenReturn(stats(1, 1));

        followService.follow(currentUserId, targetUserId);

        verify(notificationDispatcher, never()).dispatch(any(FollowReceivedIntent.class));
    }

    @Test
    @DisplayName("follow succeeds even if the dispatcher throws")
    void follow_dispatchThrows_doesNotBreakFollow() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userReadPort.findById(targetUserId)).thenReturn(testUser);
        when(userReadPort.findById(currentUserId)).thenReturn(testUser);
        when(followMapper.exists(eq(currentUserId), eq(targetUserId))).thenReturn(false);
        when(followInspector.getFollowStats(targetUserId)).thenReturn(stats(1, 1));

        doThrow(new RuntimeException("Dispatcher unavailable"))
            .when(notificationDispatcher).dispatch(any(FollowReceivedIntent.class));

        FollowStatsDTO result = followService.follow(currentUserId, targetUserId);

        assertThat(result).isNotNull();
        verify(notificationDispatcher).dispatch(any(FollowReceivedIntent.class));
    }

    @Test
    @DisplayName("follow throws when trying to follow self")
    void follow_selfFollow_throws() {
        String userId = "user-same";

        assertThatThrownBy(() -> followService.follow(userId, userId))
            .hasMessageContaining("Cannot follow yourself");
    }

    @Test
    @DisplayName("unfollow 已未关注时不删除且只记录 debug")
    void unfollow_alreadyNotFollowing_skipsDelete() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userReadPort.findById(targetUserId)).thenReturn(testUser);
        when(followMapper.deleteIfExists(currentUserId, targetUserId)).thenReturn(0);
        when(followInspector.getFollowStats(targetUserId)).thenReturn(stats(0, 0));

        FollowStatsDTO result = followService.unfollow(currentUserId, targetUserId);

        assertThat(result).isNotNull();
    }
}
