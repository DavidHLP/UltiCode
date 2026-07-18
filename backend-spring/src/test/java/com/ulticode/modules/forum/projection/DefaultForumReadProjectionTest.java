package com.ulticode.modules.forum.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultForumReadProjection}. Verifies that the
 * page / pageSize clamp semantics survived the move from
 * {@code ForumPostServiceImpl} to this read projection.
 *
 * <p>Test isolation: each test wires only the stubs it needs. Strictness is
 * LENIENT because the projection has many collaborators that not every
 * test path touches.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    // ----- findAllPosts clamps -----

    @Test
    @DisplayName("findAllPosts clamps page=0 without throwing")
    @SuppressWarnings("unchecked")
    void findAllPosts_clampsPageZero() {
        IPage<ForumPost> emptyPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        when(postMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

        PageResult<ForumPostVO> result =
                forumReadProjection.findAllPosts(null, "new", 0, 20);

        assertThat(result).isNotNull();
        // safePage = max(1, page) so response always reflects a valid page.
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("findAllPosts clamps pageSize=0 without throwing")
    @SuppressWarnings("unchecked")
    void findAllPosts_clampsPageSizeZero() {
        IPage<ForumPost> emptyPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        when(postMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

        PageResult<ForumPostVO> result =
                forumReadProjection.findAllPosts(null, "new", 1, 0);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("findAllPosts clamps negative page without throwing")
    @SuppressWarnings("unchecked")
    void findAllPosts_clampsNegativePage() {
        IPage<ForumPost> emptyPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        when(postMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

        PageResult<ForumPostVO> result =
                forumReadProjection.findAllPosts(null, "new", -5, 20);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).isEmpty();
    }

    // ----- findMyPosts clamps -----

    @Test
    @DisplayName("findMyPosts clamps page=0 without throwing")
    void findMyPosts_clampsPageZero() {
        when(postMapper.countByUserId(any())).thenReturn(0L);
        when(postMapper.findByUserId(any())).thenReturn(Collections.emptyList());

        PageResult<ForumPostVO> result =
                forumReadProjection.findMyPosts("u-001", 0, 20);

        assertThat(result).isNotNull();
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("findMyPosts clamps pageSize=0 without throwing")
    void findMyPosts_clampsPageSizeZero() {
        when(postMapper.countByUserId(any())).thenReturn(0L);
        when(postMapper.findByUserId(any())).thenReturn(Collections.emptyList());

        PageResult<ForumPostVO> result =
                forumReadProjection.findMyPosts("u-001", 1, 0);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("findMyPosts clamps negative page without throwing")
    void findMyPosts_clampsNegativePage() {
        when(postMapper.countByUserId(any())).thenReturn(0L);
        when(postMapper.findByUserId(any())).thenReturn(Collections.emptyList());

        PageResult<ForumPostVO> result =
                forumReadProjection.findMyPosts("u-001", -5, 20);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).isEmpty();
    }
}