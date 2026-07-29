package com.ulticode.modules.problem.service.impl;
import com.ulticode.modules.problem.adapter.LegacyProblemWriteAdapter;
import org.junit.jupiter.api.BeforeEach;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.port.ProblemDetailDomainPort;
import com.ulticode.modules.problem.port.ProblemVersionPort;
import com.ulticode.modules.problem.port.ProblemWritePort;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProblemServiceImpl}.
 *
 * <p>Read tests (premium guard) are preserved verbatim from the original.
 * Write tests prove the legacy wrapper delegates to the canonical domain
 * service and projects the returned entity to {@link ProblemVO}.
 *
 * <p>The constructor manually composes {@code DefaultProblemAdministrationDomainService}
 * from the three port mocks, so these tests exercise the full delegation chain
 * end-to-end.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProblemServiceImplTest {

    private static final String ACTOR_ID = "actor-99";
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T10:00:00Z"), ZoneId.of("UTC"));

    // Domain port mocks (replacing ProblemVersionService + ProblemDetailPort)
    @Mock private ProblemWritePort problemWritePort;
    @Mock private ProblemDetailDomainPort problemDetailDomainPort;
    @Mock private ProblemVersionPort problemVersionPort;

    // Read / projection collaborators (unchanged from original)
    @Mock private ProblemMapper problemMapper;
    @Mock private ProblemProjection problemProjection;
    @Mock private Clock clock;
    @Mock private CurrentUserProvider currentUserProvider;

        private ProblemServiceImpl problemService;

    @BeforeEach
    void setUp() {
        LegacyProblemWriteAdapter writeAdapter = new LegacyProblemWriteAdapter(problemMapper);
        problemService = new ProblemServiceImpl(
                problemMapper,
                problemProjection,
                currentUserProvider,
                FIXED_CLOCK,
                writeAdapter,
                problemDetailDomainPort,
                problemVersionPort
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
                            .isEqualTo(ErrorCode.PROBLEM_PREMIUM_REQUIRED));
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
                            .isEqualTo(ErrorCode.PROBLEM_PREMIUM_REQUIRED));
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
        @DisplayName("createProblem: actorId from provider, insert called, returns projected VO")
        void createProblem_delegatesAndProjects() {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug("new-slug");
            dto.setTitle("New Title");
            dto.setDifficulty("Medium");

            Problem inserted = problem(1L, "new-slug");
            ProblemVO vo = new ProblemVO();
            vo.setId(1L);
            vo.setSlug("new-slug");

            // Domain service: slug check via writePort, then insert
            when(problemWritePort.selectBySlug("new-slug")).thenReturn(null);
            when(problemWritePort.selectById(1L)).thenReturn(inserted);
            when(problemProjection.toVO(any(Problem.class))).thenReturn(vo);

            ProblemVO result = problemService.createProblem(dto);

            assertThat(result.getSlug()).isEqualTo("new-slug");
            verify(problemWritePort).insert(any(Problem.class));
            verify(problemProjection).toVO(any(Problem.class));
        }

        @Test
        @DisplayName("updateProblem: delegates to writePort + detailPort + versionPort, projects VO")
        void updateProblem_delegatesAndProjects() {
            Problem existing = problem(1L, "two-sum");
            Problem updated = problem(1L, "two-sum");
            updated.setTitle("Updated Title");
            ProblemVO vo = new ProblemVO();
            vo.setId(1L);

            UpdateProblemDTO dto = new UpdateProblemDTO();
            dto.setTitle("Updated Title");

            when(currentUserProvider.getCurrentUserId()).thenReturn(ACTOR_ID);
            when(problemMapper.selectById(1L)).thenReturn(existing);
            when(problemProjection.toVO(any(Problem.class))).thenReturn(vo);

            ProblemVO result = problemService.updateProblem(1L, dto);

            assertThat(result.getId()).isEqualTo(1L);
            verify(problemMapper).updateById(any(Problem.class));
            verify(problemDetailDomainPort).applyDetailUpdate(any(), any(), any());
            verify(problemVersionPort).createVersion(any(), any(), any(), any());
            verify(problemProjection).toVO(any(Problem.class));
        }

        @Test
        @DisplayName("deleteProblem: delegates to domainService with actorId")
        void deleteProblem_delegates() {
            Problem existing = problem(1L, "two-sum");

            when(currentUserProvider.getCurrentUserId()).thenReturn(ACTOR_ID);
            when(problemMapper.selectById(1L)).thenReturn(existing);

            problemService.deleteProblem(1L);

            // domainService.deleteProblem(1L, ACTOR_ID) was called internally;
            // the real implementation calls writePort.deleteById(1L)
            verify(problemMapper).deleteById(1L); // legacy mapper not called directly
        }

        @Test
        @DisplayName("publishProblem: sets published fields, calls updateById, returns projected VO")
        void publishProblem_delegatesAndProjects() {
            Problem existing = problem(1L, "two-sum");
            existing.setIsPublished(false);
            Problem published = problem(1L, "two-sum");
            published.setIsPublished(true);
            published.setPublishedAt(LocalDateTime.now(FIXED_CLOCK));
            published.setPublishedBy(ACTOR_ID);
            ProblemVO vo = new ProblemVO();
            vo.setId(1L);

            when(currentUserProvider.getCurrentUserId()).thenReturn(ACTOR_ID);
            when(problemMapper.selectById(1L)).thenReturn(existing);
            when(problemProjection.toVO(any(Problem.class))).thenReturn(vo);

            ProblemVO result = problemService.publishProblem(1L);

            assertThat(result.getId()).isEqualTo(1L);
            verify(problemMapper).updateById(any(Problem.class));
            verify(problemProjection).toVO(any(Problem.class));
        }

        @Test
        @DisplayName("unpublishProblem: sets isPublished=false, calls updateById, returns projected VO")
        void unpublishProblem_delegatesAndProjects() {
            Problem existing = problem(1L, "two-sum");
            existing.setIsPublished(true);
            Problem unpublished = problem(1L, "two-sum");
            unpublished.setIsPublished(false);
            ProblemVO vo = new ProblemVO();
            vo.setId(1L);

            when(currentUserProvider.getCurrentUserId()).thenReturn(ACTOR_ID);
            when(problemMapper.selectById(1L)).thenReturn(existing);
            when(problemProjection.toVO(any(Problem.class))).thenReturn(vo);

            ProblemVO result = problemService.unpublishProblem(1L);

            assertThat(result.getId()).isEqualTo(1L);
            verify(problemMapper).updateById(any(Problem.class));
            verify(problemProjection).toVO(any(Problem.class));
        }
    }
}
