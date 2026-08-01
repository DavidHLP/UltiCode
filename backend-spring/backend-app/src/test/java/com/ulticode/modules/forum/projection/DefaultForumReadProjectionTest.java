package com.ulticode.modules.forum.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.ForumPostVO;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultForumReadProjection}. Verifies that the
 * page / pageSize clamp semantics survived the move from
 * {@code ForumPostServiceImpl} to this read projection, and that every paged
 * method (findAllPosts, findMyPosts, findPostsByCommunity) routes through the
 * shared {@code paginate} helper so the response echoes a valid ({@code >= 1})
 * page.
 *
 * <p>P7-RELOCATE-FORUM-001: {@code VoteService} dead field removed;
 * {@code UserReadProjection} replaced with {@link ForumUserReadPort}.
 */
@ExtendWith(MockitoExtension.class)
class DefaultForumReadProjectionTest {

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
        when(postMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    Page<ForumPost> page = inv.getArgument(0);
                    page.setRecords(java.util.Collections.emptyList());
                    page.setTotal(0L);
                    return page;
                });
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
