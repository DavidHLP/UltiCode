package com.ulticode.modules.reconciliation.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link DefaultAppReconciliationReadPort} — the
 * App-side owner facts for the reconciliation aggregator.
 */
@ExtendWith(MockitoExtension.class)
class DefaultAppReconciliationReadPortTest {

    @Mock
    private AppReconciliationReadMapper mapper;

    @InjectMocks
    private DefaultAppReconciliationReadPort port;

    @Test
    @DisplayName("countUserProfiles delegates to mapper")
    void countUserProfilesDelegates() {
        when(mapper.countUserProfiles()).thenReturn(7L);
        assertThat(port.countUserProfiles()).isEqualTo(7L);
    }

    @Test
    @DisplayName("countOrphans maps all nine mapper counts into the record in order")
    void countOrphansMapsAllNine() {
        when(mapper.countOrphanSubmissions()).thenReturn(1L);
        when(mapper.countOrphanSolutions()).thenReturn(2L);
        when(mapper.countOrphanForumPosts()).thenReturn(3L);
        when(mapper.countOrphanNotifications()).thenReturn(4L);
        when(mapper.countOrphanUserProfiles()).thenReturn(5L);
        when(mapper.countOrphanContestParticipants()).thenReturn(6L);
        when(mapper.countOrphanUserAchievements()).thenReturn(7L);
        when(mapper.countOrphanUserFollowsByFollower()).thenReturn(8L);
        when(mapper.countOrphanUserFollowsByFollowing()).thenReturn(9L);

        ReconciliationOrphanCounts counts = port.countOrphans();

        assertThat(counts.submissions()).isEqualTo(1L);
        assertThat(counts.solutions()).isEqualTo(2L);
        assertThat(counts.forumPosts()).isEqualTo(3L);
        assertThat(counts.notifications()).isEqualTo(4L);
        assertThat(counts.userProfiles()).isEqualTo(5L);
        assertThat(counts.contestParticipants()).isEqualTo(6L);
        assertThat(counts.userAchievements()).isEqualTo(7L);
        assertThat(counts.userFollowsByFollower()).isEqualTo(8L);
        assertThat(counts.userFollowsByFollowing()).isEqualTo(9L);

        verify(mapper).countOrphanSubmissions();
        verify(mapper).countOrphanSolutions();
        verify(mapper).countOrphanForumPosts();
        verify(mapper).countOrphanNotifications();
        verify(mapper).countOrphanUserProfiles();
        verify(mapper).countOrphanContestParticipants();
        verify(mapper).countOrphanUserAchievements();
        verify(mapper).countOrphanUserFollowsByFollower();
        verify(mapper).countOrphanUserFollowsByFollowing();
    }
}
