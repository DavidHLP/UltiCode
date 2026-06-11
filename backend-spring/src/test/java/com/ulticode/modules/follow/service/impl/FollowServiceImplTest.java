package com.ulticode.modules.follow.service.impl;

import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    private FollowServiceImpl followService;

    private User testUser;

    @BeforeEach
    void setUp() {
        followService = new FollowServiceImpl(
            followMapper,
            userMapper,
            achievementTriggerService,
            notificationService,
            notificationDispatchService
        );

        testUser = new User();
        testUser.setId("user-target");
        testUser.setUsername("alice");
        testUser.setAvatar("https://example.com/avatar.png");
    }

    @Test
    @DisplayName("follow dispatches notification on first follow (Q20 wiring)")
    void follow_firstFollow_dispatchesNotification() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userMapper.selectById(targetUserId)).thenReturn(testUser);
        when(userMapper.selectById(currentUserId)).thenReturn(testUser);
        when(followMapper.exists(eq(currentUserId), eq(targetUserId))).thenReturn(false);
        when(followMapper.countByFollowingId(eq(targetUserId))).thenReturn(1);
        when(followMapper.countByFollowerId(eq(targetUserId))).thenReturn(1);
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
        when(followMapper.countByFollowingId(eq(targetUserId))).thenReturn(1);
        when(followMapper.countByFollowerId(eq(targetUserId))).thenReturn(1);

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
        when(followMapper.countByFollowingId(eq(targetUserId))).thenReturn(1);
        when(followMapper.countByFollowerId(eq(targetUserId))).thenReturn(1);

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
}
