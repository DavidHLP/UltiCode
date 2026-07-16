package com.ulticode.modules.problem.projection;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.edgeoperations.inspector.EdgeOperationInspector;
import com.ulticode.modules.problem.dto.AdjacentProblemsVO;
import com.ulticode.modules.problem.dto.ProblemDetailAdminVO;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemDetailMapper;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.problem.mapper.ProblemLanguageMapper;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Unit tests for {@link DefaultProblemProjection}, the deep module that owns
 * problem entity-to-VO projection and read-side aggregation.
 *
 * <p>The state-change paths in {@code ProblemServiceImpl} used to mock every
 * collaborator (submission mapper, solution mapper, edge-operations service,
 * tag mappers, detail mapper) just to exercise {@code toVO}. After the seam
 * extraction, the projection is tested here with a single mock surface, and
 * the state-machine tests no longer need those collaborators.
 *
 * <p>An impartial Jackson {@link ObjectMapper} is wired in (not a mock) so the
 * JSON-array parsing paths in {@code buildDetailData} / {@code buildExamples}
 * run for real.
 *
 * @author ulticode
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultProblemProjection — problem read-side deep module")
class DefaultProblemProjectionTest {

    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private ProblemDetailMapper problemDetailMapper;
    @Mock
    private ProblemExampleMapper problemExampleMapper;
    @Mock
    private ProblemLanguageMapper problemLanguageMapper;
    @Mock
    private ProblemTagMapper problemTagMapper;
    @Mock
    private ProblemTagRelationMapper problemTagRelationMapper;
    @Mock
    private SubmissionMapper submissionMapper;
    @Mock
    private SolutionMapper solutionMapper;
    @Mock
    private EdgeOperationInspector edgeOperationsService;
    @Mock
    
    private CurrentUserProvider currentUserProvider;
    private EdgeOperationMapper edgeOperationMapper;

    private DefaultProblemProjection projection;

    @BeforeEach
    void setUp() {
        // Real ObjectMapper so parseJsonArray / buildExamples JSON paths are exercised.
        projection = new DefaultProblemProjection(
                problemMapper, problemDetailMapper, problemExampleMapper,
                problemLanguageMapper, problemTagMapper, problemTagRelationMapper,
                submissionMapper, solutionMapper, edgeOperationsService,
                edgeOperationMapper, new ObjectMapper(), currentUserProvider,
                new com.ulticode.modules.submission.port.DefaultJudgingLanguageSupport());
    }

    // ------------------------------------------------------------------
    // toVO projection
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("toVO(entity → VO)")
    class ToVOTests {

        @Test
        @DisplayName("null entity → null VO")
        void toVO_whenNull_returnsNull() {
            assertThat(projection.toVO(null)).isNull();
        }

        @Test
        @DisplayName("maps every field and upper-cases difficulty")
        void toVO_mapsAllFieldsAndUppercasesDifficulty() {
            Problem problem = baseProblem(1L, "two-sum", "Two Sum", "easy");

            ProblemVO vo = projection.toVO(problem);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(1L);
            assertThat(vo.getSlug()).isEqualTo("two-sum");
            assertThat(vo.getTitle()).isEqualTo("Two Sum");
            assertThat(vo.getDifficulty()).isEqualTo("EASY"); // upper-cased
            assertThat(vo.getIsPremium()).isFalse();
            assertThat(vo.getIsPublished()).isTrue();
            assertThat(vo.getSubmissionCount()).isZero(); // empty maps → 0
            assertThat(vo.getSolutionCount()).isZero();
            assertThat(vo.getTags()).isEmpty();
        }

        @Test
        @DisplayName("null difficulty stays null (no NPE in upper-case)")
        void toVO_nullDifficulty_staysNull() {
            Problem problem = baseProblem(2L, "null-d", "Null Diff", null);

            ProblemVO vo = projection.toVO(problem);

            assertThat(vo.getDifficulty()).isNull();
        }

