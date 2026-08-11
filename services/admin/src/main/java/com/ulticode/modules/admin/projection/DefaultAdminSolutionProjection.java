package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.service.SolutionAdminReadPort;
import com.ulticode.app.api.service.SolutionAdminReadPort.SolutionAdminQuery;
import com.ulticode.app.api.service.SolutionAdminReadPort.SolutionAdminRow;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminSolutionListItemVO;
import com.ulticode.modules.admin.dto.AdminSolutionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSolutionVO;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminSolutionProjection}. Owns every
 * row-to-VO projection rule and read-side query translation for the admin
 * solution surface &mdash; see the interface javadoc for why this is a deep
 * module.
 *
 * <p>All methods are pure reads; none mutate solution state. Solution row
 * data crosses the seam as entity-free {@link SolutionAdminRow} values from
 * {@link SolutionAdminReadPort} (ADMIN-006); the admin module no longer
 * imports the solution entity or mapper. Batch-loads cross-module
 * enrichment (user + problem) to keep the paginated list read N+1-safe
 * (mirrors WR-05 / {@code DefaultAdminSubmissionProjection}).
 *
 * <p>Branch routing (active via the provider's MyBatis-Plus path vs
 * soft-deleted via the provider's raw-SQL pair) is expressed as the
 * {@code includeDeleted} flag on the query, keeping the two read branches
 * byte-for-byte behaviour-equivalent to the inline blocks they replace from
 * the pre-cutover {@code AdminSolutionServiceImpl}.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAdminSolutionProjection implements AdminSolutionProjection {

    private final SolutionAdminReadPort solutionAdminReadPort;
    private final AdminUserEnricher userEnricher;
    private final ProblemAdminReadPort problemReadPort;

    // ------------------------------------------------------------------
    // Paginated list read (query translation + batch enrichment)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminSolutionListItemVO> getSolutions(AdminSolutionQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);

        SolutionAdminQuery portQuery = new SolutionAdminQuery(
                query.getSearch(),
                query.getProblemId(),
                query.getUserId(),
                query.getIsFlagged(),
                query.getIsPublished(),
                // When isDeleted=true, bypass MyBatis-Plus filtering via the
                // provider's raw-SQL branch.
                Boolean.TRUE.equals(query.getIsDeleted()),
                query.getSortBy(),
                query.getSortOrder(),
                pageRequest.page(),
                pageRequest.pageSize());

        SolutionAdminReadPort.SolutionAdminPage page = solutionAdminReadPort.page(portQuery);

        Set<String> userIds = page.rows().stream()
                .map(SolutionAdminRow::userId)
                .collect(Collectors.toSet());
        Set<Long> problemIds = page.rows().stream()
                .map(SolutionAdminRow::problemId)
                .collect(Collectors.toSet());

        Map<String, AdminUserSummary> userMap = batchLoadUsers(userIds);
        Map<Long, ProblemAdminRowDTO> problemMap = batchLoadProblems(problemIds);

        List<AdminSolutionListItemVO> voList = page.rows().stream()
                .map(s -> toListItemVO(s, userMap, problemMap))
                .toList();

        return PageResult.of(voList, page.total(), pageRequest.page(), pageRequest.pageSize());
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
        SolutionAdminRow row = solutionAdminReadPort.getById(id);
        if (row == null) {
            throw new BusinessException(AdminErrorCode.SOLUTION_NOT_FOUND);
        }
        return toAdminVO(row);
    }

    // ------------------------------------------------------------------
    // Batch-loaders (cross-module enrichment — N+1-safe)
    // ------------------------------------------------------------------

    private Map<String, AdminUserSummary> batchLoadUsers(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userEnricher.enrich(userIds);
    }

    private Map<Long, ProblemAdminRowDTO> batchLoadProblems(Set<Long> problemIds) {
        if (problemIds.isEmpty()) {
            return new HashMap<>();
        }
        return problemReadPort.findProblemsByIds(problemIds).stream()
                .collect(Collectors.toMap(ProblemAdminRowDTO::id, p -> p));
    }

    // ------------------------------------------------------------------
    // Projection helpers (row &rarr; VO)
    // ------------------------------------------------------------------

    /**
     * Build a list-view {@link AdminSolutionListItemVO} from a
     * {@link SolutionAdminRow} using pre-loaded batch maps. Used by the
     * paginated list read path.
     */
    private AdminSolutionListItemVO toListItemVO(SolutionAdminRow solution, Map<String, AdminUserSummary> userMap,
                                                  Map<Long, ProblemAdminRowDTO> problemMap) {
        if (solution == null) {
            return null;
        }

        AdminUserSummary author = userMap.get(solution.userId());
        AdminSolutionListItemVO.AuthorInfo authorInfo = author != null
                ? new AdminSolutionListItemVO.AuthorInfo(author.accountId(), author.username(),
                        author.name(), author.email())
                : null;

        ProblemAdminRowDTO problem = problemMap.get(solution.problemId());
        AdminSolutionListItemVO.ProblemInfo problemInfo = problem != null
                ? new AdminSolutionListItemVO.ProblemInfo(problem.id().toString(), problem.slug(),
                        problem.title(), problem.difficulty())
                : null;

        return new AdminSolutionListItemVO(
                solution.id(),
                solution.title(),
                solution.language(),
                solution.views(),
                solution.isPublished(),
                solution.isFlagged(),
                solution.isDeleted(),
                solution.createdAt(),
                authorInfo,
                problemInfo
        );
    }

    /**
     * Build a detail-view {@link AdminSolutionVO} from a
     * {@link SolutionAdminRow}. Used by the single-detail read path where the
     * row volume is 1, so enrichment is inline rather than batch-loaded.
     */
    private AdminSolutionVO toAdminVO(SolutionAdminRow solution) {
        if (solution == null) {
            return null;
        }

        AdminSolutionVO vo = new AdminSolutionVO();
        vo.setId(solution.id());
        vo.setProblemId(solution.problemId());
        vo.setUserId(solution.userId());
        vo.setTitle(solution.title());
        vo.setContent(solution.content());
        vo.setSummary(solution.summary());
        vo.setLanguage(solution.language());
        vo.setTags(solution.tags());
        vo.setViews(solution.views());
        vo.setIsPublished(solution.isPublished());
        vo.setPublishedAt(solution.publishedAt());
        vo.setPublishedBy(solution.publishedBy());
        vo.setIsFlagged(solution.isFlagged());
        vo.setFlaggedReason(solution.flaggedReason());
        vo.setFlaggedAt(solution.flaggedAt());
        vo.setIsDeleted(solution.isDeleted());
        vo.setDeletedAt(solution.deletedAt());
        vo.setDeletedBy(solution.deletedBy());
        vo.setCreatedAt(solution.createdAt());
        vo.setUpdatedAt(solution.updatedAt());

        AdminUserSummary author = userEnricher.enrichOne(solution.userId());
        if (author != null) {
            AdminSolutionVO.AuthorInfo authorInfo = new AdminSolutionVO.AuthorInfo();
            authorInfo.setId(author.accountId());
            authorInfo.setUsername(author.username());
            authorInfo.setName(author.name());
            authorInfo.setEmail(author.email());
            vo.setAuthor(authorInfo);
        }

        ProblemAdminRowDTO problem = problemReadPort.findProblem(solution.problemId());
        if (problem != null) {
            AdminSolutionVO.ProblemInfo problemInfo = new AdminSolutionVO.ProblemInfo();
            problemInfo.setId(problem.id().toString());
            problemInfo.setSlug(problem.slug());
            problemInfo.setTitle(problem.title());
            problemInfo.setDifficulty(problem.difficulty());
            vo.setProblem(problemInfo);
        }

        return vo;
    }
}
