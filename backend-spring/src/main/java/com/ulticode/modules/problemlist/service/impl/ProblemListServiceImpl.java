package com.ulticode.modules.problemlist.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problemlist.dto.*;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListBookmark;
import com.ulticode.modules.problemlist.entity.ProblemListCategory;
import com.ulticode.modules.problemlist.entity.ProblemListProblemRelation;
import com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.problemlist.service.ProblemListService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of ProblemListService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemListServiceImpl implements ProblemListService {

    private final ProblemListMapper problemListMapper;
    private final ProblemListProblemMapper problemListProblemMapper;
    private final ProblemListCategoryMapper problemListCategoryMapper;
    private final ProblemListBookmarkMapper problemListBookmarkMapper;
    private final ProblemMapper problemMapper;
    private final UserMapper userMapper;

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

        // Check if user has saved the list
        if (userId != null) {
            vo.setIsSaved(problemListBookmarkMapper.existsByUserIdAndListId(userId, id));
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
                        return pvo;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            vo.setProblems(problemVOs);
        } else {
            vo.setProblems(Collections.emptyList());
        }

        return vo;
    }

    @Override
    @Transactional
    public ProblemListSummaryVO createList(String userId, CreateProblemListDTO dto) {
        ProblemList list = new ProblemList();
        list.setName(dto.getName());
        list.setDescription(dto.getDescription());
        list.setAuthorId(userId);
        list.setIsPublic(dto.getIsPublic() != null ? dto.getIsPublic() : false);
        list.setIsFeatured(false);
        list.setBannerTag(dto.getBannerTag());
        list.setBannerIcon(dto.getBannerIcon());
        list.setBannerTheme(dto.getBannerTheme());
        list.setBannerOrder(dto.getBannerOrder());

        problemListMapper.insert(list);

        ProblemListSummaryVO vo = toSummaryVO(list);
        vo.setProblemCount(0);
        vo.setIsSaved(false);
        return vo;
    }

    @Override
    @Transactional
    public ProblemListSummaryVO updateBasicInfo(String id, String userId, UpdateBasicInfoDTO dto) {
        ProblemList list = problemListMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

        if (!list.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_CANNOT_EDIT);
        }

        list.setName(dto.getName());
        list.setDescription(dto.getDescription());

        problemListMapper.updateById(list);

        return toSummaryVOWithSavedStatus(list, userId);
    }

    @Override
    @Transactional
    public ProblemListSummaryVO updateVisibility(String id, String userId, UpdateVisibilityDTO dto) {
        ProblemList list = problemListMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

        if (!list.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_CANNOT_EDIT);
        }

        if (dto.getIsPublic() != null) {
            list.setIsPublic(dto.getIsPublic());
        }
        if (dto.getIsFeatured() != null) {
            list.setIsFeatured(dto.getIsFeatured());
        }

        problemListMapper.updateById(list);

        return toSummaryVOWithSavedStatus(list, userId);
    }

    @Override
    @Transactional
    public ProblemListSummaryVO updateBanner(String id, String userId, UpdateBannerDTO dto) {
        ProblemList list = problemListMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

        if (!list.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_CANNOT_EDIT);
        }

        if (dto.getBannerTag() != null) {
            list.setBannerTag(dto.getBannerTag());
        }
        if (dto.getBannerTheme() != null) {
            list.setBannerTheme(dto.getBannerTheme());
        }
        if (dto.getBannerOrder() != null) {
            list.setBannerOrder(dto.getBannerOrder());
        }

        problemListMapper.updateById(list);

        return toSummaryVOWithSavedStatus(list, userId);
    }

    @Override
    @Transactional
    public ProblemListSummaryVO updateList(String id, String userId, UpdateProblemListDTO dto) {
        ProblemList list = problemListMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

        if (!list.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_CANNOT_EDIT);
        }

        if (dto.getName() != null) {
            list.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            list.setDescription(dto.getDescription());
        }
        if (dto.getIsPublic() != null) {
            list.setIsPublic(dto.getIsPublic());
        }
        if (dto.getBannerTag() != null) {
            list.setBannerTag(dto.getBannerTag());
        }
        if (dto.getBannerIcon() != null) {
            list.setBannerIcon(dto.getBannerIcon());
        }
        if (dto.getBannerTheme() != null) {
            list.setBannerTheme(dto.getBannerTheme());
        }
        if (dto.getBannerOrder() != null) {
            list.setBannerOrder(dto.getBannerOrder());
        }
        if (dto.getIsFeatured() != null) {
            list.setIsFeatured(dto.getIsFeatured());
        }

        problemListMapper.updateById(list);

        return toSummaryVOWithSavedStatus(list, userId);
    }

    @Override
    @Transactional
    public void deleteList(String id, String userId) {
        ProblemList list = problemListMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

        if (!list.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_CANNOT_EDIT);
        }

        // Delete all problem relations
        problemListProblemMapper.deleteByListId(id);

        // Delete the list
        problemListMapper.deleteById(id);
    }

    @Override
    @Transactional
    public String forkList(String id, String userId) {
        ProblemList original = problemListMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

        if (!original.getIsPublic()) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_PRIVATE);
        }

        // Create new list
        ProblemList newList = new ProblemList();
        newList.setName(original.getName() + " (Fork)");
        newList.setDescription(original.getDescription());
        newList.setAuthorId(userId);
        newList.setIsPublic(false);
        newList.setIsFeatured(false);

        problemListMapper.insert(newList);

        // Copy problems
        List<ProblemListProblemRelation> relations = problemListProblemMapper.findByListId(id);
        for (ProblemListProblemRelation rel : relations) {
            ProblemListProblemRelation newRel = new ProblemListProblemRelation();
            newRel.setListId(newList.getId());
            newRel.setProblemId(rel.getProblemId());
            newRel.setSortOrder(rel.getSortOrder());
            problemListProblemMapper.insert(newRel);
        }

        return newList.getId();
    }

    @Override
    @Transactional
    public void addProblem(String listId, String userId, Long problemId) {
        ProblemList list = problemListMapper.findById(listId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

        if (!list.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_CANNOT_EDIT);
        }

        // Check if problem exists
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        // Check if already exists
        if (problemListProblemMapper.findByListIdAndProblemId(listId, problemId).isPresent()) {
            return; // Already in list, no-op
        }

        // Get max sort order
        Integer maxOrder = problemListProblemMapper.getMaxSortOrder(listId);
        int sortOrder = (maxOrder != null) ? maxOrder + 1 : 0;

        ProblemListProblemRelation relation = new ProblemListProblemRelation();
        relation.setListId(listId);
        relation.setProblemId(problemId);
        relation.setSortOrder(sortOrder);

        problemListProblemMapper.insert(relation);
    }

    @Override
    @Transactional
    public void removeProblem(String listId, String userId, Long problemId) {
        ProblemList list = problemListMapper.findById(listId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

        if (!list.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_CANNOT_EDIT);
        }

        problemListProblemMapper.deleteByListIdAndProblemId(listId, problemId);
    }

    @Override
    @Transactional
    public void saveList(String userId, String listId, String categoryId) {
        ProblemList list = problemListMapper.findById(listId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

        if (!list.getIsPublic() && !list.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_PRIVATE);
        }

        // Check if already saved
        if (problemListBookmarkMapper.existsByUserIdAndListId(userId, listId)) {
            return; // Already saved, no-op
        }

        // Validate category if provided
        if (categoryId != null) {
            ProblemListCategory category = problemListCategoryMapper.findById(categoryId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            if (!category.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        ProblemListBookmark bookmark = new ProblemListBookmark();
        bookmark.setUserId(userId);
        bookmark.setListId(listId);
        bookmark.setCategoryId(categoryId);

        problemListBookmarkMapper.insert(bookmark);
    }

    @Override
    @Transactional
    public void unsaveList(String userId, String listId) {
        problemListBookmarkMapper.deleteByUserIdAndListId(userId, listId);
    }

    @Override
    public UserListsForProblemVO getUserListsForProblem(String userId, Long problemId) {
        UserListsForProblemVO result = new UserListsForProblemVO();
        result.setProblemId(problemId);

        List<ProblemList> userLists = problemListMapper.findByAuthorId(userId);
        List<UserListsForProblemVO.ListStatusVO> listStatuses = userLists.stream()
                .map(list -> {
                    UserListsForProblemVO.ListStatusVO status = new UserListsForProblemVO.ListStatusVO();
                    status.setId(list.getId());
                    status.setName(list.getName());
                    status.setHasProblem(
                            problemListProblemMapper.findByListIdAndProblemId(list.getId(), problemId).isPresent());
                    status.setProblemCount((int) problemListProblemMapper.countByListId(list.getId()));
                    status.setCanEdit(true);
                    return status;
                })
                .collect(Collectors.toList());

        result.setLists(listStatuses);
        return result;
    }

    @Override
    @Transactional
    public void batchAddProblemToLists(String userId, Long problemId, List<String> listIds) {
        for (String listId : listIds) {
            addProblem(listId, userId, problemId);
        }
    }

    @Override
    @Transactional
    public void batchRemoveProblemFromLists(String userId, Long problemId, List<String> listIds) {
        for (String listId : listIds) {
            removeProblem(listId, userId, problemId);
        }
    }

    @Override
    @Transactional
    public void moveListToCategory(String userId, String listId, String categoryId) {
        // NOTE: This method requires the problem_list_bookmarks table to exist
        ProblemListBookmark bookmark = problemListBookmarkMapper.findByUserIdAndListId(userId, listId)
                .orElse(null);

        if (bookmark == null) {
            // If not saved yet, save it to the category
            saveList(userId, listId, categoryId);
            return;
        }

        bookmark.setCategoryId(categoryId);
        problemListBookmarkMapper.updateById(bookmark);
    }

    @Override
    @Transactional
    public CategorySummaryVO createCategory(String userId, CreateCategoryDTO dto) {
        // NOTE: This method requires the problem_list_categories table to exist
        // Check if category name already exists for user
        if (problemListCategoryMapper.findByUserIdAndName(userId, dto.getName()).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        Integer maxOrder = problemListCategoryMapper.getMaxSortOrder(userId);
        int sortOrder = (maxOrder != null) ? maxOrder + 1 : 0;

        ProblemListCategory category = new ProblemListCategory();
        category.setUserId(userId);
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setIcon(dto.getIcon());
        category.setColor(dto.getColor());
        category.setSortOrder(sortOrder);

        problemListCategoryMapper.insert(category);

        return toCategorySummaryVO(category);
    }

    @Override
    @Transactional
    public CategorySummaryVO updateCategory(String categoryId, String userId, UpdateCategoryDTO dto) {
        // NOTE: This method requires the problem_list_categories table to exist
        ProblemListCategory category = problemListCategoryMapper.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!category.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (dto.getName() != null) {
            category.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            category.setDescription(dto.getDescription());
        }
        if (dto.getIcon() != null) {
            category.setIcon(dto.getIcon());
        }
        if (dto.getColor() != null) {
            category.setColor(dto.getColor());
        }
        if (dto.getSortOrder() != null) {
            category.setSortOrder(dto.getSortOrder());
        }

        problemListCategoryMapper.updateById(category);

        return toCategorySummaryVO(category);
    }

    @Override
    @Transactional
    public void deleteCategory(String categoryId, String userId) {
        // NOTE: This method requires the problem_list_categories table to exist
        ProblemListCategory category = problemListCategoryMapper.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!category.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // Remove category from bookmarks
        List<ProblemListBookmark> bookmarks = problemListBookmarkMapper.findByCategoryId(categoryId);
        for (ProblemListBookmark bookmark : bookmarks) {
            bookmark.setCategoryId(null);
            problemListBookmarkMapper.updateById(bookmark);
        }

        // Delete category
        problemListCategoryMapper.deleteById(categoryId);
    }

    // ==================== Helper Methods ====================

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

    private ProblemListSummaryVO toSummaryVOWithSavedStatus(ProblemList list, String userId) {
        ProblemListSummaryVO vo = toSummaryVO(list);
        if (userId != null) {
            vo.setIsSaved(problemListBookmarkMapper.existsByUserIdAndListId(userId, list.getId()));
        } else {
            vo.setIsSaved(false);
        }
        return vo;
    }

    private CategorySummaryVO toCategorySummaryVO(ProblemListCategory category) {
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
}
