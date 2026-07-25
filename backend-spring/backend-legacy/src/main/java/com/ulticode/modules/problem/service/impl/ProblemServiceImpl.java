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
import com.ulticode.modules.problem.port.ProblemDetailPort;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.service.ProblemService;
import com.ulticode.modules.problem.service.ProblemVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * State-machine implementation of {@link ProblemService}.
 *
 * <p>This service owns the problem write surface: create / update / publish /
 * unpublish / delete, the premium-access guard on the read entry points
 * ({@code getProblemById} / {@code getProblemBySlug}), and the cross-module
 * entity lookups ({@code findById} / {@code findBySlug}) used by auth,
 * submission, contest, forum and other modules.
 *
 * <p>All entity-to-VO projection and read-side aggregation (list, detail,
 * adjacent, random, {@code toVO}) lives in {@link ProblemProjection} — see
 * that interface for why the seam exists. The detail-satellite write lifecycle
 * (problem_details row + languages + examples + tag relations) lives in
 * {@link ProblemDetailPort} — see that interface for why the write side is a
 * separate deep module. {@code toVO(Problem)} is kept on this interface as a
 * thin facade so the four cross-module callers
 * ({@code AdminProblemServiceImpl}, {@code AuthController},
 * {@code AuthSessionModule}, {@code SubmissionServiceImpl}) that already hold
 * a {@code ProblemService} reference need not be rewired.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;
    private final ProblemVersionService problemVersionService;
    private final ProblemProjection problemProjection;
    private final ProblemDetailPort problemDetailPort;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;

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

    /**
     * Single premium-access verdict shared by {@link #getProblemById} and
     * {@link #getProblemBySlug}. Premium problems require an admin role; any
     * other caller is refused with {@link ErrorCode#PROBLEM_PREMIUM_REQUIRED}.
     */
    private void enforcePremiumAccess(Problem problem) {
        if (Boolean.TRUE.equals(problem.getIsPremium())
                && !currentUserProvider.hasRole("ADMIN")
                && !currentUserProvider.hasRole("SUPER_ADMIN")) {
            throw new BusinessException(ErrorCode.PROBLEM_PREMIUM_REQUIRED);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public ProblemVO createProblem(CreateProblemDTO createDTO) {
        // Check if slug already exists
        Optional<Problem> existingProblem = findBySlug(createDTO.getSlug());
        if (existingProblem.isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Problem with this slug already exists");
        }

        Problem problem = new Problem();
        problem.setSlug(createDTO.getSlug());
        problem.setTitle(createDTO.getTitle());
        problem.setDifficulty(createDTO.getDifficulty());
        problem.setIsPremium(createDTO.getIsPremium() != null ? createDTO.getIsPremium() : false);
        problem.setIsPublished(createDTO.getIsPublished() != null ? createDTO.getIsPublished() : true);
        problem.setStatus("todo");
        problem.setHasSolution(false);
        problem.setAcceptanceRate(BigDecimal.ZERO);
        problem.setIsFlagged(false);
        problem.setIsDeleted(false);
        problem.setVersion(1);

        // Set published info if publishing
        if (Boolean.TRUE.equals(problem.getIsPublished())) {
            problem.setPublishedAt(LocalDateTime.now(clock));
            problem.setPublishedBy(currentUserProvider.getCurrentUserId());
        }

        problemMapper.insert(problem);

        String operatorId = currentUserProvider.getCurrentUserId();
        problemVersionService.createInitialVersion(problem.getId(), operatorId);

        log.info("Problem created: {} by user {}", problem.getId(), operatorId);
        return toVO(problem);
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public ProblemVO updateProblem(Long id, UpdateProblemDTO updateDTO) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Update fields from DTO (only non-null fields)
        if (updateDTO.getSlug() != null && !updateDTO.getSlug().equals(problem.getSlug())) {
            // Check if new slug already exists
            Optional<Problem> existingProblem = findBySlug(updateDTO.getSlug());
            if (existingProblem.isPresent() && !existingProblem.get().getId().equals(id)) {
                throw new BusinessException(ErrorCode.CONFLICT, "Problem with this slug already exists");
            }
            problem.setSlug(updateDTO.getSlug());
        }
        if (updateDTO.getTitle() != null) {
            problem.setTitle(updateDTO.getTitle());
        }
        if (updateDTO.getDifficulty() != null) {
            problem.setDifficulty(updateDTO.getDifficulty());
        }
        if (updateDTO.getIsPremium() != null) {
            problem.setIsPremium(updateDTO.getIsPremium());
        }
        if (updateDTO.getIsPublished() != null) {
            problem.setIsPublished(updateDTO.getIsPublished());
            // Set published info if publishing for the first time
            if (Boolean.TRUE.equals(updateDTO.getIsPublished()) && problem.getPublishedAt() == null) {
            problem.setPublishedAt(LocalDateTime.now(clock));
                problem.setPublishedBy(currentUserProvider.getCurrentUserId());
            }
        }
        if (updateDTO.getHasSolution() != null) {
            problem.setHasSolution(updateDTO.getHasSolution());
        }

        problemMapper.updateById(problem);

        // Delegate detail-satellite writes (problem_details + languages + examples + tags)
        // to the ProblemDetailPort deep module — see its javadoc for why the write side
        // is a separate seam.
        problemDetailPort.applyDetailUpdate(id, problem, updateDTO);

        String operatorId = currentUserProvider.getCurrentUserId();
        problemVersionService.createVersion(id, "UPDATE", null, operatorId);

        log.info("Problem updated: {} by user {}", id, operatorId);
        return toVO(problem);
    }

    @Override
    @Transactional
    @CacheEvict(value = "problem", allEntries = true)
    public void deleteProblem(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Soft delete is handled by MyBatis-Plus @TableLogic
        problemMapper.deleteById(id);

        log.info("Problem deleted: {} by user {}", id, currentUserProvider.getCurrentUserId());
    }

    @Override
    @Transactional
    public ProblemVO publishProblem(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        problem.setIsPublished(true);
        if (problem.getPublishedAt() == null) {
            problem.setPublishedAt(LocalDateTime.now(clock));
            problem.setPublishedBy(currentUserProvider.getCurrentUserId());
        }

        problemMapper.updateById(problem);

        log.info("Problem published: {} by user {}", id, currentUserProvider.getCurrentUserId());
        return toVO(problem);
    }

    @Override
    @Transactional
    public ProblemVO unpublishProblem(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        problem.setIsPublished(false);

        problemMapper.updateById(problem);

        log.info("Problem unpublished: {} by user {}", id, currentUserProvider.getCurrentUserId());
        return toVO(problem);
    }

    /**
     * Convert a {@code Problem} entity to a {@code ProblemVO}. Thin facade over
     * {@link ProblemProjection#toVO(Problem)} — kept on this interface because
     * four cross-module callers ({@code AdminProblemServiceImpl},
     * {@code AuthController}, {@code AuthSessionModule},
     * {@code SubmissionServiceImpl}) already hold a {@code ProblemService}
     * reference. The state-change methods below also call it to shape their
     * return value.
     */
    @Override
    public ProblemVO toVO(Problem problem) {
        return problemProjection.toVO(problem);
    }
}
