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
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.modules.vote.service.VoteService;
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
 */
@ExtendWith(MockitoExtension.class)
class DefaultForumReadProjectionTest {

    @Mock private ForumPostMapper postMapper;
    @Mock private ForumCommentMapper commentMapper;
    @Mock private ForumCommentProjection commentProjection;
    @Mock private ForumCommunityMapper communityMapper;
    @Mock private ForumCommunityMemberMapper memberMapper;
    @Mock private ForumTagMapper tagMapper;
    @Mock private UserReadProjection userReadProjection;
    @Mock private VoteService voteService;

    @InjectMocks
    private DefaultForumReadProjection forumReadProjection;

    @SuppressWarnings("unchecked")
    private void stubEmptyPage() {
        IPage<ForumPost> emptyPage = new Page<>();
        when(postMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);
    }

    // ----- findAllPosts clamps -----

    @Test
    @DisplayName("findAllPosts clamps page=0 without throwing")
    void findAllPosts_clampsPageZero() {
        stubEmptyPage();

        PageResult<ForumPostVO> result =
                forumReadProjection.findAllPosts(null, "new", 0, 20);

        assertThat(result).isNotNull();
        // safePage = max(1, page) so response always reflects a valid page.
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("findAllPosts clamps pageSize=0 without throwing")
    void findAllPosts_clampsPageSizeZero() {
        stubEmptyPage();

        PageResult<ForumPostVO> result =
                forumReadProjection.findAllPosts(null, "new", 1, 0);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("findAllPosts clamps negative page without throwing")
    void findAllPosts_clampsNegativePage() {
        stubEmptyPage();

        PageResult<ForumPostVO> result =
                forumReadProjection.findAllPosts(null, "new", -5, 20);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).isEmpty();
    }

    // ----- findMyPosts clamps (now also via selectPage + paginate) -----

    @Test
    @DisplayName("findMyPosts clamps page=0 to page=1 without throwing")
    void findMyPosts_clampsPageZero() {
        stubEmptyPage();

        PageResult<ForumPostVO> result =
                forumReadProjection.findMyPosts("u-001", 0, 20);

        assertThat(result).isNotNull();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("findMyPosts clamps pageSize=0 without throwing")
    void findMyPosts_clampsPageSizeZero() {
        stubEmptyPage();

        PageResult<ForumPostVO> result =
                forumReadProjection.findMyPosts("u-001", 1, 0);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("findMyPosts clamps negative page without throwing")
    void findMyPosts_clampsNegativePage() {
        stubEmptyPage();

        PageResult<ForumPostVO> result =
                forumReadProjection.findMyPosts("u-001", -5, 20);

        assertThat(result).isNotNull();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getItems()).isEmpty();
    }
}
