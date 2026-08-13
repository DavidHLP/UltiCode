package com.ulticode.modules.follow.service.impl;

import com.ulticode.app.api.event.FollowEventPublisher;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.inspector.FollowInspector;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.follow.port.UserReadPort;
import com.ulticode.modules.follow.port.UserReadPort.UserSummaryData;
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
    private UserReadPort userReadPort;

    @Mock
    private FollowEventPublisher followEventPublisher;

    @Mock
    private FollowInspector followInspector;

    private FollowServiceImpl followService;

    private UserSummaryData testUser;
    private UserSummaryData currentUser;

    @BeforeEach
    void setUp() {
        followService = new FollowServiceImpl(followMapper, userReadPort, followEventPublisher, followInspector);
        testUser = new UserSummaryData("target-123", "targetUser", "http://avatar", "bio");
        currentUser = new UserSummaryData("user-1", "currentUser", "http://avatar1", "bio1");
    }

    private FollowStatsDTO stats(int followers, int following) {
        FollowStatsDTO dto = new FollowStatsDTO();
        dto.setFollowerCount(followers);
        dto.setFollowingCount(following);
        return dto;
    }

    @Test
    @DisplayName("follow dispatches full-context follow event on first follow")
    void follow_firstFollow_dispatchesEventWithContext() {
        when(userReadPort.findById("target-123")).thenReturn(testUser);
        when(userReadPort.findById("user-1")).thenReturn(currentUser);
        when(followMapper.exists("user-1", "target-123")).thenReturn(false);

        when(followInspector.getFollowStats("target-123")).thenReturn(stats(1, 0));
        when(followInspector.getFollowStats("user-1")).thenReturn(stats(0, 1));

        FollowStatsDTO result = followService.follow("user-1", "target-123");

        assertThat(result.getFollowerCount()).isEqualTo(1);
        verify(followMapper).insertIdempotent("user-1", "target-123");
        verify(followEventPublisher).publishFollowEvent("user-1", "currentUser", "target-123", 1, 1);
    }

    @Test
    @DisplayName("follow does NOT dispatch when already following")
    void follow_alreadyFollowing_doesNotDispatch() {
        when(userReadPort.findById("target-123")).thenReturn(testUser);
        when(followMapper.exists("user-1", "target-123")).thenReturn(true);
        when(followInspector.getFollowStats("target-123")).thenReturn(stats(1, 0));

        FollowStatsDTO result = followService.follow("user-1", "target-123");

        assertThat(result.getFollowerCount()).isEqualTo(1);
        verify(followMapper, never()).insertIdempotent(anyString(), anyString());
        verify(followEventPublisher, never()).publishFollowEvent(anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("follow fails if durable event publication fails")
    void follow_publisherThrows_abortsFollow() {
        when(userReadPort.findById("target-123")).thenReturn(testUser);
        when(userReadPort.findById("user-1")).thenReturn(currentUser);
        when(followMapper.exists("user-1", "target-123")).thenReturn(false);
        when(followInspector.getFollowStats("target-123")).thenReturn(stats(1, 0));
        when(followInspector.getFollowStats("user-1")).thenReturn(stats(0, 1));

        doThrow(new RuntimeException("Publisher error"))
                .when(followEventPublisher).publishFollowEvent(anyString(), anyString(), anyString(), anyInt(), anyInt());

        assertThatThrownBy(() -> followService.follow("user-1", "target-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Publisher error");
        verify(followMapper).insertIdempotent("user-1", "target-123");
    }

    @Test
    @DisplayName("follow throws when trying to follow self")
    void follow_selfFollow_throws() {
        assertThatThrownBy(() -> followService.follow("user-1", "user-1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("unfollow dispatches unfollow event and skips delete if already not following")
    void unfollow_alreadyNotFollowing_skipsDelete() {
        when(userReadPort.findById("target-123")).thenReturn(testUser);
        when(followMapper.deleteIfExists("user-1", "target-123")).thenReturn(0);
        when(followInspector.getFollowStats("target-123")).thenReturn(stats(0, 0));

        FollowStatsDTO result = followService.unfollow("user-1", "target-123");

        assertThat(result.getFollowerCount()).isEqualTo(0);
        verify(followEventPublisher, never()).publishUnfollowEvent(anyString(), anyString(), anyInt(), anyInt());
    }
}
