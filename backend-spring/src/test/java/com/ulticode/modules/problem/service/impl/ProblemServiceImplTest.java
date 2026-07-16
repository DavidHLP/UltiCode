package com.ulticode.modules.problem.service.impl;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the single premium-access verdict ({@code enforcePremiumAccess})
 * shared by {@code getProblemById} / {@code getProblemBySlug}. The guard
 * behaviour is identical for both entry points; exercising {@code getProblemById}
 * proves the shared helper.
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
        problem.setIsPremium(true);
        return problem;
    }

    private Problem freeProblem() {
        Problem problem = new Problem();
        problem.setId(2L);
        problem.setIsPremium(false);
        return problem;
    }

    @Test
    @DisplayName("premium problem is returned to an admin caller")
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
    @DisplayName("premium problem is refused for a non-admin caller")
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
    @DisplayName("non-premium problem bypasses the admin guard entirely")
    void getProblemById_allowsAnyCallerForFreeProblem() {
        Problem problem = freeProblem();
        ProblemVO vo = new ProblemVO();
        when(problemMapper.selectById(2L)).thenReturn(problem);
        when(problemProjection.toVO(problem)).thenReturn(vo);

        assertThat(problemService.getProblemById(2L)).isSameAs(vo);
        verify(currentUserProvider, never()).hasRole("ADMIN");
    }
}
