package com.ulticode.modules.problem.service.impl;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.ProblemErrorCode;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.service.ProblemAdministrationDomainService;
import com.ulticode.modules.problem.service.ProblemIndexRefresher;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProblemServiceImplTest {

    private static final String ACTOR_ID = "actor-99";
    @Mock private ProblemAdministrationDomainService domainService;
    @Mock private ProblemMapper problemMapper;
    @Mock private ProblemProjection problemProjection;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ProblemIndexRefresher indexRefresher;

    private ProblemServiceImpl problemService;

    @BeforeEach
    void setUp() {
        problemService = new ProblemServiceImpl(
                domainService,
                problemMapper,
                problemProjection,
                currentUserProvider,
                indexRefresher
        );
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Problem problem(Long id, String slug) {
        Problem p = new Problem();
        p.setId(id);
        p.setSlug(slug);
        p.setTitle("Test Problem");
        p.setDifficulty("Easy");
        p.setIsPremium(false);
        p.setIsPublished(false);
        p.setStatus("todo");
        p.setHasSolution(false);
        p.setAcceptanceRate(BigDecimal.ZERO);
        p.setIsFlagged(false);
        p.setIsDeleted(false);
        p.setVersion(1);
        return p;
    }

    // ── read tests — verbatim from original ProblemServiceImplTest ──────────

    @Nested
    class PremiumAccessTests {

        private Problem premiumProblem() {
            Problem p = problem(1L, "premium-slug");
            p.setIsPremium(true);
            return p;
        }

        private Problem freeProblem() {
            Problem p = problem(2L, "free-slug");
            p.setIsPremium(false);
            return p;
        }

        @Test
        @DisplayName("premium problem is returned to an admin caller (by id)")
        void getProblemById_allowsAdminForPremium() {
            Problem p = premiumProblem();
            ProblemVO vo = new ProblemVO();
            when(problemMapper.selectById(1L)).thenReturn(p);
            when(currentUserProvider.hasRole("ADMIN")).thenReturn(true);
            when(problemProjection.toVO(p)).thenReturn(vo);

            assertThat(problemService.getProblemById(1L)).isSameAs(vo);
            verify(problemProjection).toVO(p);
        }

        @Test
        @DisplayName("premium problem is refused for a non-admin caller (by id)")
        void getProblemById_refusesNonAdminForPremium() {
            Problem p = premiumProblem();
            when(problemMapper.selectById(1L)).thenReturn(p);
            when(currentUserProvider.hasRole("ADMIN")).thenReturn(false);
            when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);

            assertThatThrownBy(() -> problemService.getProblemById(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemErrorCode.PROBLEM_PREMIUM_REQUIRED));
            verify(problemProjection, never()).toVO(p);
        }

        @Test
        @DisplayName("non-premium problem bypasses the admin guard entirely (by id)")
        void getProblemById_allowsAnyCallerForFreeProblem() {
            Problem p = freeProblem();
            ProblemVO vo = new ProblemVO();
            when(problemMapper.selectById(2L)).thenReturn(p);
            when(problemProjection.toVO(p)).thenReturn(vo);

            assertThat(problemService.getProblemById(2L)).isSameAs(vo);
            verify(currentUserProvider, never()).hasRole("ADMIN");
        }

        @Test
        @DisplayName("premium problem is returned to an admin caller (by slug)")
        void getProblemBySlug_allowsAdminForPremium() {
            Problem p = premiumProblem();
            ProblemVO vo = new ProblemVO();
            when(problemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
            when(currentUserProvider.hasRole("ADMIN")).thenReturn(true);
            when(problemProjection.toVO(p)).thenReturn(vo);

            assertThat(problemService.getProblemBySlug("premium-slug")).isSameAs(vo);
            verify(problemProjection).toVO(p);
        }

        @Test
        @DisplayName("premium problem is refused for a non-admin caller (by slug)")
        void getProblemBySlug_refusesNonAdminForPremium() {
            Problem p = premiumProblem();
            when(problemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
            when(currentUserProvider.hasRole("ADMIN")).thenReturn(false);
            when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);

            assertThatThrownBy(() -> problemService.getProblemBySlug("premium-slug"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ProblemErrorCode.PROBLEM_PREMIUM_REQUIRED));
            verify(problemProjection, never()).toVO(p);
        }

        @Test
        @DisplayName("non-premium problem bypasses the admin guard entirely (by slug)")
        void getProblemBySlug_allowsAnyCallerForFreeProblem() {
            Problem p = freeProblem();
            ProblemVO vo = new ProblemVO();
            when(problemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
            when(problemProjection.toVO(p)).thenReturn(vo);

            assertThat(problemService.getProblemBySlug("free-slug")).isSameAs(vo);
            verify(currentUserProvider, never()).hasRole("ADMIN");
        }
    }

    // ── write delegation tests ──────────────────────────────────────────────

    @Nested
    class WriteDelegationTests {

        @Test
        @DisplayName("createProblem: delegates to the injected domain service and refreshes the index")
        void createProblem_delegatesAndProjects() {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug("new-slug");
            dto.setTitle("New Title");
            dto.setDifficulty("Medium");

            Problem inserted = problem(1L, "new-slug");
            ProblemVO vo = new ProblemVO();
            vo.setId(1L);
            vo.setSlug("new-slug");

            lenient().when(currentUserProvider.getCurrentUserId()).thenReturn(ACTOR_ID);
            when(domainService.createProblem(dto, ACTOR_ID)).thenReturn(inserted);
            lenient().when(problemProjection.toVO(any(Problem.class))).thenReturn(vo);

            ProblemVO result = problemService.createProblem(dto);

            assertThat(result.getSlug()).isEqualTo("new-slug");
            verify(domainService).createProblem(dto, ACTOR_ID);
            verify(indexRefresher).publish(inserted);
            verify(problemProjection).toVO(any(Problem.class));
        }

        @Test
        @DisplayName("updateProblem: reads the current version and uses the fenced domain method")
        void updateProblem_delegatesAndProjects() {
            Problem existing = problem(1L, "two-sum");
            existing.setTitle("Old Title");
            ProblemVO vo = new ProblemVO();
            vo.setId(1L);

            UpdateProblemDTO dto = new UpdateProblemDTO();
            dto.setTitle("Updated Title");

            when(currentUserProvider.getCurrentUserId()).thenReturn(ACTOR_ID);
            when(problemMapper.selectById(1L)).thenReturn(existing);
            when(domainService.updateProblem(1L, dto, ACTOR_ID, 1L)).thenReturn(existing);
            lenient().when(problemProjection.toVO(any(Problem.class))).thenReturn(vo);

            ProblemVO result = problemService.updateProblem(1L, dto);

            assertThat(result.getId()).isEqualTo(1L);
            verify(domainService).updateProblem(1L, dto, ACTOR_ID, 1L);
            verify(indexRefresher).publish(existing);
            verify(problemProjection).toVO(any(Problem.class));
        }

        @Test
        @DisplayName("deleteProblem: forwards the current version and publishes a tombstone")
        void deleteProblem_delegates() {
            Problem existing = problem(1L, "two-sum");

            when(currentUserProvider.getCurrentUserId()).thenReturn(ACTOR_ID);
            when(problemMapper.selectById(1L)).thenReturn(existing);

            problemService.deleteProblem(1L);

            verify(domainService).deleteProblem(1L, ACTOR_ID, 1L);
            assertThat(existing.getIsDeleted()).isTrue();
            verify(indexRefresher).publish(existing);
        }

        @Test
        @DisplayName("publishProblem: forwards the current version and refreshes the index")
        void publishProblem_delegatesAndProjects() {
            Problem existing = problem(1L, "two-sum");
            existing.setIsPublished(false);
            ProblemVO vo = new ProblemVO();
            vo.setId(1L);

            when(currentUserProvider.getCurrentUserId()).thenReturn(ACTOR_ID);
            when(problemMapper.selectById(1L)).thenReturn(existing);
            when(domainService.publishProblem(1L, ACTOR_ID, 1L)).thenReturn(existing);
            lenient().when(problemProjection.toVO(any(Problem.class))).thenReturn(vo);

            ProblemVO result = problemService.publishProblem(1L);

            assertThat(result.getId()).isEqualTo(1L);
            verify(domainService).publishProblem(1L, ACTOR_ID, 1L);
            verify(problemProjection).toVO(any(Problem.class));
            verify(indexRefresher).publish(existing);
        }

        @Test
        @DisplayName("unpublishProblem: forwards the current version and refreshes the index")
        void unpublishProblem_delegatesAndProjects() {
            Problem existing = problem(1L, "two-sum");
            existing.setIsPublished(true);
            ProblemVO vo = new ProblemVO();
            vo.setId(1L);

            when(currentUserProvider.getCurrentUserId()).thenReturn(ACTOR_ID);
            when(problemMapper.selectById(1L)).thenReturn(existing);
            when(domainService.unpublishProblem(1L, ACTOR_ID, 1L)).thenReturn(existing);
            lenient().when(problemProjection.toVO(any(Problem.class))).thenReturn(vo);

            ProblemVO result = problemService.unpublishProblem(1L);

            assertThat(result.getId()).isEqualTo(1L);
            verify(domainService).unpublishProblem(1L, ACTOR_ID, 1L);
            verify(problemProjection).toVO(any(Problem.class));
            verify(indexRefresher).publish(existing);
        }
    }
}
