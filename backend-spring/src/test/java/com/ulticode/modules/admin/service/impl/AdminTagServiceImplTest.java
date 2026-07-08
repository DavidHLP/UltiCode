package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.admin.dto.tag.CreateTagDTO;
import com.ulticode.modules.admin.dto.tag.MergeTagDTO;
import com.ulticode.modules.admin.dto.tag.TagQueryDTO;
import com.ulticode.modules.admin.dto.tag.TagVO;
import com.ulticode.modules.admin.dto.tag.UpdateTagDTO;
import com.ulticode.modules.forum.entity.ForumTag;
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.admin.service.handler.ForumTagHandler;
import com.ulticode.modules.admin.service.handler.ProblemTagHandler;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminTagServiceImpl}.
 *
 * <p>Focuses on the bugs reported in <code>docs/admin-tags-test-plan.md §7</code>:
 * type whitelist (Bug #2), PROBLEM sortBy honoring (Bug #3), and merge guards.
 * Tests use manual constructor injection (mirrors AdminUserServiceImplTest) so each
 * mock dependency is explicit and the @RequiredArgsConstructor 3-arg contract is
 * verified at compile time.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTagServiceImpl")
class AdminTagServiceImplTest {

    @Mock
    private ProblemTagMapper problemTagMapper;

    @Mock
    private ProblemTagRelationMapper problemTagRelationMapper;

    @Mock
    private ForumTagMapper forumTagMapper;
    @Mock
    private Clock clock;

    private AdminTagServiceImpl service;

    @BeforeEach
    void setUp() {
        FixedUuidGenerator uuidGenerator = new FixedUuidGenerator();
        ProblemTagHandler problemHandler = new ProblemTagHandler(problemTagMapper, problemTagRelationMapper, clock, uuidGenerator);
        ForumTagHandler forumHandler = new ForumTagHandler(forumTagMapper, clock, uuidGenerator);
        service = new AdminTagServiceImpl(problemHandler, forumHandler);
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
    }

    private static TagQueryDTO q(String type) {
        TagQueryDTO q = new TagQueryDTO();
        q.setType(type);
        q.setPage(1);
        q.setLimit(20);
        return q;
    }

    private static ProblemTag problemTag(String id, String label, int usage) {
        ProblemTag t = new ProblemTag();
        t.setId(id);
        t.setLabel(label);
        t.setSlug(label.toLowerCase());
        t.setUsageCount(usage);
        return t;
    }

    @Nested
    @DisplayName("Bug #2: type whitelist")
    class TypeWhitelist {

        @Test
        @DisplayName("rejects null type on getTags")
        void rejectsNullTypeOnGetTags() {
            assertThatThrownBy(() -> service.getTags(q(null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        }

        @Test
        @DisplayName("rejects unknown type on getTags")
        void rejectsUnknownTypeOnGetTags() {
            assertThatThrownBy(() -> service.getTags(q("BOGUS")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.BAD_REQUEST.getCode());
        }

        @Test
        @DisplayName("accepts lowercase 'problem' (case-insensitive)")
        void acceptsLowercaseProblem() {
            when(problemTagMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());
            // Should NOT throw
            service.getTags(q("problem"));
            verify(problemTagMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("accepts mixed-case 'Forum' (case-insensitive)")
        void acceptsMixedCaseForum() {
            when(forumTagMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());
            service.getTags(q("Forum"));
            verify(forumTagMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("createTag rejects unknown type before mapper call")
        void createTagRejectsUnknownType() {
            CreateTagDTO dto = new CreateTagDTO();
            dto.setName("x");
            dto.setType("WRONG");
            assertThatThrownBy(() -> service.createTag(dto))
                    .isInstanceOf(BusinessException.class);
            verify(problemTagMapper, never()).insert(any(ProblemTag.class));
            verify(forumTagMapper, never()).insert(any(ForumTag.class));
        }

        @Test
        @DisplayName("deleteTag rejects unknown type before mapper call")
        void deleteTagRejectsUnknownType() {
            assertThatThrownBy(() -> service.deleteTag("id", "WRONG"))
                    .isInstanceOf(BusinessException.class);
            verify(problemTagMapper, never()).deleteById(anyString());
            verify(forumTagMapper, never()).deleteById(anyString());
        }

        @Test
        @DisplayName("getTag rejects unknown type before mapper call")
        void getTagRejectsUnknownType() {
            assertThatThrownBy(() -> service.getTag("id", "WRONG"))
                    .isInstanceOf(BusinessException.class);
            verify(problemTagMapper, never()).selectById(anyString());
            verify(forumTagMapper, never()).selectById(anyString());
        }

        @Test
        @DisplayName("mergeTag rejects unknown type before mapper call")
        void mergeTagRejectsUnknownType() {
            MergeTagDTO dto = new MergeTagDTO();
            dto.setSourceId("a");
            dto.setTargetTagId("b");
            dto.setType("WRONG");
            assertThatThrownBy(() -> service.mergeTag(dto))
                    .isInstanceOf(BusinessException.class);
            verify(problemTagMapper, never()).deleteById(anyString());
            verify(forumTagMapper, never()).deleteById(anyString());
        }
    }

    @Nested
    @DisplayName("Bug #3: PROBLEM sortBy honored")
    class ProblemSortBy {

        @Test
        @DisplayName("getTags returns PROBLEM list with sortBy defaulting to label")
        void problemDefaultSortByLabel() {
            Page<ProblemTag> page = new Page<>();
            page.setRecords(List.of(
                    problemTag("a", "alpha", 5),
                    problemTag("b", "bravo", 10)));
            page.setTotal(2);
            when(problemTagMapper.selectPage(any(Page.class), any())).thenReturn(page);

            var resp = service.getTags(q("PROBLEM"));

            assertThat(resp.getTotal()).isEqualTo(2);
            assertThat(resp.getData()).extracting(TagVO::getName)
                    .containsExactly("alpha", "bravo");
            verify(problemTagMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("getTags routes FORUM when type=FORUM")
        void routesForumCorrectly() {
            Page<ForumTag> page = new Page<>();
            ForumTag ft = new ForumTag();
            ft.setId("f1");
            ft.setName("forum-tag");
            ft.setSlug("forum-tag");
            ft.setUsageCount(3);
            page.setRecords(List.of(ft));
            page.setTotal(1);
            when(forumTagMapper.selectPage(any(Page.class), any())).thenReturn(page);

            var resp = service.getTags(q("FORUM"));

            assertThat(resp.getTotal()).isEqualTo(1);
            assertThat(resp.getData()).hasSize(1);
            assertThat(resp.getData().get(0).getType()).isEqualTo("FORUM");
            verify(problemTagMapper, never()).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("Merge guards")
    class MergeGuards {

        @Test
        @DisplayName("self-merge throws BAD_REQUEST")
        void selfMergeRejected() {
            MergeTagDTO dto = new MergeTagDTO();
            dto.setSourceId("same");
            dto.setTargetTagId("same");
            dto.setType("PROBLEM");

            assertThatThrownBy(() -> service.mergeTag(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot merge tag into itself");
        }

        @Test
        @DisplayName("missing source throws NOT_FOUND for PROBLEM")
        void missingSourceForProblem() {
            MergeTagDTO dto = new MergeTagDTO();
            dto.setSourceId("no-such");
            dto.setTargetTagId("exists");
            dto.setType("PROBLEM");

            when(problemTagMapper.selectById("no-such")).thenReturn(null);

            assertThatThrownBy(() -> service.mergeTag(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PROBLEM_TAG_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("missing target throws NOT_FOUND for FORUM")
        void missingTargetForForum() {
            MergeTagDTO dto = new MergeTagDTO();
            dto.setSourceId("src");
            dto.setTargetTagId("missing");
            dto.setType("FORUM");

            ForumTag src = new ForumTag();
            src.setId("src");
            src.setName("src");
            when(forumTagMapper.selectById("src")).thenReturn(src);
            when(forumTagMapper.selectById("missing")).thenReturn(null);

            assertThatThrownBy(() -> service.mergeTag(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.FORUM_TAG_NOT_FOUND.getCode());
        }
    }

    @Nested
    @DisplayName("Update guards")
    class UpdateGuards {

        @Test
        @DisplayName("updateTag rejects unknown type")
        void updateTagRejectsUnknownType() {
            UpdateTagDTO dto = new UpdateTagDTO();
            dto.setType("WRONG");

            assertThatThrownBy(() -> service.updateTag("id", dto))
                    .isInstanceOf(BusinessException.class);
            verify(problemTagMapper, never()).updateById(any(ProblemTag.class));
        }

        @Test
        @DisplayName("updateTag rejects when PROBLEM id not found")
        void updateTagProblemNotFound() {
            UpdateTagDTO dto = new UpdateTagDTO();
            dto.setType("PROBLEM");

            when(problemTagMapper.selectById("missing")).thenReturn(null);

            assertThatThrownBy(() -> service.updateTag("missing", dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PROBLEM_TAG_NOT_FOUND.getCode());
        }
    }
}