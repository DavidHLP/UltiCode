package com.ulticode.modules.admin.projection;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminSolutionListItemVO;
import com.ulticode.modules.admin.dto.AdminSolutionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSolutionVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;

import com.ulticode.modules.admin.projection.AdminUserEnricher;
import com.ulticode.modules.admin.projection.AdminUserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
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
 *   <li>{@code getSolutions(isDeleted=true)} takes the raw-SQL deleted-branch
 *       and never calls {@code selectPage}; conversely the default branch
 *       never calls {@code selectDeletedSolutions}.</li>
 * </ul>
 *
 * <p>The existing {@code AdminSolutionServiceImplTest} continues to cover the
 * four write methods ({@code flagSolution}, {@code unflagSolution},
 * {@code deleteSolution}, {@code bulkAction}) that stayed on the service.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultAdminSolutionProjection")
class AdminSolutionProjectionTest {

    @Mock private SolutionMapper solutionMapper;
    @Mock private AdminUserEnricher userEnricher;
    @Mock private ProblemMapper problemMapper;

    private DefaultAdminSolutionProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminSolutionProjection(solutionMapper, userEnricher, problemMapper);
    }

    @Nested
    @DisplayName("getSolution(id) — single-detail read")
    class GetSolution {

        @Test
        @DisplayName("throws BusinessException(SOLUTION_NOT_FOUND) when id absent")
        void throwsWhenNotFound() {
            when(solutionMapper.selectById("sol-missing")).thenReturn(null);
            assertThrows(BusinessException.class, () -> projection.getSolution("sol-missing"));
        }

        @Test
        @DisplayName("enriches author + problem inline and copies every detail field")
        void enrichesInlineAndCopiesFields() {
            Solution sol = new Solution();
            sol.setId("sol-1");
            sol.setUserId("user-1");
            sol.setProblemId(100L);
            sol.setTitle("Binary search explainer");
            sol.setContent("## Walkthrough");
            sol.setIsFlagged(true);
            sol.setIsPublished(true);

            AdminUserSummary author = new AdminUserSummary(
                    "user-1", "alice", "role1", "Alice", "avatar1", "alice@example.com");

            Problem problem = new Problem();
            problem.setId(100L);
            problem.setSlug("binary-search");
            problem.setTitle("Binary Search");
            problem.setDifficulty("easy");

            when(solutionMapper.selectById("sol-1")).thenReturn(sol);
            when(userEnricher.enrichOne("user-1")).thenReturn(author);
            when(problemMapper.selectById(100L)).thenReturn(problem);

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
            Solution sol = new Solution();
            sol.setId("sol-2");
            sol.setUserId("user-orphan");
            sol.setProblemId(999L);

            when(solutionMapper.selectById("sol-2")).thenReturn(sol);
            when(userEnricher.enrichOne("user-orphan")).thenReturn(null);
            when(problemMapper.selectById(999L)).thenReturn(null);

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

            // Active branch should be taken (isDeleted=false). selectPage must be
            // called; the deleted-branch raw-SQL methods must never be called.
            when(solutionMapper.selectPage(any(), any())).thenReturn(emptySolutionPage());
            when(userEnricher.enrich(anySet())).thenReturn(Collections.emptyMap());
            when(problemMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

            projection.getFlaggedSolutions(query);

            verify(solutionMapper).selectPage(any(), any());
            verify(solutionMapper, never()).selectDeletedSolutions(
                    any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
            verify(solutionMapper, never()).countDeletedSolutions(
                    any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getSolutions(query) — branch routing on isDeleted")
    class GetSolutionsBranchRouting {

        @Test
        @DisplayName("isDeleted=true routes to the raw-SQL deleted-branch (selectDeletedSolutions)")
        void isDeletedTrue_routesToDeletedBranch() {
            AdminSolutionQueryDTO query = new AdminSolutionQueryDTO();
            query.setIsDeleted(true);
            query.setPage(1);
            query.setLimit(10);

            when(solutionMapper.selectDeletedSolutions(
                    any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
            when(solutionMapper.countDeletedSolutions(any(), any(), any(), any(), any()))
                .thenReturn(0L);
            when(userEnricher.enrich(anySet())).thenReturn(Collections.emptyMap());
            when(problemMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

            PageResult<AdminSolutionListItemVO> result = projection.getSolutions(query);

            assertThat(result).isNotNull();
            assertThat(result.getItems()).isEmpty();
            verify(solutionMapper, never()).selectPage(any(), any());
        }

        @Test
        @DisplayName("isDeleted unset routes to the active LambdaQueryWrapper branch (selectPage)")
        void isDeletedUnset_routesToActiveBranch() {
            AdminSolutionQueryDTO query = new AdminSolutionQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            when(solutionMapper.selectPage(any(), any())).thenReturn(emptySolutionPage());
            when(userEnricher.enrich(anySet())).thenReturn(Collections.emptyMap());
            when(problemMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

            PageResult<AdminSolutionListItemVO> result = projection.getSolutions(query);

            assertThat(result).isNotNull();
            assertThat(result.getItems()).isEmpty();
            verify(solutionMapper).selectPage(any(), any());
            verify(solutionMapper, never()).selectDeletedSolutions(
                    any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("empty result page skips batch-load calls (no useless selectBatchIds)")
        void emptyPage_skipsBatchLoads() {
            AdminSolutionQueryDTO query = new AdminSolutionQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            when(solutionMapper.selectPage(any(), any())).thenReturn(emptySolutionPage());

            projection.getSolutions(query);

            verify(userEnricher, never()).enrich(anySet());
            verify(problemMapper, never()).selectBatchIds(anyCollection());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static com.baomidou.mybatisplus.extension.plugins.pagination.Page<Solution> emptySolutionPage() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Solution> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        p.setTotal(0);
        return p;
    }
}
