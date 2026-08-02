package com.ulticode.modules.problemlist.projection;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
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
import com.ulticode.app.api.dto.ProblemListItemDTO;
import com.ulticode.app.api.service.ProblemListReadPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final ProblemListReadPort problemListReadPort;
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
        // Get featured lists
        List<ProblemList> featured = problemListMapper.findFeatured();
        result.setFeaturedLists(featured.stream()
                .map(list -> toSummaryVOWithSavedStatus(list, userId))
                .collect(Collectors.toList()));

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

        // Get problems in the list via ProblemListReadPort
        List<ProblemListProblemRelation> relations = problemListProblemMapper.findByListId(id);
        if (!relations.isEmpty()) {
            Set<Long> problemIds = relations.stream()
                    .map(ProblemListProblemRelation::getProblemId)
                    .collect(Collectors.toSet());
            List<ProblemListItemDTO> dtos = problemListReadPort.findByIds(problemIds);
            Map<Long, ProblemListItemDTO> dtoById = dtos.stream()
                    .collect(Collectors.toMap(ProblemListItemDTO::id, dto -> dto));
            Map<Long, List<ProblemListDetailVO.ProblemInListVO.ProblemTagVO>> tagMap = dtos.stream()
                    .collect(Collectors.toMap(
                            ProblemListItemDTO::id,
                            dto -> (dto.tags() == null ? List.of()
                                    : dto.tags().stream()
                                            .map(t -> {
                                                ProblemListDetailVO.ProblemInListVO.ProblemTagVO tag = new ProblemListDetailVO.ProblemInListVO.ProblemTagVO();
                                                tag.setId(t.id());
                                                tag.setLabel(t.label());
                                                return tag;
                                            })
                                            .collect(Collectors.toList()))));

            List<ProblemListDetailVO.ProblemInListVO> problemVOs = relations.stream()
                    .map(rel -> {
                        ProblemListItemDTO dto = dtoById.get(rel.getProblemId());
                        if (dto == null) return null;
                        ProblemListDetailVO.ProblemInListVO pvo = new ProblemListDetailVO.ProblemInListVO();
                        pvo.setId(dto.id());
                        pvo.setSlug(dto.slug());
                        pvo.setTitle(dto.title());
                        pvo.setDifficulty(dto.difficulty());
                        pvo.setStatus(dto.status());
                        pvo.setSortOrder(rel.getSortOrder());
                        pvo.setAddedAt(rel.getAddedAt());
                        pvo.setAcceptanceRate(dto.acceptanceRate());
                        pvo.setIsPremium(dto.isPremium());
                        pvo.setHasSolution(dto.hasSolution());
                        pvo.setTags(tagMap.getOrDefault(dto.id(), List.of()));
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

        // Get list count
        vo.setListCount((int) problemListBookmarkMapper.findByCategoryId(category.getId()).size());

        return vo;
    }

    private List<ProblemListDetailVO.ProblemInListVO> assembleProblemInList(
            List<ProblemListProblemRelation> relations) {
        Set<Long> problemIds = relations.stream()
                .map(ProblemListProblemRelation::getProblemId)
                .collect(Collectors.toSet());
        List<ProblemListItemDTO> dtos = problemListReadPort.findByIds(problemIds);
        Map<Long, ProblemListItemDTO> dtoById = dtos.stream()
                .collect(Collectors.toMap(ProblemListItemDTO::id, dto -> dto));
        Map<Long, List<ProblemListDetailVO.ProblemInListVO.ProblemTagVO>> tagMap = dtos.stream()
                .collect(Collectors.toMap(
                        ProblemListItemDTO::id,
                        dto -> (dto.tags() == null ? List.of()
                                : dto.tags().stream()
                                        .map(t -> {
                                            ProblemListDetailVO.ProblemInListVO.ProblemTagVO tag = new ProblemListDetailVO.ProblemInListVO.ProblemTagVO();
                                            tag.setId(t.id());
                                            tag.setLabel(t.label());
                                            return tag;
                                        })
                                        .collect(Collectors.toList()))));

        return relations.stream()
                .map(rel -> {
                    ProblemListItemDTO dto = dtoById.get(rel.getProblemId());
                    if (dto == null) return null;
                    ProblemListDetailVO.ProblemInListVO pvo = new ProblemListDetailVO.ProblemInListVO();
                    pvo.setId(dto.id());
                    pvo.setSlug(dto.slug());
                    pvo.setTitle(dto.title());
                    pvo.setDifficulty(dto.difficulty());
                    pvo.setStatus(dto.status());
                    pvo.setSortOrder(rel.getSortOrder());
                    pvo.setAddedAt(rel.getAddedAt());
                    pvo.setAcceptanceRate(dto.acceptanceRate());
                    pvo.setIsPremium(dto.isPremium());
                    pvo.setHasSolution(dto.hasSolution());
                    pvo.setTags(tagMap.getOrDefault(dto.id(), List.of()));
                    return pvo;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private ProblemListDetailVO.ProblemListStatsVO assembleStats(
            String listId, List<ProblemListDetailVO.ProblemInListVO> problems) {
        ProblemListDetailVO.ProblemListStatsVO statsVO = new ProblemListDetailVO.ProblemListStatsVO();
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
