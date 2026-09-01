package com.ulticode.modules.admin.projection;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.admin.error.AdminReadContract;
import com.ulticode.app.api.service.SolutionAdminReadPort;
import com.ulticode.app.api.service.SolutionAdminReadPort.SolutionAdminQuery;
import com.ulticode.app.api.service.SolutionAdminReadPort.SolutionAdminRow;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminSolutionListItemVO;
import com.ulticode.modules.admin.dto.AdminSolutionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSolutionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
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
                Boolean.TRUE.equals(query.getIsDeleted()),
                query.getSortBy(),
                query.getSortOrder(),
                pageRequest.page(),
                pageRequest.pageSize());

        SolutionAdminReadPort.SolutionAdminPage page;
        try {
            page = solutionAdminReadPort.page(portQuery);
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("App solution", exception);
        }
        requirePage(page);

        Set<String> userIds = page.rows().stream()
                .map(SolutionAdminRow::userId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> problemIds = page.rows().stream()
                .map(SolutionAdminRow::problemId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        AdminUserEnricher.EnrichedUsers users = batchLoadUsers(userIds);
        ProblemBatch problems = batchLoadProblems(problemIds);
        DegradationStatus status = mergeStatus(users.status(), problems.status());

        List<AdminSolutionListItemVO> voList = page.rows().stream()
                .map(s -> toListItemVO(s, users.users(), problems.rows()))
                .toList();

        return PageResult.of(voList, page.total(), pageRequest, status);
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
        SolutionAdminRow row;
        try {
            row = solutionAdminReadPort.getById(id);
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("App solution", exception);
        }
        if (row == null) {
            throw new BusinessException(AdminErrorCode.SOLUTION_NOT_FOUND);
        }
        return toAdminVO(row);
    }

    // ------------------------------------------------------------------
    // Batch-loaders (cross-module enrichment — N+1-safe)
    // ------------------------------------------------------------------

    private AdminUserEnricher.EnrichedUsers batchLoadUsers(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return new AdminUserEnricher.EnrichedUsers(Collections.emptyMap(), DegradationStatus.OK);
        }
        AdminUserEnricher.EnrichedUsers result;
        try {
            result = userEnricher.enrichWithStatus(userIds);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Auth/App user", exception);
        }
        if (result == null || result.status() == null
                || result.status() == DegradationStatus.UNAVAILABLE) {
            throw AdminReadContract.ownerUnavailable("Auth/App user");
        }
        return result;
    }

    private ProblemBatch batchLoadProblems(Set<Long> problemIds) {
        if (problemIds.isEmpty()) {
            return new ProblemBatch(Collections.emptyMap(), DegradationStatus.OK);
        }

        List<ProblemAdminRowDTO> rows;
        try {
            rows = problemReadPort.findProblemsByIds(problemIds);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("App problem", exception);
        }
        if (rows == null) {
            throw AdminReadContract.ownerUnavailable("App problem");
        }

        Map<Long, ProblemAdminRowDTO> rowMap = rows.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(ProblemAdminRowDTO::id, p -> p));
        DegradationStatus status = rowMap.keySet().containsAll(problemIds)
                ? DegradationStatus.OK : DegradationStatus.PARTIAL;
        return new ProblemBatch(rowMap, status);
    }

    private static DegradationStatus mergeStatus(
            DegradationStatus userStatus, DegradationStatus problemStatus) {
        if (userStatus == DegradationStatus.PARTIAL
                || problemStatus == DegradationStatus.PARTIAL) {
            return DegradationStatus.PARTIAL;
        }
        return DegradationStatus.OK;
    }

    private static void requirePage(SolutionAdminReadPort.SolutionAdminPage page) {
        if (page == null || page.rows() == null
                || page.rows().stream().anyMatch(java.util.Objects::isNull)
                || page.total() < 0) {
            throw AdminReadContract.ownerUnavailable("App solution");
        }
    }

    private record ProblemBatch(
            Map<Long, ProblemAdminRowDTO> rows, DegradationStatus status) {
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

        AdminUserSummary author;
        try {
            author = userEnricher.enrichOne(solution.userId());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Auth/App user", exception);
        }
        if (author != null) {
            AdminSolutionVO.AuthorInfo authorInfo = new AdminSolutionVO.AuthorInfo();
            authorInfo.setId(author.accountId());
            authorInfo.setUsername(author.username());
            authorInfo.setName(author.name());
            authorInfo.setEmail(author.email());
            vo.setAuthor(authorInfo);
        }

        ProblemAdminRowDTO problem;
        try {
            problem = problemReadPort.findProblem(solution.problemId());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("App problem", exception);
        }
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
