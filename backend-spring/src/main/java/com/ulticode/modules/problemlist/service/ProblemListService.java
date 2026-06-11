package com.ulticode.modules.problemlist.service;

import com.ulticode.modules.problemlist.dto.*;

import java.util.List;

/**
 * Service interface for problem list operations.
 */
public interface ProblemListService {

    /**
     * Find all public problem lists.
     *
     * @param locale the locale for i18n
     * @return user problem lists overview
     */
    UserProblemListsVO findAll(String locale);

    /**
     * Get a user's problem lists overview.
     *
     * @param userId the user ID
     * @return user problem lists overview
     */
    UserProblemListsVO getUserProblemLists(String userId);

    /**
     * Get a problem list overview by ID.
     *
     * @param id     the list ID
     * @param userId the current user ID (optional)
     * @param locale the locale for i18n
     * @return the problem list detail
     */
    ProblemListDetailVO getListOverview(String id, String userId, String locale);

    /**
     * Create a new problem list.
     *
     * @param userId the user ID
     * @param dto    the create problem list DTO
     * @return the created problem list
     */
    ProblemListSummaryVO createList(String userId, CreateProblemListDTO dto);

    /**
     * Update a problem list.
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
     * Get user's lists for a specific problem.
     *
     * @param userId    the user ID
     * @param problemId the problem ID
     * @return user lists for problem
     */
    UserListsForProblemVO getUserListsForProblem(String userId, Long problemId);

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
