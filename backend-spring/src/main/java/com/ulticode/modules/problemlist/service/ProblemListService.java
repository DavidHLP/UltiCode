package com.ulticode.modules.problemlist.service;

import com.ulticode.modules.problemlist.dto.CategorySummaryVO;
import com.ulticode.modules.problemlist.dto.CreateCategoryDTO;
import com.ulticode.modules.problemlist.dto.CreateProblemListDTO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateCategoryDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;

import java.util.List;

/**
 * Write-side facade for the problem-list domain — the list state machine plus
 * the bookmark / category mutations.
 *
 * <p>Read paths (overview lists, list detail, user-lists-for-problem) were
 * lifted into
 * {@link com.ulticode.modules.problemlist.projection.ProblemListProjection};
 * the controller depends on that projection directly for reads and on this
 * service for writes. Write paths shape their return values through
 * {@link com.ulticode.modules.problemlist.projection.ProblemListProjection#toSummaryVO}
 * / {@code toSummaryVOWithSavedStatus} / {@code toCategorySummaryVO}.
 */
public interface ProblemListService {

    /**
     * Create a new problem list.
     *
     * @param userId the user ID
     * @param dto    the create problem list DTO
     * @return the created problem list
     */
    ProblemListSummaryVO createList(String userId, CreateProblemListDTO dto);

    /**
     * Update a problem list (full/partial).
     *
     * @param id     the list ID
     * @param userId the user ID
     * @param dto    the update problem list DTO
     * @return the updated problem list
     */
    ProblemListSummaryVO updateList(String id, String userId, UpdateProblemListDTO dto);

    /**
     * Delete a problem list.
     *
     * @param id     the list ID
     * @param userId the user ID
     */
    void deleteList(String id, String userId);

    /**
     * Fork a problem list.
     *
     * @param id     the list ID to fork
     * @param userId the user ID
     * @return the new problem list summary (full VO, aligned with createList)
     */
    ProblemListSummaryVO forkList(String id, String userId);

    /**
     * Add a problem to a list.
     *
     * @param listId    the list ID
     * @param userId    the user ID
     * @param problemId the problem ID
     */
    void addProblem(String listId, String userId, Long problemId);

    /**
     * Remove a problem from a list.
     *
     * @param listId    the list ID
     * @param userId    the user ID
     * @param problemId the problem ID
     */
    void removeProblem(String listId, String userId, Long problemId);

    /**
     * Save a problem list to user's saved lists.
     *
     * @param userId     the user ID
     * @param listId     the list ID
     * @param categoryId the category ID (optional)
     */
    void saveList(String userId, String listId, String categoryId);

    /**
     * Unsave a problem list from user's saved lists.
     *
     * @param userId the user ID
     * @param listId the list ID
     */
    void unsaveList(String userId, String listId);

    /**
     * Batch add a problem to multiple lists.
     *
     * @param userId    the user ID
     * @param problemId the problem ID
     * @param listIds   the list IDs
     */
    void batchAddProblemToLists(String userId, Long problemId, List<String> listIds);

    /**
     * Batch remove a problem from multiple lists.
     *
     * @param userId    the user ID
     * @param problemId the problem ID
     * @param listIds   the list IDs
     */
    void batchRemoveProblemFromLists(String userId, Long problemId, List<String> listIds);

    /**
     * Move a list to a category.
     *
     * @param userId     the user ID
     * @param listId     the list ID
     * @param categoryId the category ID (null to uncategorize)
     */
    void moveListToCategory(String userId, String listId, String categoryId);

    // ==================== Category Management ====================

    /**
     * Create a category.
     *
     * @param userId the user ID
     * @param dto    the create category DTO
     * @return the created category
     */
    CategorySummaryVO createCategory(String userId, CreateCategoryDTO dto);

    /**
     * Update a category.
     *
     * @param categoryId the category ID
     * @param userId     the user ID
     * @param dto        the update category DTO
     * @return the updated category
     */
    CategorySummaryVO updateCategory(String categoryId, String userId, UpdateCategoryDTO dto);

    /**
     * Delete a category.
     *
     * @param categoryId the category ID
     * @param userId     the user ID
     */
    void deleteCategory(String categoryId, String userId);

    ProblemListSummaryVO updateBasicInfo(String id, String userId, UpdateBasicInfoDTO dto);

    ProblemListSummaryVO updateVisibility(String id, String userId, UpdateVisibilityDTO dto);

    ProblemListSummaryVO updateBanner(String id, String userId, UpdateBannerDTO dto);
}
