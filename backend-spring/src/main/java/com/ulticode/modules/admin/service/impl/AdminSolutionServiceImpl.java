package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminSolutionListItemVO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSolutionServiceImpl implements AdminSolutionService {

    private final SolutionMapper solutionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;

    @Override
    public PageResult<AdminSolutionListItemVO> getSolutions(AdminSolutionQueryDTO query) {
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        // When isDeleted=true, bypass MyBatis-Plus filtering via raw SQL
        if (Boolean.TRUE.equals(query.getIsDeleted())) {
            int offset = (page - 1) * limit;
            String search = StringUtils.hasText(query.getSearch()) ? query.getSearch() : null;
            String userId = StringUtils.hasText(query.getUserId()) ? query.getUserId() : null;

            boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
            String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
            String sortColumn = switch (sortBy) {
                case "title" -> "title";
                case "views" -> "views";
                case "updatedAt" -> "updated_at";
                default -> "created_at";
            };
            String sortOrder = isAsc ? "ASC" : "DESC";

            List<Solution> deletedSolutions = solutionMapper.selectDeletedSolutions(
                    search, query.getProblemId(), userId,
                    query.getIsFlagged(), query.getIsPublished(),
                    sortColumn, sortOrder, limit, offset);
            long total = solutionMapper.countDeletedSolutions(
                    search, query.getProblemId(), userId,
                    query.getIsFlagged(), query.getIsPublished());

            Set<String> userIds = deletedSolutions.stream()
                    .map(Solution::getUserId)
                    .collect(Collectors.toSet());
            Set<Long> problemIds = deletedSolutions.stream()
                    .map(Solution::getProblemId)
                    .collect(Collectors.toSet());

            Map<String, User> userMap = batchLoadUsers(userIds);
            Map<Long, Problem> problemMap = batchLoadProblems(problemIds);

            List<AdminSolutionListItemVO> voList = deletedSolutions.stream()
                    .map(s -> toListItemVO(s, userMap, problemMap))
                    .toList();

            return PageResult.of(voList, total, page, limit);
        }

        // Normal query — MyBatis-Plus auto-excludes soft-deleted via @TableLogic
        LambdaQueryWrapper<Solution> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getSearch())) {
            String search = "%" + query.getSearch() + "%";
            wrapper.and(w -> w
                    .like(Solution::getTitle, search)
                    .or()
                    .like(Solution::getContent, search));
        }

        if (query.getProblemId() != null) {
            wrapper.eq(Solution::getProblemId, query.getProblemId());
        }

        if (StringUtils.hasText(query.getUserId())) {
            wrapper.eq(Solution::getUserId, query.getUserId());
        }

        if (query.getIsFlagged() != null) {
            wrapper.eq(Solution::getIsFlagged, query.getIsFlagged());
        }

        if (query.getIsPublished() != null) {
            wrapper.eq(Solution::getIsPublished, query.getIsPublished());
        }

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

        Set<String> userIds = result.getRecords().stream()
                .map(Solution::getUserId)
                .collect(Collectors.toSet());
        Set<Long> problemIds = result.getRecords().stream()
                .map(Solution::getProblemId)
                .collect(Collectors.toSet());

        Map<String, User> userMap = batchLoadUsers(userIds);
        Map<Long, Problem> problemMap = batchLoadProblems(problemIds);

        List<AdminSolutionListItemVO> voList = result.getRecords().stream()
                .map(s -> toListItemVO(s, userMap, problemMap))
                .toList();

        return PageResult.of(voList, result.getTotal(), page, limit);
    }

    @Override
    public PageResult<AdminSolutionListItemVO> getFlaggedSolutions(AdminSolutionQueryDTO query) {
        AdminSolutionQueryDTO flaggedQuery = new AdminSolutionQueryDTO();
        flaggedQuery.setSearch(query.getSearch());
        flaggedQuery.setProblemId(query.getProblemId());
        flaggedQuery.setUserId(query.getUserId());
        flaggedQuery.setIsFlagged(true);
        flaggedQuery.setIsPublished(query.getIsPublished());
        flaggedQuery.setIsDeleted(query.getIsDeleted());
        flaggedQuery.setPage(query.getPage());
        flaggedQuery.setLimit(query.getLimit());
        flaggedQuery.setSortBy(query.getSortBy());
        flaggedQuery.setSortOrder(query.getSortOrder());
        return getSolutions(flaggedQuery);
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
    @Audited(action = AuditActionUtil.FLAG_SOLUTION, entityType = AuditActionUtil.ENTITY_SOLUTION, userIdFrom = "id")
    public AdminSolutionVO flagSolution(String id, String reason, String adminId) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of(
            "isFlagged", solution.getIsFlagged() != null ? solution.getIsFlagged() : false,
            "flaggedReason", solution.getFlaggedReason() != null ? solution.getFlaggedReason() : ""
        ));

        LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Solution::getId, id)
                .set(Solution::getIsFlagged, true)
                .set(Solution::getFlaggedReason, reason)
                .set(Solution::getFlaggedAt, LocalDateTime.now());

        solutionMapper.update(null, wrapper);

        AuditContext.setNewValues(Map.of("isFlagged", true, "flaggedReason", reason != null ? reason : ""));

        log.info("Solution flagged: {} by admin {}, reason: {}", id, adminId, reason);

        return getSolution(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.UNFLAG_SOLUTION, entityType = AuditActionUtil.ENTITY_SOLUTION, userIdFrom = "id")
    public AdminSolutionVO unflagSolution(String id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of(
            "isFlagged", solution.getIsFlagged() != null ? solution.getIsFlagged() : false,
            "flaggedReason", solution.getFlaggedReason() != null ? solution.getFlaggedReason() : ""
        ));

        LambdaUpdateWrapper<Solution> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Solution::getId, id)
                .set(Solution::getIsFlagged, false)
                .set(Solution::getFlaggedReason, null)
                .set(Solution::getFlaggedAt, null);

        solutionMapper.update(null, wrapper);

        AuditContext.setNewValues(Map.of("isFlagged", false, "flaggedReason", ""));

        log.info("Solution unflagged: {}", id);

        return getSolution(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.DELETE_SOLUTION, entityType = AuditActionUtil.ENTITY_SOLUTION, userIdFrom = "id")
    public void deleteSolution(String id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of(
            "title", solution.getTitle() != null ? solution.getTitle() : "",
            "problemId", solution.getProblemId()
        ));

        solutionMapper.deleteById(id);

        LambdaQueryWrapper<Solution> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Solution::getProblemId, solution.getProblemId());
        long remainingCount = solutionMapper.selectCount(countWrapper);

        if (remainingCount == 0) {
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
            } catch (RuntimeException e) {
                log.error("Failed to perform action {} on solution {}: {}", action, id, e.getMessage());
                results.add(BulkActionResult.failure(id, e.getMessage()));
            }
        }

        return results;
    }

    private Map<String, User> batchLoadUsers(Set<String> userIds) {
        if (userIds.isEmpty()) return new HashMap<>();
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, Problem> batchLoadProblems(Set<Long> problemIds) {
        if (problemIds.isEmpty()) return new HashMap<>();
        return problemMapper.selectBatchIds(problemIds).stream()
                .collect(Collectors.toMap(Problem::getId, p -> p));
    }

    private AdminSolutionListItemVO toListItemVO(Solution solution, Map<String, User> userMap,
                                                  Map<Long, Problem> problemMap) {
        if (solution == null) return null;

        User author = userMap.get(solution.getUserId());
        AdminSolutionListItemVO.AuthorInfo authorInfo = author != null
                ? new AdminSolutionListItemVO.AuthorInfo(author.getId(), author.getUsername(), author.getName(), author.getEmail())
                : null;

        Problem problem = problemMap.get(solution.getProblemId());
        AdminSolutionListItemVO.ProblemInfo problemInfo = problem != null
                ? new AdminSolutionListItemVO.ProblemInfo(problem.getId().toString(), problem.getSlug(), problem.getTitle(), problem.getDifficulty())
                : null;

        return new AdminSolutionListItemVO(
                solution.getId(),
                solution.getTitle(),
                solution.getLanguage(),
                solution.getViews(),
                solution.getIsPublished(),
                solution.getIsFlagged(),
                solution.getIsDeleted(),
                solution.getCreatedAt(),
                authorInfo,
                problemInfo
        );
    }

    private AdminSolutionVO toAdminVO(Solution solution, Map<String, User> userMap,
                                      Map<Long, Problem> problemMap) {
        if (solution == null) return null;

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

        User author = userMap.get(solution.getUserId());
        if (author != null) {
            AdminSolutionVO.AuthorInfo authorInfo = new AdminSolutionVO.AuthorInfo();
            authorInfo.setId(author.getId());
            authorInfo.setUsername(author.getUsername());
            authorInfo.setName(author.getName());
            authorInfo.setEmail(author.getEmail());
            vo.setAuthor(authorInfo);
        }

        Problem problem = problemMap.get(solution.getProblemId());
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

    private AdminSolutionVO toAdminVO(Solution solution) {
        if (solution == null) return null;

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

        User author = userMapper.selectById(solution.getUserId());
        if (author != null) {
            AdminSolutionVO.AuthorInfo authorInfo = new AdminSolutionVO.AuthorInfo();
            authorInfo.setId(author.getId());
            authorInfo.setUsername(author.getUsername());
            authorInfo.setName(author.getName());
            authorInfo.setEmail(author.getEmail());
            vo.setAuthor(authorInfo);
        }

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