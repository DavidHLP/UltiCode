package com.ulticode.modules.follow.inspector;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.dto.UserSummaryDTO;
import com.ulticode.modules.follow.entity.UserFollow;
import com.ulticode.modules.follow.mapper.FollowMapper;
import com.ulticode.modules.follow.mapper.FollowMapper.FollowCountDTO;
import com.ulticode.modules.follow.port.UserReadPort;
import com.ulticode.modules.follow.port.UserReadPort.UserSummaryData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultFollowInspector — follow-graph read deep module")
class DefaultFollowInspectorTest {

    @Mock
    private FollowMapper followMapper;

    @Mock
    private UserReadPort userReadPort;

    private DefaultFollowInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new DefaultFollowInspector(followMapper, userReadPort);
    }

    @Test
    @DisplayName("getFollowers maps user summaries and batch count enrichment")
    void getFollowers_success() {
        UserFollow uf = new UserFollow();
        uf.setFollowerId("follower-1");
        uf.setFollowingId("user-1");

        when(followMapper.selectByFollowingIdPaged("user-1", 0, 20)).thenReturn(List.of(uf));
        when(followMapper.countByFollowingId("user-1")).thenReturn(1);

        UserSummaryData summaryData = new UserSummaryData("follower-1", "followerUser", "http://avatar", "bio text");
        when(userReadPort.findByIds(List.of("follower-1"))).thenReturn(Map.of("follower-1", summaryData));

        when(followMapper.batchFollowCounts(List.of("follower-1"))).thenReturn(List.of(new FollowCountDTO("follower-1", 5, 0)));
        when(followMapper.batchFollowingCounts(List.of("follower-1"))).thenReturn(List.of(new FollowCountDTO("follower-1", 0, 3)));

        PageResult<UserSummaryDTO> result = inspector.getFollowers("user-1", 1, 20);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(1);
        UserSummaryDTO dto = result.getItems().get(0);
        assertThat(dto.getId()).isEqualTo("follower-1");
        assertThat(dto.getUsername()).isEqualTo("followerUser");
        assertThat(dto.getFollowerCount()).isEqualTo(5);
        assertThat(dto.getFollowingCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getFollowStats aggregates follower and following counts")
    void getFollowStats_success() {
        when(followMapper.countByFollowingId("user-1")).thenReturn(10);
        when(followMapper.countByFollowerId("user-1")).thenReturn(5);

        FollowStatsDTO stats = inspector.getFollowStats("user-1");

        assertThat(stats.getFollowerCount()).isEqualTo(10);
        assertThat(stats.getFollowingCount()).isEqualTo(5);
    }
}
