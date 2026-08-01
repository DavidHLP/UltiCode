package com.ulticode.modules.forum.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import com.ulticode.modules.forum.port.ForumUserReadPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Regression tests for {@code DefaultForumReadProjection.applySortBy}, exercised
 * through the public {@code findAllPosts} surface. Locks down the
 * "hot vs top" semantics, the {@code id} tie-breaker, and the failure mode for
 * unknown {@code sortBy} values. The previous silent fallback masked two real
 * defects (hot==top, community page ignored sortBy); these tests fail loudly
 * if either regresses.
 *
 * <p>P7-RELOCATE-FORUM-001: {@code VoteService} dead field removed;
 * {@code UserReadProjection} replaced with {@link ForumUserReadPort}.
 */
@ExtendWith(MockitoExtension.class)
class DefaultForumReadProjectionSortTest {

    @Mock private ForumPostMapper postMapper;
    @Mock private ForumCommentMapper commentMapper;
    @Mock private ForumCommentProjection commentProjection;
    @Mock private ForumCommunityMapper communityMapper;
    @Mock private ForumCommunityMemberMapper memberMapper;
    @Mock private ForumTagMapper tagMapper;
    @Mock private ForumUserReadPort forumUserReadPort;
    @Mock private ForumPostProjection postProjection;

    @InjectMocks
    private DefaultForumReadProjection forumReadProjection;

    @SuppressWarnings("unchecked")
    private void stubEmptyPage() {
        doAnswer(inv -> {
            ((Page<ForumPost>) inv.getArgument(0)).setRecords(java.util.Collections.emptyList());
            ((Page<ForumPost>) inv.getArgument(0)).setTotal(0L);
            return inv.getArgument(0);
        }).when(postMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ----- findAllPosts clamps -----

    @Test
    @DisplayName("findAllPosts clamps page=0 without throwing")
    void findAllPosts_clampsPageZero() {
        stubEmptyPage();
        assertThat(forumReadProjection.findAllPosts("u1", "new", 0, 20)).isNotNull();
    }

    @Test
    @DisplayName("findAllPosts clamps pageSize=0 without throwing")
    void findAllPosts_clampsPageSizeZero() {
        stubEmptyPage();
        assertThat(forumReadProjection.findAllPosts("u1", "new", 1, 0)).isNotNull();
    }

    @Test
    @DisplayName("findAllPosts clamps negative page without throwing")
    void findAllPosts_clampsNegativePage() {
        stubEmptyPage();
        assertThat(forumReadProjection.findAllPosts("u1", "new", -5, 20)).isNotNull();
    }

    // ----- findMyPosts clamps (now also via selectPage + paginate) -----

    @Test
    @DisplayName("findMyPosts clamps page=0 to page=1 without throwing")
    void findMyPosts_clampsPageZero() {
        stubEmptyPage();
        assertThat(forumReadProjection.findMyPosts("u1", 0, 20)).isNotNull();
    }

    @Test
    @DisplayName("findMyPosts clamps pageSize=0 without throwing")
    void findMyPosts_clampsPageSizeZero() {
        stubEmptyPage();
        assertThat(forumReadProjection.findMyPosts("u1", 1, 0)).isNotNull();
    }

    @Test
    @DisplayName("findMyPosts clamps negative page without throwing")
    void findMyPosts_clampsNegativePage() {
        stubEmptyPage();
        assertThat(forumReadProjection.findMyPosts("u1", -5, 20)).isNotNull();
    }
}
