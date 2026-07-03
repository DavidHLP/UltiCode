package com.ulticode.modules.problemlist.service.impl;

import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.modules.problemlist.dto.CreateCategoryDTO;
import com.ulticode.modules.problemlist.dto.CreateProblemListDTO;
import com.ulticode.modules.problemlist.dto.CategorySummaryVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateCategoryDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListBookmark;
import com.ulticode.modules.problemlist.entity.ProblemListCategory;
import com.ulticode.modules.problemlist.entity.ProblemListProblemRelation;
import com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import com.ulticode.modules.problemlist.projection.ProblemListProjection;
import com.ulticode.modules.problemlist.service.ProblemListService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Write-side facade for problem-list operations. Owns the list state machine
 * (create / update / delete / fork / addProblem / removeProblem / save /
 * batch / move / category CRUD).
 *
 * <p>All read paths — overview lists, list detail, user-lists-for-problem, and
 * the entity-to-VO projection helpers — live in {@link ProblemListProjection}.
 * Write paths shape their return values through
 * {@link ProblemListProjection#toSummaryVO} /
 * {@link ProblemListProjection#toSummaryVOWithSavedStatus} /
 * {@link ProblemListProjection#toCategorySummaryVO}.
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
    private final ProblemListProjection problemListProjection;

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

        ProblemListSummaryVO vo = problemListProjection.toSummaryVO(list);
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

        return problemListProjection.toSummaryVOWithSavedStatus(list, userId);
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

        return problemListProjection.toSummaryVOWithSavedStatus(list, userId);
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

        return problemListProjection.toSummaryVOWithSavedStatus(list, userId);
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

        return problemListProjection.toSummaryVOWithSavedStatus(list, userId);
    }

    @Override
    @Transactional
    @Audited(
            action = AuditActionUtil.DELETE_PROBLEM_LIST,
            entityType = AuditActionUtil.ENTITY_PROBLEM_LIST,
            userIdFrom = "userId", entityIdFrom = "id")
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
    public ProblemListSummaryVO forkList(String id, String userId) {
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

        // Return full VO aligned with createList() contract (was: id-only String).
        return problemListProjection.toSummaryVO(newList);
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

        // Check if already exists — throw BusinessException so API returns 409 instead of silent no-op.
        // Note: this is a fast-path check. The database-level PRIMARY KEY (problem_id, list_id)
        // enforces uniqueness regardless, and the catch below converts any race-condition
        // duplicate into the same BusinessException for a deterministic 409 contract.
        if (problemListProblemMapper.findByListIdAndProblemId(listId, problemId).isPresent()) {
            throw new BusinessException(ErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE);
        }

        // Get max sort order
        Integer maxOrder = problemListProblemMapper.getMaxSortOrder(listId);
        int sortOrder = (maxOrder != null) ? maxOrder + 1 : 0;

        ProblemListProblemRelation relation = new ProblemListProblemRelation();
        relation.setListId(listId);
        relation.setProblemId(problemId);
        relation.setSortOrder(sortOrder);

        try {
            problemListProblemMapper.insert(relation);
        } catch (DuplicateKeyException e) {
            // Concurrent insert won the race; treat as duplicate per the same 409 contract.
            log.debug("addProblem lost duplicate race for list={} problem={}", listId, problemId);
            throw new BusinessException(ErrorCode.PROBLEM_LIST_PROBLEM_DUPLICATE);
        }
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

        return problemListProjection.toCategorySummaryVO(category);
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

        return problemListProjection.toCategorySummaryVO(category);
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
}
