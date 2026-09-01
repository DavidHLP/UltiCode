package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.SolutionAdminReadPort;
import com.ulticode.app.api.service.SolutionAdminReadPort.SolutionAdminPage;
import com.ulticode.app.api.service.SolutionAdminReadPort.SolutionAdminQuery;
import com.ulticode.app.api.service.SolutionAdminReadPort.SolutionAdminRow;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminSolutionListItemVO;
import com.ulticode.modules.admin.dto.AdminSolutionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSolutionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAdminSolutionProjection} &mdash; the read-side
 * deep module lifted out of AdminSolutionServiceImpl per ADR-0011 Stage 2.
 *
 * <p>Pins the three behaviours that moved behind the projection seam:
 * <ul>
 *   <li>{@code getSolution(id)} throws when missing and enriches inline when
 *       present (single-detail path).</li>
 *   <li>{@code getFlaggedSolutions} forces {@code isFlagged=true} AND
 *       {@code isDeleted=false} regardless of caller input (BUG-Q9).</li>
 *   <li>{@code getSolutions(isDeleted=true)} passes {@code includeDeleted}
 *       to {@link SolutionAdminReadPort} so the provider takes the raw-SQL
 *       deleted branch; otherwise the active branch is requested.</li>
 * </ul>
 *
 * <p>ADMIN-006: the solution entity/mapper are gone — the projection
 * consumes the entity-free {@link SolutionAdminReadPort} seam and keeps the
 * batch user/problem enrichment locally. The existing
 * {@code AdminSolutionServiceImplTest} continues to cover the four write
 * methods that stayed on the service.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAdminSolutionProjection")
class AdminSolutionProjectionTest {

    @Mock
    private SolutionAdminReadPort solutionAdminReadPort;
    @Mock
    private AdminUserEnricher userEnricher;
    @Mock
    private ProblemAdminReadPort problemReadPort;

    @InjectMocks
    private DefaultAdminSolutionProjection projection;

    @Nested
    @DisplayName("getSolution(id) — single-detail read")
    class GetSolution {

        @Test
        @DisplayName("throws BusinessException(SOLUTION_NOT_FOUND) when id absent")
        void throwsWhenNotFound() {
            when(solutionAdminReadPort.getById("sol-missing")).thenReturn(null);
            assertThrows(BusinessException.class, () -> projection.getSolution("sol-missing"));
        }
        @Test
        void problemOwnerFailureIsTypedAsUnavailable() {
            SolutionAdminRow sol = row("sol-3", 100L, "user-3", "Title", null, null, null);
            when(solutionAdminReadPort.getById("sol-3")).thenReturn(sol);
            when(userEnricher.enrichOne("user-3")).thenReturn(null);
            when(problemReadPort.findProblem(100L))
                    .thenThrow(new IllegalStateException("problem owner offline"));

            assertThatThrownBy(() -> projection.getSolution("sol-3"))
                    .extracting("errorCode")
                    .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE);
        }


        @Test
        @DisplayName("enriches author + problem inline and copies every detail field")
        void enrichesInlineAndCopiesFields() {
            SolutionAdminRow sol = row("sol-1", 100L, "user-1", "Binary search explainer",
                    "## Walkthrough", true, true);

            AdminUserSummary author = new AdminUserSummary(
                    "user-1", "alice", "role1", "Alice", "avatar1", "alice@example.com");

            ProblemAdminRowDTO problem = new ProblemAdminRowDTO(
                    100L, "binary-search", "Binary Search", "easy", null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null);

            when(solutionAdminReadPort.getById("sol-1")).thenReturn(sol);
            when(userEnricher.enrichOne("user-1")).thenReturn(author);
            when(problemReadPort.findProblem(100L)).thenReturn(problem);

            AdminSolutionVO vo = projection.getSolution("sol-1");

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo("sol-1");
            assertThat(vo.getTitle()).isEqualTo("Binary search explainer");
            assertThat(vo.getIsFlagged()).isTrue();
            assertThat(vo.getIsPublished()).isTrue();
            assertThat(vo.getAuthor()).isNotNull();
            assertThat(vo.getAuthor().getUsername()).isEqualTo("alice");
            assertThat(vo.getAuthor().getEmail()).isEqualTo("alice@example.com");
            assertThat(vo.getProblem()).isNotNull();
            assertThat(vo.getProblem().getSlug()).isEqualTo("binary-search");
            assertThat(vo.getProblem().getDifficulty()).isEqualTo("easy");
        }

