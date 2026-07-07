package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminSolutionListItemVO;
import com.ulticode.modules.admin.dto.AdminSolutionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSolutionVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminSolutionProjection}. Owns every
 * entity-to-VO projection rule and read-side query builder for the admin
 * solution surface &mdash; see the interface javadoc for why this is a deep
 * module.
 *
 * <p>All methods are pure reads; none mutate solution state. Batch-loads
 * cross-module enrichment (user + problem) via {@code selectBatchIds} to
 * keep the paginated list read N+1-safe (mirrors WR-05 /
 * {@code DefaultAdminSubmissionProjection}).
 *
 * <p>Cross-module entity imports ({@link User}, {@link Problem} and their
 * mappers) live here and only here &mdash; the admin solution service no
 * longer imports them after the ADR-0011 Stage 2 extraction.
 *
 * <p>The two read branches (active via {@code LambdaQueryWrapper} +
 * soft-deleted via the raw-SQL {@code selectDeletedSolutions} pair) are kept
 * intact and byte-for-byte behaviour-equivalent to the inline blocks they
 * replace from {@code AdminSolutionServiceImpl}.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminSolutionProjection implements AdminSolutionProjection {

    private final SolutionMapper solutionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;

    // ------------------------------------------------------------------
    // Paginated list read (query build + batch enrichment, two branches)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminSolutionListItemVO> getSolutions(AdminSolutionQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);

        // When isDeleted=true, bypass MyBatis-Plus filtering via raw SQL
        if (Boolean.TRUE.equals(query.getIsDeleted())) {
            return getSolutionsDeletedBranch(query, pageRequest.page(), pageRequest.pageSize());
        }
        return getSolutionsActiveBranch(query, pageRequest.page(), pageRequest.pageSize());
    }

    // ------------------------------------------------------------------
    // Flagged list derivation (forces isFlagged=true, isDeleted=false)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminSolutionListItemVO> getFlaggedSolutions(AdminSolutionQueryDTO query) {
        AdminSolutionQueryDTO flaggedQuery = new AdminSolutionQueryDTO();
        flaggedQuery.setSearch(query.getSearch());
        flaggedQuery.setProblemId(query.getProblemId());
        flaggedQuery.setUserId(query.getUserId());
        flaggedQuery.setIsFlagged(true);
        flaggedQuery.setIsPublished(query.getIsPublished());
        // /admin/solutions/flagged always returns currently-active (non-deleted) solutions,
        // even if the caller passes isDeleted=true; otherwise the endpoint title would be
        // misleading (see docs/solutions-admin-api-qa-2026-06-09.md BUG-Q9).
        flaggedQuery.setIsDeleted(false);
        flaggedQuery.setPage(query.getPage());
        flaggedQuery.setLimit(query.getLimit());
        flaggedQuery.setSortBy(query.getSortBy());
        flaggedQuery.setSortOrder(query.getSortOrder());
        return getSolutions(flaggedQuery);
    }

    // ------------------------------------------------------------------
    // Single-item detail read (inline enrichment — row volume is 1)
    // ------------------------------------------------------------------

    @Override
    public AdminSolutionVO getSolution(String id) {
        Solution solution = solutionMapper.selectById(id);
        if (solution == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }
        return toAdminVO(solution);
    }

    // ------------------------------------------------------------------
    // Branch A — active rows via MyBatis-Plus LambdaQueryWrapper
    // ------------------------------------------------------------------

    private PageResult<AdminSolutionListItemVO> getSolutionsActiveBranch(AdminSolutionQueryDTO query,
                                                                         int page, int limit) {
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

    // ------------------------------------------------------------------
    // Branch B — soft-deleted rows via the raw-SQL mapper pair
    // ------------------------------------------------------------------

    private PageResult<AdminSolutionListItemVO> getSolutionsDeletedBranch(AdminSolutionQueryDTO query,
                                                                          int page, int limit) {
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

    // ------------------------------------------------------------------
    // Batch-loaders (cross-module enrichment — N+1-safe)
    // ------------------------------------------------------------------

    private Map<String, User> batchLoadUsers(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<Long, Problem> batchLoadProblems(Set<Long> problemIds) {
        if (problemIds.isEmpty()) {
            return new HashMap<>();
        }
        return problemMapper.selectBatchIds(problemIds).stream()
                .collect(Collectors.toMap(Problem::getId, p -> p));
    }

    // ------------------------------------------------------------------
    // Projection helpers (entity &rarr; VO)
    // ------------------------------------------------------------------

    /**
     * Build a list-view {@link AdminSolutionListItemVO} from a Solution using
     * pre-loaded batch maps. Used by the paginated list read path.
     */
    private AdminSolutionListItemVO toListItemVO(Solution solution, Map<String, User> userMap,
                                                  Map<Long, Problem> problemMap) {
        if (solution == null) {
            return null;
        }

        User author = userMap.get(solution.getUserId());
        AdminSolutionListItemVO.AuthorInfo authorInfo = author != null
                ? new AdminSolutionListItemVO.AuthorInfo(author.getId(), author.getUsername(),
                        author.getName(), author.getEmail())
                : null;

        Problem problem = problemMap.get(solution.getProblemId());
        AdminSolutionListItemVO.ProblemInfo problemInfo = problem != null
                ? new AdminSolutionListItemVO.ProblemInfo(problem.getId().toString(), problem.getSlug(),
                        problem.getTitle(), problem.getDifficulty())
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

    /**
     * Build a detail-view {@link AdminSolutionVO} from a Solution. Used by the
     * single-detail read path where the row volume is 1, so enrichment is
     * inline rather than batch-loaded.
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
