package com.ulticode.modules.forum.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Regression tests for {@code DefaultForumReadProjection.applySortBy}, exercised
 * through the public {@code findAllPosts} surface. Locks down the
 * "hot vs top" semantics, the {@code id} tie-breaker, and the failure mode for
 * unknown {@code sortBy} values. The previous silent fallback masked two real
 * defects (hot==top, community page ignored sortBy); these tests fail loudly
 * if either regresses.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    @SuppressWarnings("unchecked")
    @BeforeEach
    void stubEmptyPage() {
        IPage<ForumPost> emptyPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        when(postMapper.selectPage(any(IPage.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);
        doAnswer(invocation -> {
            lastWrapper = invocation.getArgument(1);
            return emptyPage;
        }).when(postMapper).selectPage(any(IPage.class), any(LambdaQueryWrapper.class));
    }

    /**
     * Bootstrap the MyBatis-Plus lambda cache so {@code
     * LambdaQueryWrapper.getSqlSegment()} can resolve column references.
     * {@code TableInfoHelper.initTableInfo(BuilderAssistant, Class)} is the
     * only public route that initialises a table for a non-Spring test.
     */
    @BeforeAll
    static void bootstrapLambdaCache() {
        try {
            Class<?> assistantClass = Class.forName(
                    "com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant");
            org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                    (org.apache.ibatis.builder.MapperBuilderAssistant) assistantClass
                            .getDeclaredConstructor(
                                    org.apache.ibatis.session.Configuration.class,
                                    String.class)
                            .newInstance(new org.apache.ibatis.session.Configuration(), "");
            TableInfoHelper.initTableInfo(assistant, ForumPost.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to register ForumPost with MyBatis-Plus TableInfoHelper", e);
        }
    }

    private String sqlFor(String sortBy) {
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
