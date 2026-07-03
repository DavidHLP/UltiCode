package com.ulticode.modules.follow.service.impl;

import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.inspector.FollowInspector;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Write-path tests for {@link FollowServiceImpl}.
 *
 * <p>The read paths (followers / following / stats / isFollowing) live
 * on {@link FollowInspector} and are exercised in
 * {@code DefaultFollowInspectorTest}; here the inspector is stubbed
 * with {@code when(...).thenReturn(...)} when the post-mutation stats
 * need a shape, mirroring how {@code EdgeOperationsServiceTest} treats
 * the {@code EdgeOperationInspector} seam — the write path is exercised
 * without dragging the read mapper's count logic back into this test.
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock
    private FollowMapper followMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AchievementTriggerService achievementTriggerService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private com.ulticode.modules.notification.dispatcher.NotificationDispatcher notificationDispatcher;

    @Mock
    private com.ulticode.common.config.FeatureFlagsProperties featureFlags;

    @Mock
    private FollowInspector followInspector;

    private FollowServiceImpl followService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // ADR-004 M4c: legacy flag-off path is the default; tests assert
        // the legacy dispatch wiring. lenient() because tests that don't
        // exercise the dispatch path (e.g. follow_selfFollow) would
        // otherwise trip Mockito 5's UnnecessaryStubbingException.
        lenient().when(featureFlags.isUseNotificationIntent()).thenReturn(false);
        followService = new FollowServiceImpl(
            followMapper,
            userMapper,
            achievementTriggerService,
            notificationService,
            notificationDispatchService,
            notificationDispatcher,
            featureFlags,
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
    @DisplayName("follow dispatches notification on first follow (Q20 wiring)")
    void follow_firstFollow_dispatchesNotification() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userMapper.selectById(targetUserId)).thenReturn(testUser);
        when(userMapper.selectById(currentUserId)).thenReturn(testUser);
        when(followMapper.exists(eq(currentUserId), eq(targetUserId))).thenReturn(false);
        when(followInspector.getFollowStats(targetUserId)).thenReturn(stats(1, 1));
        when(notificationDispatchService.dispatch(
                eq(targetUserId), eq("FOLLOW"), eq("COMMUNICATION"),
                anyString(), anyString(), anyString(), any(), eq(false)))
            .thenReturn(Optional.empty());

        FollowStatsDTO result = followService.follow(currentUserId, targetUserId);

        assertThat(result).isNotNull();
        verify(notificationDispatchService).dispatch(
            eq(targetUserId),
            eq("FOLLOW"),
            eq("COMMUNICATION"),
            eq("alice followed you"),
            eq(""),
            eq("/profile/alice"),
            isNull(),
            eq(false)
        );
        // The legacy createNotification path is no longer called.
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("follow does NOT dispatch when already following")
    void follow_alreadyFollowing_doesNotDispatch() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userMapper.selectById(targetUserId)).thenReturn(testUser);
        when(followMapper.exists(eq(currentUserId), eq(targetUserId))).thenReturn(true);
        when(followInspector.getFollowStats(targetUserId)).thenReturn(stats(1, 1));

        followService.follow(currentUserId, targetUserId);

        verify(notificationDispatchService, never()).dispatch(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyBoolean()
        );
    }

    @Test
    @DisplayName("follow succeeds even if dispatch throws")
    void follow_dispatchThrows_doesNotBreakFollow() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userMapper.selectById(targetUserId)).thenReturn(testUser);
        when(userMapper.selectById(currentUserId)).thenReturn(testUser);
        when(followMapper.exists(eq(currentUserId), eq(targetUserId))).thenReturn(false);
        when(followInspector.getFollowStats(targetUserId)).thenReturn(stats(1, 1));

        doThrow(new RuntimeException("Dispatch service unavailable"))
            .when(notificationDispatchService).dispatch(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyBoolean()
            );

        FollowStatsDTO result = followService.follow(currentUserId, targetUserId);

        assertThat(result).isNotNull();
        verify(notificationDispatchService).dispatch(
            eq(targetUserId),
            eq("FOLLOW"),
            eq("COMMUNICATION"),
            anyString(), anyString(), anyString(), any(), eq(false)
        );
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

        when(userMapper.selectById(targetUserId)).thenReturn(testUser);
        when(followMapper.deleteIfExists(currentUserId, targetUserId)).thenReturn(0);
        when(followInspector.getFollowStats(targetUserId)).thenReturn(stats(0, 0));

        FollowStatsDTO result = followService.unfollow(currentUserId, targetUserId);

        assertThat(result).isNotNull();
    }
}
