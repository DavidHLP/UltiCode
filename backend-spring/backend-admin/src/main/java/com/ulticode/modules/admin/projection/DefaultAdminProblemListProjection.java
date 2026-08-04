package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListProblemRelation;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.admin.projection.AdminUserEnricher;
import com.ulticode.modules.admin.projection.AdminUserSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminProblemListProjection}. Owns
 * every admin-facing entity-to-VO projection rule for the problem-list
 * surface &mdash; see the interface javadoc for the deepening rationale.
 *
 * <p>All read methods are pure reads; none mutate list state. Cross-module
 * dependencies point inward (admin &rarr; feature): this projection imports
 * {@link ProblemList}, {@link Problem} entities and their
 * mappers. The feature-side {@link com.ulticode.modules.problemlist.projection.ProblemListProjection}
 * no longer imports {@code AdminProblemListQueryDTO}, restoring the
 * admin &rarr; feature direction the rest of the admin projection series
 * ({@link DefaultAdminContestProjection}, DefaultAdminSubmissionProjection,
 * DefaultAdminUserProjection) already follows.
 *
 * <p>Mirrors the {@link DefaultAdminContestProjection} shape:
 * {@link org.springframework.stereotype.Service @Service} +
 * Lombok's {@link RequiredArgsConstructor} for constructor injection,
 * {@link Slf4j @Slf4j} for the SLF4J Logger, a paginated read that owns its
 * own {@link LambdaQueryWrapper}, and a single-detail read that owns the
 * entity load.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminProblemListProjection implements AdminProblemListProjection {

    private final ProblemListMapper problemListMapper;
    private final ProblemListProblemMapper problemListProblemMapper;
    private final ProblemMapper problemMapper;
    private final AdminUserEnricher userEnricher;

    // ------------------------------------------------------------------
    // Paginated list read (query build + shape)
    // ------------------------------------------------------------------

    @Override
    public PageResult<ProblemListSummaryVO> findAdminLists(AdminProblemListQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);
        int page = pageRequest.page();
        int limit = pageRequest.pageSize();

        Page<ProblemList> result = problemListMapper.selectPage(
                new Page<>(page, limit), buildWrapper(query));

        List<ProblemListSummaryVO> vos = result.getRecords().stream()
                .map(this::toSummaryVO)
                .collect(Collectors.toList());

        return PageResult.of(vos, result.getTotal(), page, limit);
    }

    // ------------------------------------------------------------------
    // Single-detail read (load + shape)
    // ------------------------------------------------------------------

    @Override
    public ProblemListDetailVO getAdminListDetail(String id) {
        ProblemList list = problemListMapper.findById(id)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));

        ProblemListDetailVO vo = new ProblemListDetailVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setDescription(list.getDescription());
        vo.setAuthorId(list.getAuthorId());
        vo.setIsPublic(list.getIsPublic());
        vo.setIsFeatured(list.getIsFeatured());
        vo.setBannerTag(list.getBannerTag());
        vo.setBannerIcon(list.getBannerIcon());
        vo.setBannerTheme(list.getBannerTheme());
        vo.setBannerOrder(list.getBannerOrder());
        vo.setCreatedAt(list.getCreatedAt());
        vo.setUpdatedAt(list.getUpdatedAt());

        // Admin view: not owner, not saved.
        vo.setIsOwner(false);
        vo.setIsSaved(false);

        // Author enrichment.
        AdminUserSummary author = userEnricher.enrichOne(list.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.name());
            vo.setAuthorUsername(author.username());
        }

        // Problems in the list with batched tag lookup.
        List<ProblemListProblemRelation> relations =
                problemListProblemMapper.findByListId(list.getId());
        List<ProblemListDetailVO.ProblemInListVO> problemVOs;
        if (relations.isEmpty()) {
            problemVOs = Collections.emptyList();
        } else {
            problemVOs = assembleProblemInList(relations);
        }
        vo.setProblems(problemVOs);

        // Solved/attempted/todo stats.
        vo.setStats(assembleStats(list.getId(), problemVOs));

        // Admin view: no viewer state, no categories.
        vo.setViewer(null);
        vo.setCategories(Collections.emptyList());
        return vo;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Build the {@link LambdaQueryWrapper} that backs the paginated admin
     * list read. Pure query-shape concern &mdash; search across name +
     * description, featured / public filters, sort selector.
     */
    private LambdaQueryWrapper<ProblemList> buildWrapper(AdminProblemListQueryDTO query) {
        LambdaQueryWrapper<ProblemList> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getSearch())) {
            String search = "%" + query.getSearch() + "%";
            wrapper.and(w -> w
                    .like(ProblemList::getName, search)
                    .or()
                    .like(ProblemList::getDescription, search));
        }

        if (query.getIsFeatured() != null) {
            wrapper.eq(ProblemList::getIsFeatured, query.getIsFeatured());
        }

        if (query.getIsPublic() != null) {
            wrapper.eq(ProblemList::getIsPublic, query.getIsPublic());
        }

        boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        switch (sortBy) {
            case "name" -> wrapper.orderBy(true, isAsc, ProblemList::getName);
            case "bannerOrder" -> wrapper.orderBy(true, isAsc, ProblemList::getBannerOrder);
            default -> wrapper.orderBy(true, isAsc, ProblemList::getCreatedAt);
        }

        return wrapper;
    }

    /**
     * Entity &rarr; summary VO with problem-count and author enrichment.
     * Same shape as the feature-side
     * {@code ProblemListProjection.toSummaryVO}; intentionally local so the
     * admin projection's dependency direction stays admin &rarr; feature
     * (mapper / entity only) and does not depend on the feature-side
     * projection interface just to shape its own admin VOs.
     */
    private ProblemListSummaryVO toSummaryVO(ProblemList list) {
        ProblemListSummaryVO vo = new ProblemListSummaryVO();
        vo.setId(list.getId());
        vo.setName(list.getName());
        vo.setDescription(list.getDescription());
        vo.setAuthorId(list.getAuthorId());
        vo.setIsPublic(list.getIsPublic());
        vo.setIsFeatured(list.getIsFeatured());
        vo.setBannerTag(list.getBannerTag());
        vo.setBannerIcon(list.getBannerIcon());
        vo.setBannerTheme(list.getBannerTheme());
        vo.setBannerOrder(list.getBannerOrder());
        vo.setCreatedAt(list.getCreatedAt());
        vo.setUpdatedAt(list.getUpdatedAt());

        vo.setProblemCount((int) problemListProblemMapper.countByListId(list.getId()));

        AdminUserSummary author = userEnricher.enrichOne(list.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.name());
            vo.setAuthorUsername(author.username());
        }

        return vo;
    }

    private List<ProblemListDetailVO.ProblemInListVO> assembleProblemInList(
            List<ProblemListProblemRelation> relations) {
        Set<Long> problemIds = relations.stream()
                .map(ProblemListProblemRelation::getProblemId)
                .collect(Collectors.toSet());
        List<Problem> problems = problemMapper.selectBatchIds(problemIds);
        Map<Long, Problem> problemMap = problems.stream()
                .collect(Collectors.toMap(Problem::getId, p -> p));

        List<ProblemMapper.ProblemTagDTO> tagDTOs =
                problemMapper.selectTagsByProblemIds(new ArrayList<>(problemIds));
        Map<Long, List<ProblemListDetailVO.ProblemInListVO.ProblemTagVO>> tagMap = tagDTOs.stream()
                .collect(Collectors.groupingBy(
                        ProblemMapper.ProblemTagDTO::problemId,
                        Collectors.mapping(dto -> {
                            ProblemListDetailVO.ProblemInListVO.ProblemTagVO tagVO =
                                    new ProblemListDetailVO.ProblemInListVO.ProblemTagVO();
                            tagVO.setId(dto.tagId());
                            tagVO.setLabel(dto.tagName());
                            return tagVO;
                        }, Collectors.toList())
                ));

        return relations.stream()
                .map(rel -> {
                    Problem problem = problemMap.get(rel.getProblemId());
                    if (problem == null) return null;
                    ProblemListDetailVO.ProblemInListVO pvo =
                            new ProblemListDetailVO.ProblemInListVO();
                    pvo.setId(problem.getId());
                    pvo.setSlug(problem.getSlug());
                    pvo.setTitle(problem.getTitle());
                    pvo.setDifficulty(problem.getDifficulty());
                    pvo.setStatus(problem.getStatus());
                    pvo.setSortOrder(rel.getSortOrder());
                    pvo.setAddedAt(rel.getAddedAt());
                    pvo.setAcceptanceRate(problem.getAcceptanceRate());
                    pvo.setIsPremium(problem.getIsPremium());
                    pvo.setHasSolution(problem.getHasSolution());
                    pvo.setTags(tagMap.getOrDefault(problem.getId(), List.of()));
                    return pvo;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ProblemListDetailVO.ProblemListStatsVO assembleStats(
            String listId, List<ProblemListDetailVO.ProblemInListVO> problems) {
        ProblemListDetailVO.ProblemListStatsVO statsVO =
                new ProblemListDetailVO.ProblemListStatsVO();
        statsVO.setListId(listId);
        int totalCount = problems.size();
        int solvedCount = 0;
        int attemptedCount = 0;
        for (ProblemListDetailVO.ProblemInListVO p : problems) {
            String status = p.getStatus();
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
