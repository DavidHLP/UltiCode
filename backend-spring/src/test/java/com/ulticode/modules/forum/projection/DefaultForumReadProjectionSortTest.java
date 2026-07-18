package com.ulticode.modules.forum.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.forum.entity.ForumPost;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for {@link DefaultForumReadProjection#applySortBy}. Locks
 * down the "hot vs top" semantics, the {@code id} tie-breaker, and the
 * failure mode for unknown {@code sortBy} values. The previous silent
 * fallback masked two real defects (hot==top, community page ignored sortBy);
 * these tests fail loudly if either regresses.
 */
class DefaultForumReadProjectionSortTest {

    /**
     * Pre-register {@link ForumPost} with MyBatis-Plus so {@code
     * LambdaQueryWrapper.getSqlSegment()} can resolve the lambda column
     * references. In a Spring context this happens at startup; in a plain
     * unit test we have to do it ourselves.
     */
    @BeforeAll
    static void registerForumPost() {
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

    @ParameterizedTest(name = "new semantics: sortBy={0}")
    @ValueSource(strings = {"new", "", "NEW", "NeW"})
    @DisplayName("new-family values order by createdAt desc then id desc")
    void newAndCoalesceOrdersByCreatedAtThenId(String sortBy) {
        LambdaQueryWrapper<ForumPost> w = new LambdaQueryWrapper<>();
        new DefaultForumReadProjection(null, null, null, null, null, null, null, null, null)
                .applySortBy(w, sortBy);

        String sql = w.getSqlSegment().toUpperCase();
        assertThat(sql).contains("ORDER BY");
        assertThat(sql).contains("CREATED_AT");
        assertThat(sql).contains("DESC");
        // Tie-breaker always present, regardless of branch.
        assertThat(sql).contains("ID DESC");
    }

    @Test
    @DisplayName("hot orders by views desc then createdAt desc then id desc")
    void hotOrdersByViewsThenRecencyThenId() {
        LambdaQueryWrapper<ForumPost> w = new LambdaQueryWrapper<>();
        new DefaultForumReadProjection(null, null, null, null, null, null, null, null, null)
                .applySortBy(w, "hot");

        String sql = w.getSqlSegment().toUpperCase();
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
        LambdaQueryWrapper<ForumPost> w = new LambdaQueryWrapper<>();
        new DefaultForumReadProjection(null, null, null, null, null, null, null, null, null)
                .applySortBy(w, "top");

        String sql = w.getSqlSegment().toUpperCase();
        int impIdx = sql.indexOf("IMPRESSIONS");
        int viewsIdx = sql.indexOf("VIEWS");
        int idIdx = sql.indexOf("ID DESC");

        assertThat(impIdx).isGreaterThanOrEqualTo(0);
        assertThat(viewsIdx).isGreaterThan(impIdx);
        assertThat(idIdx).isGreaterThan(viewsIdx);
        // The bug that motivates this whole test: top must not collapse to
        // "views desc, id desc" — it must mention impressions.
        assertThat(sql).contains("IMPRESSIONS");
    }

    @Test
    @DisplayName("hot and top produce different orderBy clause prefixes")
    void hotAndTopAreNotIdentical() {
        LambdaQueryWrapper<ForumPost> hot = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<ForumPost> top = new LambdaQueryWrapper<>();
        DefaultForumReadProjection p = new DefaultForumReadProjection(
                null, null, null, null, null, null, null, null, null);
        p.applySortBy(hot, "hot");
        p.applySortBy(top, "top");

        assertThat(hot.getSqlSegment()).isNotEqualTo(top.getSqlSegment());
    }

    @Test
    @DisplayName("controversial is a recognised case, not a silent fallback")
    void controversialIsRecognised() {
        LambdaQueryWrapper<ForumPost> w = new LambdaQueryWrapper<>();
        new DefaultForumReadProjection(null, null, null, null, null, null, null, null, null)
                .applySortBy(w, "controversial");

        // Must produce a valid ORDER BY (not throw), and must still include
        // the id tie-breaker. The exact primary signal is documented as a
        // placeholder pending a vote-distribution column.
        assertThat(w.getSqlSegment().toUpperCase()).contains("ORDER BY");
        assertThat(w.getSqlSegment().toUpperCase()).contains("ID DESC");
    }

    @Test
    @DisplayName("unknown sortBy throws FORUM_INVALID_SORT — no silent fallback")
    void unknownSortByThrows() {
        LambdaQueryWrapper<ForumPost> w = new LambdaQueryWrapper<>();
        DefaultForumReadProjection p = new DefaultForumReadProjection(
                null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> p.applySortBy(w, "pinned"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORUM_INVALID_SORT);
    }

    @Test
    @DisplayName("null sortBy coalesces to 'new' (existing contract preserved)")
    void nullSortByCoalescesToNew() {
        LambdaQueryWrapper<ForumPost> w = new LambdaQueryWrapper<>();
        new DefaultForumReadProjection(null, null, null, null, null, null, null, null, null)
                .applySortBy(w, null);

        assertThat(w.getSqlSegment().toUpperCase()).contains("CREATED_AT");
        assertThat(w.getSqlSegment().toUpperCase()).contains("ID DESC");
    }
}
