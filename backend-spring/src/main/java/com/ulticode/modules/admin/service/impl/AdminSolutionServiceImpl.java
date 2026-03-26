package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminSolutionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSolutionVO;
import com.ulticode.modules.admin.service.AdminSolutionService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of AdminSolutionService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSolutionServiceImpl implements AdminSolutionService {

    private final SolutionMapper solutionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;

    @Override
    public PageResult<AdminSolutionVO> getSolutions(AdminSolutionQueryDTO query) {
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        LambdaQueryWrapper<Solution> wrapper = new LambdaQueryWrapper<>();

        // Search filter (title or content)
        if (StringUtils.hasText(query.getSearch())) {
            String search = "%" + query.getSearch() + "%";
            wrapper.and(w -> w
                    .like(Solution::getTitle, search)
                    .or()
                    .like(Solution::getContent, search));
        }

        // Problem ID filter
        if (query.getProblemId() != null) {
            wrapper.eq(Solution::getProblemId, query.getProblemId());
        }

        // User ID filter
        if (StringUtils.hasText(query.getUserId())) {
            wrapper.eq(Solution::getUserId, query.getUserId());
        }

        // Flagged filter
        if (query.getIsFlagged() != null) {
            wrapper.eq(Solution::getIsFlagged, query.getIsFlagged());
        }

        // Published filter
        if (query.getIsPublished() != null) {
            wrapper.eq(Solution::getIsPublished, query.getIsPublished());
        }

        // Deleted filter (exclude soft-deleted by default)
        if (query.getIsDeleted() != null && query.getIsDeleted()) {
            // Include soft-deleted - need to use native query or disable logic delete
            // For now, we'll just not filter and let MyBatis-Plus handle it
        }
        // By default, MyBatis-Plus @TableLogic excludes soft-deleted records

        // Sorting
        boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        switch (sortBy) {
            case "title" -> wrapper.orderBy(true, isAsc, Solution::getTitle);
            case "views" -> wrapper.orderBy(true, isAsc, Solution::getViews);
            case "createdAt" -> wrapper.orderBy(true, isAsc, Solution::getCreatedAt);
            case "updatedAt" -> wrapper.orderBy(true, isAsc, Solution::getUpdatedAt);
            default -> wrapper.orderBy(true, isAsc, Solution::getCreatedAt);
        }

        Page<Solution> pageResult = new Page<>(page, limit);
        Page<Solution> result = solutionMapper.selectPage(pageResult, wrapper);

        List<AdminSolutionVO> voList = result.getRecords().stream()
                .map(this::toAdminVO)
                .toList();

        return PageResult.of(voList, result.getTotal(), page, limit);
    }

    @Override
    public PageResult<AdminSolutionVO> getFlaggedSolutions(AdminSolutionQueryDTO query) {
        // Force isFlagged = true for this endpoint
        query.setIsFlagged(true);
        return getSolutions(query);
    }

    @Override
    public AdminSolutionVO getSolution(String id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }
        return toAdminVO(solution);
    }

    @Override
    @Transactional
    public AdminSolutionVO flagSolution(String id, String reason, String adminId) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }

        LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Solution::getId, id)
                .set(Solution::getIsFlagged, true)
                .set(Solution::getFlaggedReason, reason)
                .set(Solution::getFlaggedAt, LocalDateTime.now());

        solutionMapper.update(null, wrapper);

        log.info("Solution flagged: {} by admin {}, reason: {}", id, adminId, reason);

        return getSolution(id);
    }

    @Override
    @Transactional
    public AdminSolutionVO unflagSolution(String id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }

        LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Solution::getId, id)
                .set(Solution::getIsFlagged, false)
                .set(Solution::getFlaggedReason, null)
                .set(Solution::getFlaggedAt, null);

        solutionMapper.update(null, wrapper);

        log.info("Solution unflagged: {}", id);

        return getSolution(id);
    }

    @Override
    @Transactional
    public void deleteSolution(String id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }

        // Hard delete (not soft delete via @TableLogic)
        solutionMapper.deleteById(id);

        // Check if there are remaining solutions for this problem
        LambdaQueryWrapper<Solution> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Solution::getProblemId, solution.getProblemId());
        long remainingCount = solutionMapper.selectCount(countWrapper);

        if (remainingCount == 0) {
            // Update problem's hasSolution flag
            Problem problem = problemMapper.selectById(solution.getProblemId());
            if (problem != null && Boolean.TRUE.equals(problem.getHasSolution())) {
                problem.setHasSolution(false);
                problemMapper.updateById(problem);
            }
        }

        log.info("Solution deleted: {}", id);
    }

    @Override
    @Transactional
    public List<BulkActionResult> bulkAction(List<String> ids, String action) {
        List<BulkActionResult> results = new ArrayList<>();

        for (String id : ids) {
            try {
                switch (action) {
                    case "publish" -> {
                        LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
                        wrapper.eq(Solution::getId, id)
                                .set(Solution::getIsPublished, true)
                                .set(Solution::getPublishedAt, LocalDateTime.now());
                        solutionMapper.update(null, wrapper);
                        results.add(BulkActionResult.success(id));
                    }
                    case "unpublish" -> {
                        LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
                        wrapper.eq(Solution::getId, id)
                                .set(Solution::getIsPublished, false)
                                .set(Solution::getPublishedAt, (LocalDateTime) null);
                        solutionMapper.update(null, wrapper);
                        results.add(BulkActionResult.success(id));
                    }
                    case "delete" -> {
                        deleteSolution(id);
                        results.add(BulkActionResult.success(id));
                    }
                    case "unflag" -> {
                        unflagSolution(id);
                        results.add(BulkActionResult.success(id));
                    }
                    default -> {
                        results.add(BulkActionResult.failure(id, "Unknown action: " + action));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to perform action {} on solution {}: {}", action, id, e.getMessage());
                results.add(BulkActionResult.failure(id, e.getMessage()));
            }
        }

        return results;
    }

    /**
     * Convert Solution entity to AdminSolutionVO with nested author and problem info.
     */
    private AdminSolutionVO toAdminVO(Solution solution) {
        if (solution == null) {
            return null;
        }

        AdminSolutionVO vo = new AdminSolutionVO();
        vo.setId(solution.getId());
        vo.setProblemId(solution.getProblemId());
        vo.setUserId(solution.getUserId());
        vo.setTitle(solution.getTitle());
        vo.setContent(solution.getContent());
        vo.setSummary(solution.getSummary());
        vo.setLanguage(solution.getLanguage());
        vo.setTags(solution.getTags());
        vo.setViews(solution.getViews());
        vo.setIsPublished(solution.getIsPublished());
        vo.setPublishedAt(solution.getPublishedAt());
        vo.setPublishedBy(solution.getPublishedBy());
        vo.setIsFlagged(solution.getIsFlagged());
        vo.setFlaggedReason(solution.getFlaggedReason());
        vo.setFlaggedAt(solution.getFlaggedAt());
        vo.setIsDeleted(solution.getIsDeleted());
        vo.setDeletedAt(solution.getDeletedAt());
        vo.setDeletedBy(solution.getDeletedBy());
        vo.setCreatedAt(solution.getCreatedAt());
        vo.setUpdatedAt(solution.getUpdatedAt());

        // Fetch author info
        User author = userMapper.selectById(solution.getUserId());
        if (author != null) {
            AdminSolutionVO.AuthorInfo authorInfo = new AdminSolutionVO.AuthorInfo();
            authorInfo.setId(author.getId());
            authorInfo.setUsername(author.getUsername());
            authorInfo.setName(author.getName());
            authorInfo.setEmail(author.getEmail());
            vo.setAuthor(authorInfo);
        }

        // Fetch problem info
        Problem problem = problemMapper.selectById(solution.getProblemId());
        if (problem != null) {
            AdminSolutionVO.ProblemInfo problemInfo = new AdminSolutionVO.ProblemInfo();
            problemInfo.setId(problem.getId().toString());
            problemInfo.setSlug(problem.getSlug());
            problemInfo.setTitle(problem.getTitle());
            problemInfo.setDifficulty(problem.getDifficulty());
            vo.setProblem(problemInfo);
        }

        return vo;
    }
}
