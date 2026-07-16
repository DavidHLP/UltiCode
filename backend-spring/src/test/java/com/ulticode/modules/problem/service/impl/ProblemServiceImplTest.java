package com.ulticode.modules.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.port.ProblemDetailPort;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.service.ProblemVersionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the single premium-access verdict ({@code enforcePremiumAccess})
 * shared by {@code getProblemById} / {@code getProblemBySlug}. The id and slug
 * entry points are exercised independently so a regression in either branch
 * is caught even though they share one verdict.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProblemServiceImplTest {

    @Mock
    private ProblemMapper problemMapper;

    @Mock
    private ProblemVersionService problemVersionService;

    @Mock
    private ProblemProjection problemProjection;

    @Mock
    private ProblemDetailPort problemDetailPort;

    @Mock
    private Clock clock;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private ProblemServiceImpl problemService;

    private Problem premiumProblem() {
        Problem problem = new Problem();
        problem.setId(1L);
        problem.setSlug("premium-slug");
        problem.setIsPremium(true);
        return problem;
    }

    private Problem freeProblem() {
        Problem problem = new Problem();
        problem.setId(2L);
        problem.setSlug("free-slug");
        problem.setIsPremium(false);
        return problem;
    }

    // --- id entry point ----------------------------------------------------

    @Test
    @DisplayName("premium problem is returned to an admin caller (by id)")
    void getProblemById_allowsAdminForPremium() {
        Problem problem = premiumProblem();
        ProblemVO vo = new ProblemVO();
        when(problemMapper.selectById(1L)).thenReturn(problem);
        when(currentUserProvider.hasRole("ADMIN")).thenReturn(true);
        when(problemProjection.toVO(problem)).thenReturn(vo);

        assertThat(problemService.getProblemById(1L)).isSameAs(vo);
        verify(problemProjection).toVO(problem);
    }

    @Test
    @DisplayName("premium problem is refused for a non-admin caller (by id)")
    void getProblemById_refusesNonAdminForPremium() {
        Problem problem = premiumProblem();
        when(problemMapper.selectById(1L)).thenReturn(problem);
        when(currentUserProvider.hasRole("ADMIN")).thenReturn(false);
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> problemService.getProblemById(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROBLEM_PREMIUM_REQUIRED));
        verify(problemProjection, never()).toVO(problem);
    }

    @Test
    @DisplayName("non-premium problem bypasses the admin guard entirely (by id)")
    void getProblemById_allowsAnyCallerForFreeProblem() {
        Problem problem = freeProblem();
        ProblemVO vo = new ProblemVO();
        when(problemMapper.selectById(2L)).thenReturn(problem);
        when(problemProjection.toVO(problem)).thenReturn(vo);

        assertThat(problemService.getProblemById(2L)).isSameAs(vo);
        verify(currentUserProvider, never()).hasRole("ADMIN");
    }

    // --- slug entry point (mirrors the id branch through the shared verdict) -

    @Test
    @DisplayName("premium problem is returned to an admin caller (by slug)")
    void getProblemBySlug_allowsAdminForPremium() {
        Problem problem = premiumProblem();
        ProblemVO vo = new ProblemVO();
        when(problemMapper.selectOne(org.mockito.ArgumentMatchers.any(LambdaQueryWrapper.class)))
                .thenReturn(problem);
        when(currentUserProvider.hasRole("ADMIN")).thenReturn(true);
        when(problemProjection.toVO(problem)).thenReturn(vo);

        assertThat(problemService.getProblemBySlug("premium-slug")).isSameAs(vo);
        verify(problemProjection).toVO(problem);
    }

    @Test
    @DisplayName("premium problem is refused for a non-admin caller (by slug)")
    void getProblemBySlug_refusesNonAdminForPremium() {
        Problem problem = premiumProblem();
        when(problemMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(problem);
        when(currentUserProvider.hasRole("ADMIN")).thenReturn(false);
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> problemService.getProblemBySlug("premium-slug"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PROBLEM_PREMIUM_REQUIRED));
        verify(problemProjection, never()).toVO(problem);
    }

    @Test
    @DisplayName("non-premium problem bypasses the admin guard entirely (by slug)")
    void getProblemBySlug_allowsAnyCallerForFreeProblem() {
        Problem problem = freeProblem();
        ProblemVO vo = new ProblemVO();
        when(problemMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(problem);
        when(problemProjection.toVO(problem)).thenReturn(vo);

        assertThat(problemService.getProblemBySlug("free-slug")).isSameAs(vo);
        verify(currentUserProvider, never()).hasRole("ADMIN");
    }
}
