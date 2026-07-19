package com.ulticode.modules.problemlist.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.problemlist.dto.CategorySummaryVO;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UserListsForProblemVO;
import com.ulticode.modules.problemlist.dto.UserProblemListsVO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListBookmark;
import com.ulticode.modules.problemlist.entity.ProblemListCategory;
import com.ulticode.modules.problemlist.entity.ProblemListProblemRelation;
import com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Default (and only) adapter for {@link ProblemListProjection}. Owns every
 * entity-to-VO projection rule and read-side aggregation for the problem-list
 * domain — see the interface javadoc for why this is a deep module.
 *
 * <p>All methods are pure reads; none mutate list state. The broad
 * {@code catch (Exception)} around the category-table reads preserves the
 * legacy tolerance for deployments where {@code problem_list_categories} does
 * not yet exist.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultProblemListProjection implements ProblemListProjection {

    private final ProblemListMapper problemListMapper;
    private final ProblemListProblemMapper problemListProblemMapper;
    private final ProblemListCategoryMapper problemListCategoryMapper;
    private final ProblemListBookmarkMapper problemListBookmarkMapper;
    private final ProblemMapper problemMapper;
    private final UserMapper userMapper;

    // ------------------------------------------------------------------
    // Overview reads
    // ------------------------------------------------------------------

    @Override
    public UserProblemListsVO findAll(String locale) {
        UserProblemListsVO result = new UserProblemListsVO();

        // Get featured lists
        List<ProblemList> featured = problemListMapper.findFeatured();
        result.setFeaturedLists(featured.stream()
                .map(this::toSummaryVO)
                .collect(Collectors.toList()));

        // Get all public lists
        List<ProblemList> publicLists = problemListMapper.findAllPublic();
        result.setOwnLists(publicLists.stream()
                .map(this::toSummaryVO)
                .collect(Collectors.toList()));

        result.setSavedLists(Collections.emptyList());
        result.setCategories(Collections.emptyList());

        return result;
    }

    @Override
    public UserProblemListsVO getUserProblemLists(String userId) {
        UserProblemListsVO result = new UserProblemListsVO();

        // Get user's own lists
        List<ProblemList> ownLists = problemListMapper.findByAuthorId(userId);
        result.setOwnLists(ownLists.stream()
                .map(list -> toSummaryVOWithSavedStatus(list, userId))
                .collect(Collectors.toList()));

        // Get saved lists
        List<ProblemListBookmark> bookmarks = problemListBookmarkMapper.findByUserId(userId);
        List<ProblemListSummaryVO> savedLists = bookmarks.stream()
                .map(bookmark -> {
                    ProblemList list = problemListMapper.findById(bookmark.getListId()).orElse(null);
                    if (list != null && (list.getIsPublic() || list.getAuthorId().equals(userId))) {
                        ProblemListSummaryVO vo = toSummaryVO(list);
                        vo.setIsSaved(true);
                        return vo;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        result.setSavedLists(savedLists);

        // Get featured lists
        List<ProblemList> featured = problemListMapper.findFeatured();
        result.setFeaturedLists(featured.stream()
                .map(list -> toSummaryVOWithSavedStatus(list, userId))
                .collect(Collectors.toList()));

        // Get categories - NOTE: Requires database migration
        try {
            List<ProblemListCategory> categories = problemListCategoryMapper.findByUserId(userId);
            result.setCategories(categories.stream()
                    .map(this::toCategorySummaryVO)
                    .collect(Collectors.toList()));
        // broad catch: table may not exist in all deployments
        } catch (Exception e) {
            log.warn("Categories table may not exist: {}", e.getMessage());
            result.setCategories(Collections.emptyList());
        }

        return result;
    }

    @Override
    public ProblemListDetailVO getListOverview(String id, String userId, String locale) {
        ProblemList list = problemListMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

        // Check access
        if (!list.getIsPublic() && (userId == null || !list.getAuthorId().equals(userId))) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_PRIVATE);
        }

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

        // Check if user owns the list
        vo.setIsOwner(userId != null && userId.equals(list.getAuthorId()));

        // Check if user has saved the list — reuse bookmark for viewer state below
        ProblemListBookmark userBookmark = null;
        if (userId != null) {
            userBookmark = problemListBookmarkMapper.findByUserIdAndListId(userId, id).orElse(null);
            vo.setIsSaved(userBookmark != null);
        } else {
            vo.setIsSaved(false);
        }

        // Get author info
        User author = userMapper.selectById(list.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.getName());
            vo.setAuthorUsername(author.getUsername());
        }

        // Get problems in the list
        List<ProblemListProblemRelation> relations = problemListProblemMapper.findByListId(id);
        if (!relations.isEmpty()) {
            Set<Long> problemIds = relations.stream()
                    .map(ProblemListProblemRelation::getProblemId)
                    .collect(Collectors.toSet());
            List<Problem> problems = problemMapper.selectBatchIds(problemIds);
            Map<Long, Problem> problemMap = problems.stream()
                    .collect(Collectors.toMap(Problem::getId, p -> p));

            // Batch-fetch tags for problems in the list
            List<ProblemMapper.ProblemTagDTO> tagDTOs = problemMapper.selectTagsByProblemIds(new ArrayList<>(problemIds));
            Map<Long, List<ProblemVO.ProblemTagVO>> tagMap = tagDTOs.stream()
                    .collect(Collectors.groupingBy(
                            ProblemMapper.ProblemTagDTO::problemId,
                            Collectors.mapping(dto -> {
                                ProblemVO.ProblemTagVO tagVO = new ProblemVO.ProblemTagVO();
                                tagVO.setId(dto.tagId());
                                tagVO.setLabel(dto.tagName());
                                return tagVO;
                            }, Collectors.toList())
                    ));

            List<ProblemListDetailVO.ProblemInListVO> problemVOs = relations.stream()
                    .map(rel -> {
                        Problem problem = problemMap.get(rel.getProblemId());
                        if (problem == null) return null;

                        ProblemListDetailVO.ProblemInListVO pvo = new ProblemListDetailVO.ProblemInListVO();
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
            vo.setProblems(problemVOs);
        } else {
            vo.setProblems(Collections.emptyList());
        }

        // Build stats
        ProblemListDetailVO.ProblemListStatsVO statsVO = new ProblemListDetailVO.ProblemListStatsVO();
        statsVO.setListId(id);
        List<ProblemListDetailVO.ProblemInListVO> problems = vo.getProblems();
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
        int todoCount = totalCount - solvedCount - attemptedCount;
        double progress = totalCount == 0 ? 0.0 : ((double) solvedCount / totalCount) * 100.0;
        statsVO.setTotalCount(totalCount);
        statsVO.setSolvedCount(solvedCount);
        statsVO.setAttemptedCount(attemptedCount);
        statsVO.setTodoCount(todoCount);
        statsVO.setProgress(progress);
        vo.setStats(statsVO);

        // Build viewer state — reuse bookmark fetched above
        if (userId != null) {
            ProblemListDetailVO.ViewerStateVO viewerVO = new ProblemListDetailVO.ViewerStateVO();
            viewerVO.setIsSaved(vo.getIsSaved());
            viewerVO.setCategoryId(userBookmark != null ? userBookmark.getCategoryId() : null);
            vo.setViewer(viewerVO);
        }

        // Build category options for the viewer
        if (userId != null) {
            try {
                List<ProblemListCategory> cats = problemListCategoryMapper.findByUserId(userId);
                vo.setCategories(cats.stream().map(cat -> {
                    ProblemListDetailVO.CategoryOptionVO opt = new ProblemListDetailVO.CategoryOptionVO();
                    opt.setId(cat.getId());
                    opt.setName(cat.getName());
                    opt.setSortOrder(cat.getSortOrder());
                    return opt;
                }).collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("Categories table may not exist: {}", e.getMessage());
                vo.setCategories(Collections.emptyList());
            }
        }

        return vo;
    }

    @Override
    public UserListsForProblemVO getUserListsForProblem(String userId, Long problemId) {
        UserListsForProblemVO result = new UserListsForProblemVO();
        result.setProblemId(problemId);

        List<ProblemList> userLists = problemListMapper.findByAuthorId(userId);
        if (userLists.isEmpty()) {
            result.setLists(Collections.emptyList());
            return result;
        }

        // Batch-load hasProblem + problemCount in 2 queries instead of 2*N (avoids N+1).
        List<String> listIds = userLists.stream().map(ProblemList::getId).collect(Collectors.toList());
        Set<String> listsContainingProblem = new HashSet<>(
                problemListProblemMapper.findListIdsContainingProblem(listIds, problemId));
        Map<String, Long> countByList = problemListProblemMapper.countByListIds(listIds).stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("list_id")),
                        row -> ((Number) row.get("cnt")).longValue()));

        List<UserListsForProblemVO.ListStatusVO> listStatuses = userLists.stream()
                .map(list -> {
                    UserListsForProblemVO.ListStatusVO status = new UserListsForProblemVO.ListStatusVO();
                    status.setId(list.getId());
                    status.setName(list.getName());
                    status.setHasProblem(listsContainingProblem.contains(list.getId()));
                    status.setProblemCount(countByList.getOrDefault(list.getId(), 0L).intValue());
                    status.setCanEdit(true);
                    return status;
                })
                .collect(Collectors.toList());

        result.setLists(listStatuses);
        return result;
    }

    // ------------------------------------------------------------------
    // Projection helpers (also used by the write-side service)
    // ------------------------------------------------------------------

    @Override
    public ProblemListSummaryVO toSummaryVO(ProblemList list) {
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

        // Get problem count
        vo.setProblemCount((int) problemListProblemMapper.countByListId(list.getId()));

        // Get author info
        User author = userMapper.selectById(list.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.getName());
            vo.setAuthorUsername(author.getUsername());
        }

        return vo;
    }

    @Override
    public ProblemListSummaryVO toSummaryVOWithSavedStatus(ProblemList list, String userId) {
        ProblemListSummaryVO vo = toSummaryVO(list);
        if (userId != null) {
            vo.setIsSaved(problemListBookmarkMapper.existsByUserIdAndListId(userId, list.getId()));
        } else {
            vo.setIsSaved(false);
        }
        return vo;
    }

    @Override
    public CategorySummaryVO toCategorySummaryVO(ProblemListCategory category) {
        CategorySummaryVO vo = new CategorySummaryVO();
        vo.setId(category.getId());
        vo.setUserId(category.getUserId());
        vo.setName(category.getName());
        vo.setDescription(category.getDescription());
        vo.setIcon(category.getIcon());
        vo.setColor(category.getColor());
        vo.setSortOrder(category.getSortOrder());
        vo.setCreatedAt(category.getCreatedAt());
        vo.setUpdatedAt(category.getUpdatedAt());

        // Get list count
        vo.setListCount((int) problemListBookmarkMapper.findByCategoryId(category.getId()).size());

        return vo;
    }

    // ------------------------------------------------------------------
    // Admin intent-level reads (architecture-review 2026-07-19 candidate #3)
    // ------------------------------------------------------------------
    // The management console used to build its own LambdaQueryWrapper, run
    // selectPage, then map each entity through the cross-module toSummaryVO
    // conversion helper — the page-assembly mechanics leaked across the seam.
    // These two reads own the page query, the wrapper, the entity load, and
    // the projection internally; the admin service is left with only its
    // audit context. Mirrors the DefaultAdminContestProjection /
    // DefaultAdminSubmissionProjection / DefaultAdminUserProjection shape
    // (PaginationRequest default page-size 10, selectPage, shape, PageResult).

    @Override
    public PageResult<ProblemListSummaryVO> findAdminLists(AdminProblemListQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);
        int page = pageRequest.page();
        int limit = pageRequest.pageSize();

        Page<ProblemList> result = problemListMapper.selectPage(
                new Page<>(page, limit), buildAdminWrapper(query));

        List<ProblemListSummaryVO> vos = result.getRecords().stream()
                .map(this::toSummaryVO)
                .collect(Collectors.toList());

        return PageResult.of(vos, result.getTotal(), page, limit);
    }

    @Override
    public ProblemListDetailVO getAdminListDetail(String id) {
        ProblemList list = problemListMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));
        return assembleAdminDetailVO(list);
    }

    /**
     * Build the {@link LambdaQueryWrapper} that backs the paginated admin
     * list read. Pure query-shape concern — search across name + description,
     * featured / public filters, sort selector matching the previous
     * admin-service assembly so observed behaviour is preserved.
     */
    private LambdaQueryWrapper<ProblemList> buildAdminWrapper(AdminProblemListQueryDTO query) {
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
     * Admin-detail projection (private). Same body the previous public
     * {@code toAdminDetailVO} conversion helper had; one source of truth so
     * any future tweak lands in a single place. The intent-level
     * {@link #getAdminListDetail(String)} does the load and delegates here.
     */
    private ProblemListDetailVO assembleAdminDetailVO(ProblemList list) {
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
        User author = userMapper.selectById(list.getAuthorId());
        if (author != null) {
            vo.setAuthorName(author.getName());
            vo.setAuthorUsername(author.getUsername());
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
        Map<Long, List<ProblemVO.ProblemTagVO>> tagMap = tagDTOs.stream()
                .collect(Collectors.groupingBy(
                        ProblemMapper.ProblemTagDTO::problemId,
                        Collectors.mapping(dto -> {
                            ProblemVO.ProblemTagVO tagVO = new ProblemVO.ProblemTagVO();
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
