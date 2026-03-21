package com.ulticode.modules.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of ProblemService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemServiceImpl implements ProblemService {

    private final ProblemMapper problemMapper;

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
    public PageResult<ProblemVO> listProblems(ProblemQueryDTO query) {
        // Set default pagination values
        int currentPage = (query.getPage() != null && query.getPage() > 0) ? query.getPage() : 1;
        int currentPageSize = (query.getPageSize() != null && query.getPageSize() > 0) ? query.getPageSize() : 20;

        // Limit page size to prevent large queries
        currentPageSize = Math.min(currentPageSize, 100);

        // Build query wrapper
        LambdaQueryWrapper<Problem> queryWrapper = new LambdaQueryWrapper<>();

        // Only show published and non-deleted problems (soft delete handled by @TableLogic)
        queryWrapper.eq(Problem::getIsPublished, true);

        // Filter by difficulty
        if (query.getDifficulty() != null && !query.getDifficulty().isBlank()) {
            queryWrapper.eq(Problem::getDifficulty, query.getDifficulty());
        }

        // Filter by status
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            queryWrapper.eq(Problem::getStatus, query.getStatus());
        }

        // Search by ID or title
        if (query.getSearch() != null && !query.getSearch().isBlank()) {
            String searchTerm = query.getSearch().trim();
            try {
                // Try to parse as ID
                Long id = Long.parseLong(searchTerm);
                queryWrapper.eq(Problem::getId, id);
            } catch (NumberFormatException e) {
                // Search by title
                queryWrapper.like(Problem::getTitle, searchTerm);
            }
        }

        // Order by ID ascending
        queryWrapper.orderByAsc(Problem::getId);

        // Execute paginated query
        Page<Problem> problemPage = new Page<>(currentPage, currentPageSize);
        Page<Problem> result = problemMapper.selectPage(problemPage, queryWrapper);

        // Convert to VO
        List<ProblemVO> problemVOList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(problemVOList, result.getTotal(), currentPage, currentPageSize);
    }

    @Override
    public ProblemVO getProblemById(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Check if problem is locked (premium and user doesn't have access)
        if (Boolean.TRUE.equals(problem.getIsPremium())) {
            if (!SecurityUtil.hasRole("ADMIN") && !SecurityUtil.hasRole("SUPER_ADMIN")) {
                // Return limited info for premium problems without access
                throw new BusinessException(ErrorCode.PROBLEM_PREMIUM_REQUIRED);
            }
        }

        return toVO(problem);
    }

    @Override
    public ProblemVO getProblemBySlug(String slug) {
        Problem problem = findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Check if problem is locked (premium and user doesn't have access)
        if (Boolean.TRUE.equals(problem.getIsPremium())) {
            if (!SecurityUtil.hasRole("ADMIN") && !SecurityUtil.hasRole("SUPER_ADMIN")) {
                throw new BusinessException(ErrorCode.PROBLEM_PREMIUM_REQUIRED);
            }
        }

        return toVO(problem);
    }

    @Override
    @Transactional
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
            problem.setPublishedAt(LocalDateTime.now());
            problem.setPublishedBy(SecurityUtil.getCurrentUserId());
        }

        problemMapper.insert(problem);

        log.info("Problem created: {} by user {}", problem.getId(), SecurityUtil.getCurrentUserId());
        return toVO(problem);
    }

    @Override
    @Transactional
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
                problem.setPublishedAt(LocalDateTime.now());
                problem.setPublishedBy(SecurityUtil.getCurrentUserId());
            }
        }
        if (updateDTO.getHasSolution() != null) {
            problem.setHasSolution(updateDTO.getHasSolution());
        }

        problemMapper.updateById(problem);

        log.info("Problem updated: {} by user {}", id, SecurityUtil.getCurrentUserId());
        return toVO(problem);
    }

    @Override
    @Transactional
    public void deleteProblem(Long id) {
        Problem problem = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));

        // Soft delete is handled by MyBatis-Plus @TableLogic
        problemMapper.deleteById(id);

        log.info("Problem deleted: {} by user {}", id, SecurityUtil.getCurrentUserId());
    }

    @Override
    public ProblemVO toVO(Problem problem) {
        if (problem == null) {
            return null;
        }

        ProblemVO vo = new ProblemVO();
        BeanUtils.copyProperties(problem, vo);
        return vo;
    }
}
