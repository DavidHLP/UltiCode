package com.ulticode.modules.admin.projection;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.admin.error.AdminReadContract;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.app.api.dto.ProblemListDetailDTO;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.service.ProblemListChainReadPort;
import com.ulticode.app.api.service.ProblemListSearchReadPort;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminProblemListProjection}.
 *
 * <p>ADMIN-005 (P7-RELOCATE-PROBLEMLIST-001): the App-private problem-list
 * entities / mappers are gone from the admin module. Every read is a single
 * bounded RPC through the entity-free app-api ports
 * ({@link ProblemListSearchReadPort} for the paginated list,
 * {@link ProblemListChainReadPort} for the single-detail chain). The
 * projection keeps the admin-specific concerns local: page normalization,
 * author enrichment (identity view via {@link AdminUserEnricher}), the
 * admin detail shaping (no viewer state, no categories) and the
 * solved/attempted/todo stats aggregation.
 *
 * <p>Mirrors the {@link DefaultAdminContestProjection} shape:
 * {@link Service @Service} + Lombok's {@link RequiredArgsConstructor} for
 * constructor injection, {@link Slf4j @Slf4j}, a paginated read and a
 * single-detail read.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminProblemListProjection implements AdminProblemListProjection {

    private final ProblemListSearchReadPort problemListSearchReadPort;
    private final ProblemListChainReadPort problemListChainReadPort;
    private final AdminUserEnricher userEnricher;

    // ------------------------------------------------------------------
    // Paginated list read (query normalization + enrichment)
    // ------------------------------------------------------------------

    @Override
    public PageResult<ProblemListSummaryDTO> findAdminLists(AdminProblemListQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);
        int page = pageRequest.page();
        int limit = pageRequest.pageSize();

        PageResult<ProblemListSummaryDTO> remote = problemListSearchReadPort.searchAdminLists(
                query.getSearch(),
                query.getIsFeatured(),
                query.getIsPublic(),
                query.getSortBy(),
                query.getSortOrder(),
                page,
                limit);
        // Batch author enrichment: collect all author IDs and look them up
        // in one bounded RPC via enrichWithStatus, avoiding per-row enrichOne.
        List<ProblemListSummaryDTO> items = remote.getItems();
        Set<String> authorIds = items.stream()
                .map(ProblemListSummaryDTO::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        AdminUserEnricher.EnrichedUsers enriched = userEnricher.enrichWithStatus(authorIds);
        if (enriched == null || enriched.status() == null
                || enriched.status() == DegradationStatus.UNAVAILABLE) {
            throw AdminReadContract.ownerUnavailable("Auth/App user");
        }
        Map<String, AdminUserSummary> users = enriched.users();
        List<ProblemListSummaryDTO> enrichedItems = items.stream()
                .peek(dto -> {
                    AdminUserSummary author = users.get(dto.getAuthorId());
                    if (author != null) {
                        dto.setAuthorName(author.name());
                        dto.setAuthorUsername(author.username());
                    }
                })
                .collect(Collectors.toList());

        return PageResult.of(enrichedItems, remote.getTotal(), page, limit);
    }

    // ------------------------------------------------------------------
    // Single-detail read (shape + enrichment + stats)
    // ------------------------------------------------------------------

    @Override
    public ProblemListDetailDTO getAdminListDetail(String id) {
        ProblemListDetailDTO vo = problemListChainReadPort.findAdminDetail(id);
        if (vo == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND);
        }

        // Admin view: not owner, not saved.
        vo.setIsOwner(false);
        vo.setIsSaved(false);

        // Author enrichment.
        AdminUserSummary author = userEnricher.enrichOne(vo.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.name());
            vo.setAuthorUsername(author.username());
        }

        // Admin view: no viewer state, no categories.
        vo.setViewer(null);
        vo.setCategories(Collections.emptyList());

        // Solved/attempted/todo stats from the problem chain.
        vo.setStats(assembleStats(id, vo.getProblems()));
        return vo;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private ProblemListSummaryDTO enrichAuthor(ProblemListSummaryDTO dto) {
        AdminUserSummary author = userEnricher.enrichOne(dto.getAuthorId());
        if (author != null) {
            dto.setAuthorName(author.name());
            dto.setAuthorUsername(author.username());
        }
        return dto;
    }

    private ProblemListDetailDTO.ProblemListStatsDTO assembleStats(
            String listId, List<ProblemListDetailDTO.ProblemInListDTO> problems) {
        ProblemListDetailDTO.ProblemListStatsDTO statsVO =
                new ProblemListDetailDTO.ProblemListStatsDTO();
        statsVO.setListId(listId);
        List<ProblemListDetailDTO.ProblemInListDTO> items =
                problems == null ? Collections.emptyList() : problems;
        int totalCount = items.size();
        int solvedCount = 0;
        int attemptedCount = 0;
        for (ProblemListDetailDTO.ProblemInListDTO p : items) {
            String status = p.status();
            if ("solved".equalsIgnoreCase(status)) {
                solvedCount++;
            } else if ("attempted".equalsIgnoreCase(status)) {
                attemptedCount++;
            }
        }
        int todoCount = Math.max(0, totalCount - solvedCount - attemptedCount);
        double progress = totalCount == 0
                ? 0.0
                : ((double) solvedCount / totalCount) * 100.0;
        statsVO.setTotalCount(totalCount);
        statsVO.setSolvedCount(solvedCount);
        statsVO.setAttemptedCount(attemptedCount);
        statsVO.setTodoCount(todoCount);
        statsVO.setProgress(progress);
        return statsVO;
    }
}
