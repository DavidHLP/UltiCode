package com.ulticode.modules.solution.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.service.SolutionAdminReadPort;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Provider implementing {@link SolutionAdminReadPort} for the admin service
 * (ADMIN-006).
 *
 * <p>Owns both query branches the admin solution list surface used to build
 * against {@code SolutionMapper} directly: the active branch via
 * MyBatis-Plus {@code LambdaQueryWrapper} (logical-delete filtered) and the
 * soft-deleted branch via the raw-SQL
 * {@code selectDeletedSolutions}/{@code countDeletedSolutions} pair. All
 * rows are projected into the entity-free {@link SolutionAdminReadPort.SolutionAdminRow}.
 *
 * @author ulticode
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultSolutionAdminReadAdapter implements SolutionAdminReadPort {

    private final SolutionMapper solutionMapper;

    @Override
    public SolutionAdminPage page(SolutionAdminQuery query) {
        if (query.includeDeleted()) {
            return pageDeletedBranch(query);
        }
        return pageActiveBranch(query);
    }

    @Override
    public SolutionAdminRow getById(String id) {
        Solution solution = solutionMapper.selectById(id);
        return solution != null ? toRow(solution) : null;
    }

    // ------------------------------------------------------------------
    // Branch A — active rows via MyBatis-Plus LambdaQueryWrapper
    // ------------------------------------------------------------------

    private SolutionAdminPage pageActiveBranch(SolutionAdminQuery query) {
        LambdaQueryWrapper<Solution> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.search())) {
            String search = "%" + query.search() + "%";
            wrapper.and(w -> w
                    .like(Solution::getTitle, search)
                    .or()
                    .like(Solution::getContent, search));
        }

        if (query.problemId() != null) {
            wrapper.eq(Solution::getProblemId, query.problemId());
        }

        if (StringUtils.hasText(query.userId())) {
            wrapper.eq(Solution::getUserId, query.userId());
        }

        if (query.isFlagged() != null) {
            wrapper.eq(Solution::getIsFlagged, query.isFlagged());
        }

        if (query.isPublished() != null) {
            wrapper.eq(Solution::getIsPublished, query.isPublished());
        }

        boolean isAsc = "asc".equalsIgnoreCase(query.sortOrder());
        String sortBy = StringUtils.hasText(query.sortBy()) ? query.sortBy() : "createdAt";
        switch (sortBy) {
            case "title" -> wrapper.orderBy(true, isAsc, Solution::getTitle);
            case "views" -> wrapper.orderBy(true, isAsc, Solution::getViews);
            case "createdAt" -> wrapper.orderBy(true, isAsc, Solution::getCreatedAt);
            case "updatedAt" -> wrapper.orderBy(true, isAsc, Solution::getUpdatedAt);
            default -> wrapper.orderBy(true, isAsc, Solution::getCreatedAt);
        }

        Page<Solution> result = solutionMapper.selectPage(new Page<>(query.page(), query.limit()), wrapper);

        List<SolutionAdminRow> rows = result.getRecords().stream().map(this::toRow).toList();
        return new SolutionAdminPage(rows, result.getTotal());
    }

    // ------------------------------------------------------------------
    // Branch B — soft-deleted rows via the raw-SQL mapper pair
    // ------------------------------------------------------------------

    private SolutionAdminPage pageDeletedBranch(SolutionAdminQuery query) {
        int offset = (query.page() - 1) * query.limit();
        String search = StringUtils.hasText(query.search()) ? query.search() : null;
        String userId = StringUtils.hasText(query.userId()) ? query.userId() : null;

        boolean isAsc = "asc".equalsIgnoreCase(query.sortOrder());
        String sortBy = StringUtils.hasText(query.sortBy()) ? query.sortBy() : "createdAt";
        String sortColumn = switch (sortBy) {
            case "title" -> "title";
            case "views" -> "views";
            case "updatedAt" -> "updated_at";
            default -> "created_at";
        };
        String sortOrder = isAsc ? "ASC" : "DESC";

        List<Solution> deletedSolutions = solutionMapper.selectDeletedSolutions(
                search, query.problemId(), userId,
                query.isFlagged(), query.isPublished(),
                sortColumn, sortOrder, query.limit(), offset);
        long total = solutionMapper.countDeletedSolutions(
                search, query.problemId(), userId,
                query.isFlagged(), query.isPublished());

        List<SolutionAdminRow> rows = deletedSolutions.stream().map(this::toRow).toList();
        return new SolutionAdminPage(rows, total);
    }

    // ------------------------------------------------------------------
    // Entity -> row projection (entity-free across the seam)
    // ------------------------------------------------------------------

    private SolutionAdminRow toRow(Solution s) {
        return new SolutionAdminRow(
                s.getId(),
                s.getProblemId(),
                s.getUserId(),
                s.getTitle(),
                s.getContent(),
                s.getSummary(),
                s.getLanguage(),
                s.getTags(),
                s.getViews(),
                s.getIsPublished(),
                s.getPublishedAt(),
                s.getPublishedBy(),
                s.getIsFlagged(),
                s.getFlaggedReason(),
                s.getFlaggedAt(),
                s.getIsDeleted(),
                s.getDeletedAt(),
                s.getDeletedBy(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
