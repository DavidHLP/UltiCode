package com.ulticode.modules.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.ProblemErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.service.ProblemMutationModule;
import com.ulticode.modules.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Local Problem adapter. Resolves the current actor, applies the cache policy,
 * and projects the committed write result for HTTP callers.
 */
@Service
@RequiredArgsConstructor
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMutationModule mutations;
    private final ProblemMapper problemMapper;
    private final ProblemProjection problemProjection;
    private final CurrentUserProvider currentUserProvider;

    // ── read entry points (unchanged) ────────────────────────────────────────

    @Override
    public Optional<Problem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(problemMapper.selectById(id));
    }

    @Override
    public Optional<Problem> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        LambdaQueryWrapper<Problem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Problem::getSlug, slug);
        return Optional.ofNullable(problemMapper.selectOne(queryWrapper));
    }

    @Override
    @Cacheable(value = "problem", key = "'getProblemById:' + #id")
    public ProblemVO getProblemById(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ProblemErrorCode.PROBLEM_NOT_FOUND));
        enforcePremiumAccess(problem);
        return toVO(problem);
    }

    @Override
    public ProblemVO getProblemBySlug(String slug) {
        Problem problem = findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ProblemErrorCode.PROBLEM_NOT_FOUND));
        enforcePremiumAccess(problem);
        return toVO(problem);
    }

    private void enforcePremiumAccess(Problem problem) {
        if (Boolean.TRUE.equals(problem.getIsPremium())
                && !currentUserProvider.hasRole("ADMIN")
                && !currentUserProvider.hasRole("SUPER_ADMIN")) {
            throw new BusinessException(ProblemErrorCode.PROBLEM_PREMIUM_REQUIRED);
        }
    }

    @Override
    @CacheEvict(value = "problem", allEntries = true)
    public ProblemVO createProblem(CreateProblemDTO createDTO) {
        return toVO(mutations.createProblem(createDTO, currentUserProvider.getCurrentUserId()));
    }

    @Override
    @CacheEvict(value = "problem", allEntries = true)
    public ProblemVO updateProblem(Long id, UpdateProblemDTO updateDTO) {
        return toVO(mutations.updateProblem(id, updateDTO, currentUserProvider.getCurrentUserId()));
    }

    @Override
    @CacheEvict(value = "problem", allEntries = true)
    public void deleteProblem(Long id) {
        mutations.deleteProblem(id, currentUserProvider.getCurrentUserId());
    }

    @Override
    public ProblemVO publishProblem(Long id) {
        return toVO(mutations.setPublished(id, true, currentUserProvider.getCurrentUserId()));
    }

    @Override
    public ProblemVO unpublishProblem(Long id) {
        return toVO(mutations.setPublished(id, false, currentUserProvider.getCurrentUserId()));
    }

    // ── projection facade (unchanged) ─────────────────────────────────────────

    @Override
    public ProblemVO toVO(Problem problem) {
        return problemProjection.toVO(problem);
    }
}
