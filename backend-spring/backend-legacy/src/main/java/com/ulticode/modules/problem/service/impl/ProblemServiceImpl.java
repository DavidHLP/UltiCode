package com.ulticode.modules.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.service.ProblemService;
import com.ulticode.modules.problem.port.ProblemDetailDomainPort;
import com.ulticode.modules.problem.port.ProblemVersionPort;
import com.ulticode.modules.problem.port.ProblemWritePort;
import com.ulticode.modules.problem.service.impl.ProblemAdministrationDomainServiceImpl;
import com.ulticode.modules.problem.service.ProblemAdministrationDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * State-machine implementation of {@link ProblemService}.
 *
 * <p>Write operations (create / update / publish / unpublish / delete) are
 * delegated to the canonical {@link ProblemAdministrationDomainService} in
 * {@code backend-problem-domain}. This class is the composition root: it
 * receives all collaborators via constructor, assembles the domain service
 * with the required port adapters, provides the transaction and cache
 * boundary ({@code @Transactional} / {@code @CacheEvict}), extracts actor
 * identity from the {@link CurrentUserProvider}, and projects the returned
 * {@link Problem} entity to {@link ProblemVO} via the existing
 * {@link ProblemProjection}.
 *
 * <p>Read entry points ({@code findById}, {@code findBySlug},
 * {@code getProblemById}, {@code getProblemBySlug}) are unchanged.
 *
 * @author ulticode
 */
@Slf4j
@Service
public class ProblemServiceImpl implements ProblemService {

    private final ProblemAdministrationDomainService domainService;
    private final ProblemMapper problemMapper;
    private final ProblemProjection problemProjection;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Composition root: assembles the canonical domain service from its port
     * adapters. {@code backend-problem-domain} is a pure-POJO module — no
     * Spring annotations — so it is instantiated here in the legacy service.
     * The injected {@code clock} is passed through to the domain service so
     * tests can substitute a fixed clock.
     */
    public ProblemServiceImpl(
            ProblemMapper problemMapper,
            ProblemProjection problemProjection,
            CurrentUserProvider currentUserProvider,
            Clock clock,
            ProblemWritePort problemWritePort,
            ProblemDetailDomainPort problemDetailDomainPort,
            ProblemVersionPort problemVersionPort) {
        this.problemMapper = problemMapper;
        this.problemProjection = problemProjection;
        this.currentUserProvider = currentUserProvider;
        this.domainService = new ProblemAdministrationDomainServiceImpl(
                problemWritePort,
                problemDetailDomainPort,
                problemVersionPort,
                clock);
    }

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
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        enforcePremiumAccess(problem);
        return toVO(problem);
    }

    @Override
    public ProblemVO getProblemBySlug(String slug) {
        Problem problem = findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
        enforcePremiumAccess(problem);
        return toVO(problem);
    }

    private void enforcePremiumAccess(Problem problem) {
        if (Boolean.TRUE.equals(problem.getIsPremium())
                && !currentUserProvider.hasRole("ADMIN")
                && !currentUserProvider.hasRole("SUPER_ADMIN")) {
            throw new BusinessException(ErrorCode.PROBLEM_PREMIUM_REQUIRED);
        }
    }

    // ── write operations (delegated to domain service) ────────────────────────

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public ProblemVO createProblem(CreateProblemDTO createDTO) {
        String actorId = currentUserProvider.getCurrentUserId();
        Problem created = domainService.createProblem(createDTO, actorId);
        return toVO(created);
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public ProblemVO updateProblem(Long id, UpdateProblemDTO updateDTO) {
        String actorId = currentUserProvider.getCurrentUserId();
        Problem updated = domainService.updateProblem(id, updateDTO, actorId);
        return toVO(updated);
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public void deleteProblem(Long id) {
        String actorId = currentUserProvider.getCurrentUserId();
        domainService.deleteProblem(id, actorId);
    }

    @Override
    @Transactional
    public ProblemVO publishProblem(Long id) {
        String actorId = currentUserProvider.getCurrentUserId();
        Problem published = domainService.publishProblem(id, actorId);
        return toVO(published);
    }

    @Override
    @Transactional
    public ProblemVO unpublishProblem(Long id) {
        String actorId = currentUserProvider.getCurrentUserId();
        Problem unpublished = domainService.unpublishProblem(id, actorId);
        return toVO(unpublished);
    }

    // ── projection facade (unchanged) ─────────────────────────────────────────

    @Override
    public ProblemVO toVO(Problem problem) {
        return problemProjection.toVO(problem);
    }
}