        @Test
        @DisplayName("returns VO with null author / problem when enrichment rows are missing")
        void returnsVoWithNullEnrichmentWhenRowsMissing() {
            SolutionAdminRow sol = row("sol-2", 999L, "user-orphan", "Orphan", null, null, null);

            when(solutionAdminReadPort.getById("sol-2")).thenReturn(sol);
            when(userEnricher.enrichOne("user-orphan")).thenReturn(null);
            when(problemReadPort.findProblem(999L)).thenReturn(null);

            AdminSolutionVO vo = projection.getSolution("sol-2");

            assertThat(vo).isNotNull();
            assertThat(vo.getAuthor()).isNull();
            assertThat(vo.getProblem()).isNull();
        }
    }

    @Nested
    @DisplayName("getFlaggedSolutions(query) — forces isFlagged=true & isDeleted=false")
    class GetFlaggedSolutions {

        @Test
        @DisplayName("forces isDeleted=false even when caller passes isDeleted=true (BUG-Q9)")
        void forcesIsDeletedFalse() {
            AdminSolutionQueryDTO query = new AdminSolutionQueryDTO();
            query.setIsFlagged(false);
            query.setIsDeleted(true);
            query.setPage(1);
            query.setLimit(10);

            when(solutionAdminReadPort.page(any())).thenReturn(emptyPage());
            when(userEnricher.enrich(anySet())).thenReturn(Collections.emptyMap());
            when(problemReadPort.findProblemsByIds(anyCollection())).thenReturn(Collections.emptyList());

            projection.getFlaggedSolutions(query);

            ArgumentCaptor<SolutionAdminQuery> captor =
                    ArgumentCaptor.forClass(SolutionAdminQuery.class);
            verify(solutionAdminReadPort).page(captor.capture());
            // Active branch (includeDeleted=false) must be requested regardless
            // of the caller's isDeleted=true.
            assertThat(captor.getValue().includeDeleted()).isFalse();
            assertThat(captor.getValue().isFlagged()).isTrue();
        }
    }

    @Nested
    @DisplayName("getSolutions(query) — branch routing on isDeleted")
    class GetSolutionsBranchRouting {

        @Test
        @DisplayName("isDeleted=true passes includeDeleted=true (provider raw-SQL deleted branch)")
        void isDeletedTrue_routesToDeletedBranch() {
            AdminSolutionQueryDTO query = new AdminSolutionQueryDTO();
            query.setIsDeleted(true);
            query.setPage(1);
            query.setLimit(10);

            when(solutionAdminReadPort.page(any())).thenReturn(emptyPage());
            when(userEnricher.enrich(anySet())).thenReturn(Collections.emptyMap());
            when(problemReadPort.findProblemsByIds(anyCollection())).thenReturn(Collections.emptyList());

            PageResult<AdminSolutionListItemVO> result = projection.getSolutions(query);

            assertThat(result).isNotNull();
            assertThat(result.getItems()).isEmpty();

            ArgumentCaptor<SolutionAdminQuery> captor =
                    ArgumentCaptor.forClass(SolutionAdminQuery.class);
            verify(solutionAdminReadPort).page(captor.capture());
            assertThat(captor.getValue().includeDeleted()).isTrue();
        }

        @Test
        @DisplayName("isDeleted unset passes includeDeleted=false (provider active wrapper branch)")
        void isDeletedUnset_routesToActiveBranch() {
            AdminSolutionQueryDTO query = new AdminSolutionQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            when(solutionAdminReadPort.page(any())).thenReturn(emptyPage());
            when(userEnricher.enrich(anySet())).thenReturn(Collections.emptyMap());
            when(problemReadPort.findProblemsByIds(anyCollection())).thenReturn(Collections.emptyList());

            PageResult<AdminSolutionListItemVO> result = projection.getSolutions(query);

            assertThat(result).isNotNull();
            assertThat(result.getItems()).isEmpty();

            ArgumentCaptor<SolutionAdminQuery> captor =
                    ArgumentCaptor.forClass(SolutionAdminQuery.class);
            verify(solutionAdminReadPort).page(captor.capture());
            assertThat(captor.getValue().includeDeleted()).isFalse();
        }

        @Test
        void nullRowsAreTypedAsUnavailable() {
            AdminSolutionQueryDTO query = new AdminSolutionQueryDTO();
            query.setPage(1);
            query.setLimit(10);
            when(solutionAdminReadPort.page(any()))
                    .thenReturn(new SolutionAdminPage(
                            Collections.<SolutionAdminRow>singletonList(null), 1L));

            assertThatThrownBy(() -> projection.getSolutions(query))
                    .extracting("errorCode")
                    .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE);
        }

        @Test
        @DisplayName("empty result page skips batch-load calls (no useless selectBatchIds)")
        void emptyPage_skipsBatchLoads() {
            AdminSolutionQueryDTO query = new AdminSolutionQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            when(solutionAdminReadPort.page(any())).thenReturn(emptyPage());

            projection.getSolutions(query);

            verify(userEnricher, never()).enrich(anySet());
            verify(problemReadPort, never()).findProblemsByIds(anyCollection());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static SolutionAdminPage emptyPage() {
        return new SolutionAdminPage(List.of(), 0L);
    }

    private static SolutionAdminRow row(String id, Long problemId, String userId, String title,
                                        String content, Boolean isFlagged, Boolean isPublished) {
        return new SolutionAdminRow(
                id, problemId, userId, title, content, null, null, null, null,
                isPublished, null, null, isFlagged, null, null, null, null, null, null, null);
    }
}
