package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.admin.dto.AdminForumPostQueryDTO;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.admin.projection.AdminUserEnricher;
import com.ulticode.modules.admin.projection.AdminUserSummary;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Unit tests for {@link DefaultAdminForumProjection} &mdash; the read-side
 * deep module lifted out of AdminForumServiceImpl per ADR-0011 Stage 2.
 *
 * <p>Covers the read paths that previously lived on
 * {@code AdminForumServiceImplTest}: {@code getPosts} real comment count
 * enrichment (batch-loaded from {@code forum_comments}) and the zero-comments
 * fallback. These cases were migrated verbatim when the read cluster moved
 * behind the projection seam.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAdminForumProjection")
class AdminForumProjectionTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock private ForumPostMapper forumPostMapper;
    @Mock private ForumCommentMapper forumCommentMapper;
    @Mock private ForumCommunityMapper forumCommunityMapper;
    @Mock private AdminUserEnricher userEnricher;
    @Mock private EdgeOperationMapper edgeOperationMapper;

    private DefaultAdminForumProjection projection;

    private ForumPost testPost;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminForumProjection(
                forumPostMapper, forumCommentMapper, forumCommunityMapper,
                edgeOperationMapper, userEnricher);

        testPost = new ForumPost();
        testPost.setId("post-test-001");
        testPost.setTitle("Test Post");
        testPost.setUserId("user-001");
        testPost.setCommunityId("community-001");
        testPost.setViews(100);
        testPost.setIsPinned(false);
        testPost.setIsLocked(false);
        testPost.setIsFlagged(false);
        testPost.setIsDeleted(false);
    }

    @Test
    @DisplayName("getPosts returns real comment count from forum_comments table")
    void getPosts_returnsRealCommentCount() {
        Page<ForumPost> mockPage = new Page<>();
        mockPage.setRecords(List.of(testPost));
        mockPage.setTotal(1L);
        when(forumPostMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);
        when(forumCommentMapper.countByPostIds(anyList())).thenReturn(
                List.of(Map.of("post_id", "post-test-001", "cnt", 5L)));
        when(edgeOperationMapper.countByTargetsAndOperation(anyList(), eq("FORUM_POST"), eq("VOTE_UP"))).thenReturn(
                List.of(Map.of("target_id", "post-test-001", "cnt", 10)));
        when(edgeOperationMapper.countByTargetsAndOperation(anyList(), eq("FORUM_POST"), eq("VOTE_DOWN"))).thenReturn(
                List.of(Map.of("target_id", "post-test-001", "cnt", 2)));

        var result = projection.getPosts(createDefaultQuery());

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getCommentCount()).isEqualTo(5);
        assertThat(result.getItems().get(0).getUpvotes()).isEqualTo(10);
        assertThat(result.getItems().get(0).getDownvotes()).isEqualTo(2);
    }

    @Test
    @DisplayName("getPosts returns zero when no comments exist")
    void getPosts_returnsZeroCommentCountWhenNoComments() {
        Page<ForumPost> mockPage = new Page<>();
        mockPage.setRecords(List.of(testPost));
        mockPage.setTotal(1L);
        when(forumPostMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);
        when(forumCommentMapper.countByPostIds(anyList())).thenReturn(List.of());
        when(edgeOperationMapper.countByTargetsAndOperation(anyList(), eq("FORUM_POST"), eq("VOTE_UP"))).thenReturn(List.of());
        when(edgeOperationMapper.countByTargetsAndOperation(anyList(), eq("FORUM_POST"), eq("VOTE_DOWN"))).thenReturn(List.of());

        var result = projection.getPosts(createDefaultQuery());

        assertThat(result.getItems().get(0).getCommentCount()).isEqualTo(0);
        assertThat(result.getItems().get(0).getUpvotes()).isEqualTo(0);
        assertThat(result.getItems().get(0).getDownvotes()).isEqualTo(0);
    }

    @Test
    @DisplayName("getPosts handles empty result page without calling enrichment mappers")
    void getPosts_emptyPageSkipsEnrichment() {
        Page<ForumPost> mockPage = new Page<>();
        mockPage.setRecords(List.of());
        mockPage.setTotal(0L);
        when(forumPostMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        var result = projection.getPosts(createDefaultQuery());

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    private AdminForumPostQueryDTO createDefaultQuery() {
        AdminForumPostQueryDTO dto = new AdminForumPostQueryDTO();
        dto.setPage(1);
        dto.setLimit(10);
        return dto;
    }
}
