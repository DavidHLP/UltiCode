package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.AdminForumPostPage;
import com.ulticode.app.api.dto.AdminForumPostQuery;
import com.ulticode.app.api.dto.AdminForumPostRowDTO;
import com.ulticode.app.api.service.AdminForumReadPort;
import com.ulticode.modules.admin.dto.AdminForumPostQueryDTO;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAdminForumProjection} &mdash; the read-side
 * deep module lifted out of AdminForumServiceImpl per ADR-0011 Stage 2.
 *
 * <p>Covers the read paths that previously lived on
 * {@code AdminForumServiceImplTest}: {@code getPosts} real comment count
 * enrichment (batch-loaded from {@code forum_comments}) and the
 * zero-comments fallback. After ADMIN-007 the underlying data comes from
 * the App-owned {@link AdminForumReadPort} (Dubbo), which composes the
 * comment counts and vote counts; these tests pin the projection's
 * VO-shaping contract against that port.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAdminForumProjection")
class AdminForumProjectionTest {

    @Mock
    private AdminForumReadPort adminForumReadPort;

    @Mock
    private AdminUserEnricher userEnricher;

    private DefaultAdminForumProjection projection;

    private AdminForumPostRowDTO testPost;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminForumProjection(adminForumReadPort, userEnricher);

        testPost = new AdminForumPostRowDTO();
        testPost.setId("post-test-001");
        testPost.setTitle("Test Post");
        testPost.setUserId("user-001");
        testPost.setCommunityId("community-001");
        testPost.setCommunityName("General");
        testPost.setCommunitySlug("general");
        testPost.setViews(100);
        testPost.setIsPinned(false);
        testPost.setIsLocked(false);
        testPost.setIsFlagged(false);
        testPost.setIsDeleted(false);
    }

    @Test
    @DisplayName("getPosts returns real comment and vote counts from the read port")
    void getPosts_returnsRealCommentCount() {
        testPost.setCommentCount(5);
        testPost.setUpvotes(10);
        testPost.setDownvotes(2);
        when(adminForumReadPort.listPosts(any(AdminForumPostQuery.class)))
                .thenReturn(new AdminForumPostPage(List.of(testPost), 1));
        when(userEnricher.enrich(any())).thenReturn(Map.of());

        var result = projection.getPosts(createDefaultQuery());

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getCommentCount()).isEqualTo(5);
        assertThat(result.getItems().get(0).getUpvotes()).isEqualTo(10);
        assertThat(result.getItems().get(0).getDownvotes()).isEqualTo(2);
        assertThat(result.getItems().get(0).getCommunityName()).isEqualTo("General");
    }

    @Test
    @DisplayName("getPosts returns zero when no comments or votes exist")
    void getPosts_returnsZeroCommentCountWhenNoComments() {
        testPost.setCommentCount(0);
        testPost.setUpvotes(0);
        testPost.setDownvotes(0);
        when(adminForumReadPort.listPosts(any(AdminForumPostQuery.class)))
                .thenReturn(new AdminForumPostPage(List.of(testPost), 1));
        when(userEnricher.enrich(any())).thenReturn(Map.of());

        var result = projection.getPosts(createDefaultQuery());

        assertThat(result.getItems().get(0).getCommentCount()).isEqualTo(0);
        assertThat(result.getItems().get(0).getUpvotes()).isEqualTo(0);
        assertThat(result.getItems().get(0).getDownvotes()).isEqualTo(0);
    }

    @Test
    @DisplayName("getPosts handles empty result page without touching the enricher")
    void getPosts_emptyPageSkipsEnrichment() {
        when(adminForumReadPort.listPosts(any(AdminForumPostQuery.class)))
                .thenReturn(new AdminForumPostPage(List.of(), 0));

        var result = projection.getPosts(createDefaultQuery());

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    private AdminForumPostQueryDTO createDefaultQuery() {
        AdminForumPostQueryDTO dto = new AdminForumPostQueryDTO();
        dto.setPage(1);
        dto.setLimit(10);
        dto.setSortBy("createdAt");
        dto.setSortOrder("desc");
        return dto;
    }
}
