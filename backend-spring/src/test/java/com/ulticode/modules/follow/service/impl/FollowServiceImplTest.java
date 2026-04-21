package com.ulticode.modules.follow.service.impl;

import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private FollowServiceImpl followService;

    private User testUser;

    @BeforeEach
    void setUp() {
        followService = new FollowServiceImpl(
            followMapper,
            userMapper,
            achievementTriggerService,
            notificationService
        );

        testUser = new User();
        testUser.setId("user-target");
        testUser.setUsername("alice");
        testUser.setAvatar("https://example.com/avatar.png");
    }

    @Test
    @DisplayName("follow creates notification on first follow")
    void follow_firstFollow_createsNotification() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userMapper.selectById(targetUserId)).thenReturn(testUser);
        when(followMapper.exists(eq(currentUserId), eq(targetUserId))).thenReturn(false);
        when(followMapper.countByFollowingId(eq(targetUserId))).thenReturn(1);
        when(followMapper.countByFollowerId(eq(targetUserId))).thenReturn(1);
        when(followMapper.countByFollowingId(eq(currentUserId))).thenReturn(0);
        when(followMapper.countByFollowerId(eq(currentUserId))).thenReturn(0);

        FollowStatsDTO result = followService.follow(currentUserId, targetUserId);

        assertThat(result).isNotNull();
        verify(notificationService).createNotification(
            eq(targetUserId),
            eq("FOLLOW"),
            eq("social"),
            eq("alice followed you"),
            eq(""),
            eq("/profile/alice")
        );
    }

    @Test
    @DisplayName("follow does NOT create notification when already following")
    void follow_alreadyFollowing_doesNotCreateNotification() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userMapper.selectById(targetUserId)).thenReturn(testUser);
        when(followMapper.exists(eq(currentUserId), eq(targetUserId))).thenReturn(true);
        when(followMapper.countByFollowingId(eq(targetUserId))).thenReturn(1);
        when(followMapper.countByFollowerId(eq(targetUserId))).thenReturn(1);
        when(followMapper.countByFollowingId(eq(currentUserId))).thenReturn(0);
        when(followMapper.countByFollowerId(eq(currentUserId))).thenReturn(0);

        FollowStatsDTO result = followService.follow(currentUserId, targetUserId);

        assertThat(result).isNotNull();
        verify(notificationService, never()).createNotification(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    @DisplayName("follow succeeds even if notification creation throws")
    void follow_notificationThrows_doesNotBreakFollow() {
        String currentUserId = "user-current";
        String targetUserId = "user-target";

        when(userMapper.selectById(targetUserId)).thenReturn(testUser);
        when(followMapper.exists(eq(currentUserId), eq(targetUserId))).thenReturn(false);
        when(followMapper.countByFollowingId(eq(targetUserId))).thenReturn(1);
        when(followMapper.countByFollowerId(eq(targetUserId))).thenReturn(1);
        when(followMapper.countByFollowingId(eq(currentUserId))).thenReturn(0);
        when(followMapper.countByFollowerId(eq(currentUserId))).thenReturn(0);

        doThrow(new RuntimeException("Notification service unavailable"))
            .when(notificationService).createNotification(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
            );

        FollowStatsDTO result = followService.follow(currentUserId, targetUserId);

        assertThat(result).isNotNull();
        verify(notificationService).createNotification(
            eq(targetUserId),
            eq("FOLLOW"),
            eq("social"),
            eq("alice followed you"),
            eq(""),
            eq("/profile/alice")
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
