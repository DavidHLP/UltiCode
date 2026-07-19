package com.ulticode.modules.forum.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.testsupport.MyBatisPlusLambdaCacheSupport;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.modules.vote.service.VoteService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Regression tests for {@code DefaultForumReadProjection.applySortBy}, exercised
 * through the public {@code findAllPosts} surface. Locks down the
 * "hot vs top" semantics, the {@code id} tie-breaker, and the failure mode for
 * unknown {@code sortBy} values. The previous silent fallback masked two real
 * defects (hot==top, community page ignored sortBy); these tests fail loudly
 * if either regresses.
 */
@ExtendWith(MockitoExtension.class)
class DefaultForumReadProjectionSortTest {

    @Mock private ForumPostMapper postMapper;
    @Mock private ForumCommentMapper commentMapper;
    @Mock private ForumCommentProjection commentProjection;
    @Mock private ForumCommunityMapper communityMapper;
    @Mock private ForumCommunityMemberMapper memberMapper;
    @Mock private ForumTagMapper tagMapper;
    @Mock private UserReadProjection userReadProjection;
    @Mock private VoteService voteService;

    @InjectMocks
    private DefaultForumReadProjection projection;

    /** Last wrapper passed to {@code selectPage}; captured via a doAnswer stub. */
    private LambdaQueryWrapper<ForumPost> lastWrapper;

    /**
     * Bootstrap the MyBatis-Plus lambda cache so {@code
     * LambdaQueryWrapper.getSqlSegment()} can resolve column references.
     * See {@link MyBatisPlusLambdaCacheSupport}.
     */
    @BeforeAll
    static void bootstrapLambdaCache() {
        MyBatisPlusLambdaCacheSupport.register(ForumPost.class);
    }

    private String sqlFor(String sortBy) {
        // Stub per-test (strict stubs): only the tests that drive findAllPosts
        // pay for it, and the unknown-sortBy case never reaches selectPage.
        IPage<ForumPost> emptyPage = new Page<>();
        doAnswer(invocation -> {
            lastWrapper = invocation.getArgument(1);
            return emptyPage;
        }).when(postMapper).selectPage(any(IPage.class), any(LambdaQueryWrapper.class));
        lastWrapper = null;
        projection.findAllPosts(null, sortBy, 1, 20);
        assertThat(lastWrapper).as("selectPage was not called for sortBy=%s", sortBy).isNotNull();
        return lastWrapper.getSqlSegment().toUpperCase();
    }

    @ParameterizedTest(name = "new-family: sortBy={0}")
    @ValueSource(strings = {"new", "", "NEW", "NeW"})
    @DisplayName("new-family values order by createdAt desc then id desc")
    void newAndCoalesceOrdersByCreatedAtThenId(String sortBy) {
        String sql = sqlFor(sortBy);
        assertThat(sql).contains("ORDER BY");
        assertThat(sql).contains("CREATED_AT");
        assertThat(sql).contains("DESC");
        assertThat(sql).contains("ID DESC");
    }

    @Test
    @DisplayName("hot orders by views desc then createdAt desc then id desc")
    void hotOrdersByViewsThenRecencyThenId() {
        String sql = sqlFor("hot");
        int viewsIdx = sql.indexOf("VIEWS");
        int createdIdx = sql.indexOf("CREATED_AT");
        int idIdx = sql.indexOf("ID DESC");
        assertThat(viewsIdx).isGreaterThanOrEqualTo(0);
        assertThat(createdIdx).isGreaterThan(viewsIdx);
        assertThat(idIdx).isGreaterThan(createdIdx);
    }

    @Test
    @DisplayName("top orders by impressions desc then views desc then id desc — distinct from hot")
    void topOrdersByImpressionsThenViewsThenId() {
        String sql = sqlFor("top");
        int impIdx = sql.indexOf("IMPRESSIONS");
        int viewsIdx = sql.indexOf("VIEWS");
        int idIdx = sql.indexOf("ID DESC");
        assertThat(impIdx).isGreaterThanOrEqualTo(0);
        assertThat(viewsIdx).isGreaterThan(impIdx);
        assertThat(idIdx).isGreaterThan(viewsIdx);
        assertThat(sql).contains("IMPRESSIONS");
    }

    @Test
    @DisplayName("hot and top produce different orderBy clause prefixes")
    void hotAndTopAreNotIdentical() {
        assertThat(sqlFor("hot")).isNotEqualTo(sqlFor("top"));
    }

    @Test
    @DisplayName("controversial is a recognised case, not a silent fallback")
    void controversialIsRecognised() {
        String sql = sqlFor("controversial");
        assertThat(sql).contains("ORDER BY");
        assertThat(sql).contains("ID DESC");
    }

    @Test
    @DisplayName("controversial currently mirrors hot (placeholder) — TODO: diverge once vote columns exist")
    void controversialCurrentlyMirrorsHotPlaceholder() {
        // Spec 3.8: controversial is an accepted placeholder. Today it reuses
        // the hot signal because forum_posts has no score/upvotes/downvotes
        // column. This test pins the *current* degenerate behavior so the
        // placeholder is explicit; when a vote-distribution column lands,
        // flip this assertion to isNotEqualTo(...) to lock the divergence.
        assertThat(sqlFor("controversial")).isEqualTo(sqlFor("hot"));
    }

    @Test
    @DisplayName("unknown sortBy throws FORUM_INVALID_SORT — no silent fallback")
    void unknownSortByThrows() {
        assertThatThrownBy(() -> projection.findAllPosts(null, "pinned", 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORUM_INVALID_SORT);
    }

    @Test
    @DisplayName("null sortBy coalesces to 'new' (existing contract preserved)")
    void nullSortByCoalescesToNew() {
        String sql = sqlFor(null);
        assertThat(sql).contains("CREATED_AT");
        assertThat(sql).contains("ID DESC");
    }
}
