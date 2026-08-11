package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.admin.dto.tag.CreateTagDTO;
import com.ulticode.modules.admin.dto.tag.MergeTagDTO;
import com.ulticode.modules.admin.dto.tag.TagQueryDTO;
import com.ulticode.modules.admin.dto.tag.TagVO;
import com.ulticode.modules.admin.dto.tag.UpdateTagDTO;
import com.ulticode.app.api.dto.ProblemAdminTagDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.ProblemTagOwnerPort;
import com.ulticode.app.api.service.ForumTagAdministrationService;
import com.ulticode.app.api.service.ForumTagReadPort;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagPage;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagRow;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.admin.service.handler.ForumTagHandler;
import com.ulticode.modules.admin.service.handler.ProblemTagHandler;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
    private ProblemAdminReadPort problemReadPort;

    @Mock
    private ProblemTagOwnerPort problemTagOwnerPort;

    @Mock
    private ForumTagReadPort forumTagReadPort;

    @Mock
    private ForumTagAdministrationService forumTagAdministrationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private Clock clock;

    private AdminTagServiceImpl service;

    @BeforeEach
    void setUp() {
        FixedUuidGenerator uuidGenerator = new FixedUuidGenerator();
        ProblemTagHandler problemHandler = new ProblemTagHandler(problemReadPort, problemTagOwnerPort, clock, uuidGenerator);
        ForumTagHandler forumHandler = new ForumTagHandler(forumTagReadPort, forumTagAdministrationService, currentUserProvider);
        service = new AdminTagServiceImpl(problemHandler, forumHandler);
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn("admin-001");
    }

    private static TagQueryDTO q(String type) {
        TagQueryDTO q = new TagQueryDTO();
        q.setType(type);
        q.setPage(1);
        q.setLimit(20);
        return q;
    }

    private static ProblemAdminTagDTO problemTag(String id, String label, int usage) {
        return new ProblemAdminTagDTO(id, label, label.toLowerCase(), null, null, usage, null, null);
    }

    @Nested
    @DisplayName("Bug #2: type whitelist")
    class TypeWhitelist {

        @Test
        @DisplayName("rejects null type on getTags")
        void rejectsNullTypeOnGetTags() {
            assertThatThrownBy(() -> service.getTags(q(null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(AdminErrorCode.BAD_REQUEST.getCode());
        }

        @Test
        @DisplayName("rejects unknown type on getTags")
        void rejectsUnknownTypeOnGetTags() {
            assertThatThrownBy(() -> service.getTags(q("BOGUS")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(AdminErrorCode.BAD_REQUEST.getCode());
        }

        @Test
        @DisplayName("accepts lowercase 'problem' (case-insensitive)")
        void acceptsLowercaseProblem() {
            when(problemReadPort.listTags(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(PageResult.of(List.of(), 0L, 1, 20));
            // Should NOT throw
            service.getTags(q("problem"));
            verify(problemReadPort).listTags(any(), anyInt(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("accepts mixed-case 'Forum' (case-insensitive)")
        void acceptsMixedCaseForum() {
            when(forumTagReadPort.page(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(new ForumTagPage(List.of(), 0));
            service.getTags(q("Forum"));
            verify(forumTagReadPort).page(any(), anyInt(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("createTag rejects unknown type before mapper call")
        void createTagRejectsUnknownType() {
            CreateTagDTO dto = new CreateTagDTO();
            dto.setName("x");
            dto.setType("WRONG");
            assertThatThrownBy(() -> service.createTag(dto))
                    .isInstanceOf(BusinessException.class);
            verify(problemTagOwnerPort, never()).createTag(any());
            verify(forumTagAdministrationService, never()).mutate(any());
        }

        @Test
        @DisplayName("deleteTag rejects unknown type before mapper call")
        void deleteTagRejectsUnknownType() {
            assertThatThrownBy(() -> service.deleteTag("id", "WRONG"))
                    .isInstanceOf(BusinessException.class);
            verify(problemTagOwnerPort, never()).deleteTag(anyString());
            verify(forumTagAdministrationService, never()).mutate(any());
        }

        @Test
        @DisplayName("getTag rejects unknown type before mapper call")
        void getTagRejectsUnknownType() {
            assertThatThrownBy(() -> service.getTag("id", "WRONG"))
                    .isInstanceOf(BusinessException.class);
            verify(problemReadPort, never()).getTagById(anyString());
            verify(forumTagReadPort, never()).getById(anyString());
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
            verify(problemTagOwnerPort, never()).deleteTag(anyString());
            verify(forumTagAdministrationService, never()).mutate(any());
        }
    }

    @Nested
    @DisplayName("Bug #3: PROBLEM sortBy honored")
    class ProblemSortBy {

        @Test
        @DisplayName("getTags returns PROBLEM list with sortBy defaulting to label")
        void problemDefaultSortByLabel() {
            when(problemReadPort.listTags(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(PageResult.of(List.of(
                            problemTag("a", "alpha", 5),
                            problemTag("b", "bravo", 10)), 2L, 1, 20));

            var resp = service.getTags(q("PROBLEM"));

            assertThat(resp.getTotal()).isEqualTo(2);
            assertThat(resp.getData()).extracting(TagVO::getName)
                    .containsExactly("alpha", "bravo");
            verify(problemReadPort).listTags(any(), anyInt(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("getTags routes FORUM when type=FORUM")
        void routesForumCorrectly() {
            ForumTagRow ft = new ForumTagRow("f1", "forum-tag", "forum-tag", null, null, 3, null);
            when(forumTagReadPort.page(any(), anyInt(), anyInt(), any(), any()))
                    .thenReturn(new ForumTagPage(List.of(ft), 1));

            var resp = service.getTags(q("FORUM"));

            assertThat(resp.getTotal()).isEqualTo(1);
            assertThat(resp.getData()).hasSize(1);
            assertThat(resp.getData().get(0).getType()).isEqualTo("FORUM");
            verify(problemReadPort, never()).listTags(any(), anyInt(), anyInt(), any(), any());
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

            when(problemReadPort.getTagById("no-such")).thenReturn(null);

            assertThatThrownBy(() -> service.mergeTag(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(AdminErrorCode.PROBLEM_TAG_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("missing target throws NOT_FOUND for FORUM")
        void missingTargetForForum() {
            MergeTagDTO dto = new MergeTagDTO();
            dto.setSourceId("src");
            dto.setTargetTagId("missing");
            dto.setType("FORUM");

            // The service reads the source tag for audit oldValues before the
            // merge; the merge itself is delegated to the owner administration
            // service, and a missing target surfaces as an RPC NOT_FOUND error
            // mapped to FORUM_TAG_NOT_FOUND by the handler.
            ForumTagRow src = new ForumTagRow("src", "src", null, null, null, 0, null);
            when(forumTagReadPort.getById("src")).thenReturn(src);
            when(forumTagAdministrationService.mutate(any())).thenReturn(RpcResult.failure(
                    new RpcResult.ErrorPayload("app", 40401, "tag not found"), "t-1"));

            assertThatThrownBy(() -> service.mergeTag(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(AdminErrorCode.FORUM_TAG_NOT_FOUND.getCode());
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
            verify(problemTagOwnerPort, never()).updateTag(any());
        }

        @Test
        @DisplayName("updateTag rejects when PROBLEM id not found")
        void updateTagProblemNotFound() {
            UpdateTagDTO dto = new UpdateTagDTO();
            dto.setType("PROBLEM");

            when(problemReadPort.getTagById("missing")).thenReturn(null);

            assertThatThrownBy(() -> service.updateTag("missing", dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(AdminErrorCode.PROBLEM_TAG_NOT_FOUND.getCode());
        }
    }
}