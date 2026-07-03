package com.ulticode.modules.follow.inspector;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.dto.UserSummaryDTO;
import com.ulticode.modules.follow.entity.UserFollow;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.follow.mapper.FollowMapper.FollowCountDTO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultFollowInspector}, the new home for every
 * follow-graph read (paginated followers / following, aggregate stats,
 * per-viewer follow-status).
 *
 * <p>The read module is exercised here in isolation; the write-module
 * tests in {@code FollowServiceImplTest} only verify the write paths
 * (follow / unfollow) and stub the inspector with
 * {@code when(...).thenReturn(...)} when the post-mutation stats shape
 * needs to match.
 *
 * <p>Test surface: a single constructor call with two collaborators
 * ({@code FollowMapper}, {@code UserMapper}). No Spring context is
 * required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultFollowInspector — follow-graph read deep module")
class DefaultFollowInspectorTest {

    @Mock
    private FollowMapper followMapper;

    @Mock
    private UserMapper userMapper;

    private DefaultFollowInspector inspector;

    private static final String CURRENT = "user-current";
    private static final String TARGET = "user-target";

    @BeforeEach
    void setUp() {
        inspector = new DefaultFollowInspector(followMapper, userMapper);
    }

    private User user(String id, String name) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        u.setAvatar("https://example.com/" + id + ".png");
        u.setBio("bio of " + name);
        return u;
    }

    private UserFollow follow(String followerId, String followingId) {
        UserFollow uf = new UserFollow();
        uf.setFollowerId(followerId);
        uf.setFollowingId(followingId);
        return uf;
    }

    // ------------------------------------------------------------------
    // getFollowStats
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getFollowStats")
    class GetFollowStatsTests {

        @Test
        @DisplayName("returns follower + following counts from the mapper")
        void getFollowStats_returnsCounts() {
            when(followMapper.countByFollowingId(TARGET)).thenReturn(7);
            when(followMapper.countByFollowerId(TARGET)).thenReturn(3);

            FollowStatsDTO stats = inspector.getFollowStats(TARGET);

            assertThat(stats.getFollowerCount()).isEqualTo(7);
            assertThat(stats.getFollowingCount()).isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------
    // isFollowing
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("isFollowing")
    class IsFollowingTests {

        @Test
        @DisplayName("isFollowing 命中返回 true")
        void isFollowing_relationExists_returnsTrue() {
            when(userMapper.selectById(TARGET)).thenReturn(user(TARGET, "alice"));
            when(followMapper.exists(CURRENT, TARGET)).thenReturn(true);

            assertThat(inspector.isFollowing(CURRENT, TARGET)).isTrue();
            verify(followMapper).exists(CURRENT, TARGET);
        }

        @Test
        @DisplayName("isFollowing 未命中返回 false")
        void isFollowing_noRelation_returnsFalse() {
            when(userMapper.selectById(TARGET)).thenReturn(user(TARGET, "alice"));
            when(followMapper.exists(CURRENT, TARGET)).thenReturn(false);

            assertThat(inspector.isFollowing(CURRENT, TARGET)).isFalse();
            verify(followMapper).exists(CURRENT, TARGET);
        }

        @Test
        @DisplayName("isFollowing target 不存在抛 USER_NOT_FOUND")
        void isFollowing_targetNotFound_throws() {
            when(userMapper.selectById(TARGET)).thenReturn(null);

            assertThatThrownBy(() -> inspector.isFollowing(CURRENT, TARGET))
                .hasMessageContaining("User not found");
            verify(followMapper, never()).exists(anyString(), anyString());
        }

        @Test
        @DisplayName("isFollowing 自查抛 FORBIDDEN")
        void isFollowing_selfQuery_throws() {
            assertThatThrownBy(() -> inspector.isFollowing(CURRENT, CURRENT))
                .hasMessageContaining("Cannot query follow status of yourself");
            verify(followMapper, never()).exists(anyString(), anyString());
        }
    }

    // ------------------------------------------------------------------
    // getFollowers
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getFollowers")
    class GetFollowersTests {

        @Test
        @DisplayName("empty page returns empty items with the total")
        void getFollowers_empty_returnsEmptyItems() {
            when(followMapper.selectByFollowingIdPaged(eq(TARGET), eq(0L), eq(20L))).thenReturn(List.of());
            when(followMapper.countByFollowingId(TARGET)).thenReturn(0);

            PageResult<UserSummaryDTO> result = inspector.getFollowers(TARGET, 1, 20);

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getTotal()).isZero();
            verify(userMapper, never()).selectBatchIds(any());
        }

        @Test
        @DisplayName("populated page returns enriched summaries with batch counts")
        void getFollowers_populated_returnsEnrichedSummaries() {
            UserFollow uf = follow("follower-1", TARGET);
            when(followMapper.selectByFollowingIdPaged(eq(TARGET), eq(0L), eq(20L))).thenReturn(List.of(uf));
            when(followMapper.countByFollowingId(TARGET)).thenReturn(1);
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user("follower-1", "bob")));
            when(followMapper.batchFollowCounts(anyList()))
                .thenReturn(List.of(new FollowCountDTO("follower-1", 5, 0)));
            when(followMapper.batchFollowingCounts(anyList()))
                .thenReturn(List.of(new FollowCountDTO("follower-1", 0, 3)));

            PageResult<UserSummaryDTO> result = inspector.getFollowers(TARGET, 1, 20);

            assertThat(result.getItems()).hasSize(1);
            UserSummaryDTO dto = result.getItems().get(0);
            assertThat(dto.getUsername()).isEqualTo("bob");
            assertThat(dto.getFollowerCount()).isEqualTo(5);
            assertThat(dto.getFollowingCount()).isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------
    // getFollowing
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getFollowing")
    class GetFollowingTests {

        @Test
        @DisplayName("empty page returns empty items with the total")
        void getFollowing_empty_returnsEmptyItems() {
            when(followMapper.selectByFollowerIdPaged(eq(CURRENT), eq(0L), eq(20L))).thenReturn(List.of());
            when(followMapper.countByFollowerId(CURRENT)).thenReturn(0);

            PageResult<UserSummaryDTO> result = inspector.getFollowing(CURRENT, 1, 20);

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getTotal()).isZero();
            verify(userMapper, never()).selectBatchIds(any());
        }

        @Test
        @DisplayName("populated page returns enriched summaries with batch counts")
        void getFollowing_populated_returnsEnrichedSummaries() {
            UserFollow uf = follow(CURRENT, "followee-1");
            when(followMapper.selectByFollowerIdPaged(eq(CURRENT), eq(0L), eq(20L))).thenReturn(List.of(uf));
            when(followMapper.countByFollowerId(CURRENT)).thenReturn(1);
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(user("followee-1", "carol")));
            when(followMapper.batchFollowCounts(anyList()))
                .thenReturn(List.of(new FollowCountDTO("followee-1", 8, 0)));
            when(followMapper.batchFollowingCounts(anyList()))
                .thenReturn(List.of(new FollowCountDTO("followee-1", 0, 2)));

            PageResult<UserSummaryDTO> result = inspector.getFollowing(CURRENT, 1, 20);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getUsername()).isEqualTo("carol");
            assertThat(result.getItems().get(0).getFollowerCount()).isEqualTo(8);
            assertThat(result.getItems().get(0).getFollowingCount()).isEqualTo(2);
        }
    }
}