        @Test
        @DisplayName("uses pre-loaded counts and tags from the maps")
        void toVO_usesCountsAndTagsFromPreloadedMaps() {
            Problem problem = baseProblem(3L, "three-sum", "Three Sum", "medium");
            ProblemVO.ProblemTagVO tag = new ProblemVO.ProblemTagVO();
            tag.setId("tag-array");
            tag.setLabel("数组");

            ProblemVO vo = projection.toVO(problem,
                    Map.of(3L, List.of(tag)),
                    Map.of(3L, 42L),
                    Map.of(3L, 7L));

            assertThat(vo.getSubmissionCount()).isEqualTo(42L);
            assertThat(vo.getSolutionCount()).isEqualTo(7L);
            assertThat(vo.getTags()).hasSize(1);
            assertThat(vo.getTags().get(0).getLabel()).isEqualTo("数组");
        }
    }

    // ------------------------------------------------------------------
    // Detail endpoints — existence check
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("detail endpoints — existence guard")
    class DetailExistenceTests {

        @Test
        @DisplayName("publicDetailById: missing id throws PROBLEM_NOT_FOUND")
        void publicDetailById_whenNotFound_throws() {
            when(problemMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> projection.publicDetailById(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.PROBLEM_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("publicDetailBySlug: blank slug throws PROBLEM_NOT_FOUND")
        void publicDetailBySlug_blankSlug_throws() {
            assertThatThrownBy(() -> projection.publicDetailBySlug("   "))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("adminDetailById: missing id throws PROBLEM_NOT_FOUND")
        void adminDetailById_whenNotFound_throws() {
            when(problemMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> projection.adminDetailById(99L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("adminDetailById: existing problem carries admin-only fields")
        void adminDetailById_carriesAdminFields() {
            Problem problem = baseProblem(5L, "admin", "Admin", "hard");
            problem.setIsFlagged(true);
            problem.setFlagReason("spam");
            when(problemMapper.selectById(5L)).thenReturn(problem);
            when(problemTagRelationMapper.findTagIdsByProblemId(5L)).thenReturn(List.of());

            ProblemDetailAdminVO vo = projection.adminDetailById(5L);

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(5L);
            assertThat(vo.getDifficulty()).isEqualTo("HARD");
            assertThat(vo.getIsFlagged()).isTrue();
            assertThat(vo.getFlagReason()).isEqualTo("spam");
        }
    }

    // ------------------------------------------------------------------
    // listProblems
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("listProblems (query + project)")
    class ListProblemsTests {

        @Test
        @DisplayName("normal path: paginates and projects the page")
        void listProblems_normalPath_returnsPaginatedVOs() {
            Problem problem = baseProblem(10L, "p", "P", "easy");
            Page<Problem> page = new Page<>(1, 20);
            page.setRecords(List.of(problem));
            page.setTotal(1);
            when(problemMapper.selectPage(any(Page.class), any())).thenReturn(page);
            // batch-fetch helpers must return empty lists (mock default is null → NPE)
            when(problemMapper.selectTagsByProblemIds(any())).thenReturn(List.of());
            when(problemMapper.countSubmissionsByProblemIds(any())).thenReturn(List.of());
            when(problemMapper.countSolutionsByProblemIds(any())).thenReturn(List.of());

            ProblemQueryDTO query = new ProblemQueryDTO();
            query.setPage(1);
            query.setPageSize(20);

            PageResult<ProblemVO> result = projection.listProblems(query);

            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getTitle()).isEqualTo("P");
        }

        @Test
        @DisplayName("caps page size at 100")
        void listProblems_capsPageSize() {
            Page<Problem> emptyPage = new Page<>(1, 100);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);
            when(problemMapper.selectPage(any(Page.class), any())).thenReturn(emptyPage);

            ProblemQueryDTO query = new ProblemQueryDTO();
            query.setPage(1);
            query.setPageSize(500); // oversized

            PageResult<ProblemVO> result = projection.listProblems(query);

            assertThat(result.getPageSize()).isEqualTo(100); // capped
        }

        @Test
        @DisplayName("ARCHIVED branch routes to the deleted-problems mapper")
        void listProblems_archivedPath_usesDeletedProblemsMapper() {
            Problem deleted = baseProblem(11L, "del", "Deleted", "easy");
            when(problemMapper.selectDeletedProblems(any(), anyInt(), anyInt())).thenReturn(List.of(deleted));
            when(problemMapper.countDeletedProblems(any())).thenReturn(1L);

            ProblemQueryDTO query = new ProblemQueryDTO();
            query.setPublishStatus("ARCHIVED");

            PageResult<ProblemVO> result = projection.listProblems(query);

            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getSlug()).isEqualTo("del");
        }
    }

    // ------------------------------------------------------------------
    // adjacentProblems
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("adjacentProblems (prev/next navigation)")
    class AdjacentProblemsTests {

        @Test
        @DisplayName("missing id throws PROBLEM_NOT_FOUND")
        void adjacentProblems_whenNotFound_throws() {
            when(problemMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

            assertThatThrownBy(() -> projection.adjacentProblems(404L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.PROBLEM_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("returns prev/next slugs when neighbours exist")
        void adjacentProblems_whenFound_returnsPrevNextSlugs() {
            when(problemMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
            Problem prev = baseProblem(8L, "prev-slug", "Prev", "easy");
            Problem next = baseProblem(12L, "next-slug", "Next", "easy");
            when(problemMapper.selectOne(any(Wrapper.class))).thenReturn(prev, next);

            AdjacentProblemsVO result = projection.adjacentProblems(10L);

            assertThat(result.getPrev()).isEqualTo("prev-slug");
            assertThat(result.getNext()).isEqualTo("next-slug");
        }

        @Test
        @DisplayName("returns null slugs at the boundary (no neighbour)")
        void adjacentProblems_atBoundary_returnsNullSlugs() {
            when(problemMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
            when(problemMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            AdjacentProblemsVO result = projection.adjacentProblems(10L);

            assertThat(result.getPrev()).isNull();
            assertThat(result.getNext()).isNull();
        }
    }

    // ------------------------------------------------------------------
    // findRandomPublished
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findRandomPublished")
    class FindRandomPublishedTests {

        @Test
        @DisplayName("no published problems throws PROBLEM_NOT_FOUND")
        void findRandomPublished_whenNonePublished_throws() {
            when(problemMapper.selectOne(any(Wrapper.class))).thenReturn(null);

            // BusinessException(PROBLEM_NOT_FOUND, "No published problems available") —
            // the custom message overrides the code's default, so assert the type + key text.
            assertThatThrownBy(() -> projection.findRandomPublished())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No published problems available");
        }

        @Test
        @DisplayName("found problem is enriched with tags and counts")
        void findRandomPublished_whenFound_enrichedWithTagsAndCounts() {
            Problem problem = baseProblem(21L, "rand", "Random", "medium");
            when(problemMapper.selectOne(any(Wrapper.class))).thenReturn(problem);
            // batch-fetch helpers return empty → defaults applied (no NPE)
            when(problemMapper.selectTagsByProblemIds(any())).thenReturn(List.of());
            when(problemMapper.countSubmissionsByProblemIds(any())).thenReturn(List.of());
            when(problemMapper.countSolutionsByProblemIds(any())).thenReturn(List.of());

            ProblemVO vo = projection.findRandomPublished();

            assertThat(vo).isNotNull();
            assertThat(vo.getId()).isEqualTo(21L);
            assertThat(vo.getTags()).isEmpty();
            assertThat(vo.getSubmissionCount()).isZero();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Build a minimal-but-complete {@link Problem} for projection tests.
     */
    private static Problem baseProblem(Long id, String slug, String title, String difficulty) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setSlug(slug);
        problem.setTitle(title);
        problem.setDifficulty(difficulty);
        problem.setIsPremium(false);
        problem.setIsPublished(true);
        problem.setHasSolution(false);
        problem.setAcceptanceRate(BigDecimal.ZERO);
        problem.setIsFlagged(false);
        problem.setIsDeleted(false);
        problem.setCreatedAt(LocalDateTime.now());
        problem.setUpdatedAt(LocalDateTime.now());
        return problem;
    }
}
