package com.ulticode.modules.admin.service;

import com.ulticode.app.api.dto.ProblemListDetailDTO;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.admin.dto.CreateProblemListRequest;
import com.ulticode.modules.admin.dto.UpdateBannerRequest;
import com.ulticode.modules.admin.dto.UpdateBasicInfoRequest;
import com.ulticode.modules.admin.dto.UpdateProblemListRequest;
import com.ulticode.modules.admin.dto.UpdateProblemsRequest;
import com.ulticode.modules.admin.dto.UpdateVisibilityRequest;

/**
 * Service interface for admin problem list operations.
 *
 * <p>P7-RELOCATE-PROBLEMLIST-001: return and request types are the
 * entity-free app-api DTOs / admin request DTOs; writes route through
 * {@code ProblemListAdministrationService} (Dubbo), reads through the
 * admin projection backed by {@code ProblemListSearchReadPort} /
 * {@code ProblemListChainReadPort}.
 */
public interface AdminProblemListService {

    /**
     * Get paginated list of problem lists with filters.
     *
     * @param query the query parameters
     * @return paginated result of problem lists
     */
    PageResult<ProblemListSummaryDTO> getProblemLists(AdminProblemListQueryDTO query);

    /**
     * Get a problem list by ID with full details.
     *
     * @param id the problem list ID
     * @return the problem list detail
     */
    ProblemListDetailDTO getProblemList(String id);

    /**
     * Create a new problem list.
     *
     * @param dto      the create problem list request
     * @param authorId the author ID
     * @return the created problem list
     */
    ProblemListSummaryDTO createProblemList(CreateProblemListRequest dto, String authorId);
    default ProblemListSummaryDTO createProblemList(
            CreateProblemListRequest dto, String authorId, String idempotencyKey) {
        return createProblemList(dto, authorId);
    }

    /**
     * Update an existing problem list.
     *
     * @param id     the problem list ID
     * @param dto    the update problem list request
     * @param userId the admin user ID making the request
     * @return the updated problem list
     */
    ProblemListSummaryDTO updateProblemList(String id, UpdateProblemListRequest dto, String userId);
    default ProblemListSummaryDTO updateProblemList(
            String id, UpdateProblemListRequest dto, String userId, String idempotencyKey) {
        return updateProblemList(id, dto, userId);
    }

    /**
     * Delete a problem list.
     *
     * @param id     the problem list ID
     * @param userId the admin user ID making the request
     */
    void deleteProblemList(String id, String userId);
    default void deleteProblemList(String id, String userId, String idempotencyKey) {
        deleteProblemList(id, userId);
    }

    /**
     * Update the problems in a problem list (full replacement).
     *
     * @param id     the problem list ID
     * @param dto    the update request containing the new problems
     * @param userId the admin user ID making the request
     */
    void updateListProblems(String id, UpdateProblemsRequest dto, String userId);
    default void updateListProblems(
            String id, UpdateProblemsRequest dto, String userId, String idempotencyKey) {
        updateListProblems(id, dto, userId);
    }

    /**
     * Update basic info of a problem list.
     *
     * @param id     the problem list ID
     * @param userId the admin user ID making the request
     * @param dto    the update basic info request
     * @return the updated problem list
     */
    ProblemListSummaryDTO updateBasicInfo(String id, String userId, UpdateBasicInfoRequest dto);
    default ProblemListSummaryDTO updateBasicInfo(
            String id, String userId, UpdateBasicInfoRequest dto, String idempotencyKey) {
        return updateBasicInfo(id, userId, dto);
    }

    /**
     * Update visibility of a problem list.
     *
     * @param id     the problem list ID
     * @param userId the admin user ID making the request
     * @param dto    the update visibility request
     * @return the updated problem list
     */
    ProblemListSummaryDTO updateVisibility(String id, String userId, UpdateVisibilityRequest dto);
    default ProblemListSummaryDTO updateVisibility(
            String id, String userId, UpdateVisibilityRequest dto, String idempotencyKey) {
        return updateVisibility(id, userId, dto);
    }

    /**
     * Update banner of a problem list.
     *
     * @param id     the problem list ID
     * @param userId the admin user ID making the request
     * @param dto    the update banner request
     * @return the updated problem list
     */
    ProblemListSummaryDTO updateBanner(String id, String userId, UpdateBannerRequest dto);
    default ProblemListSummaryDTO updateBanner(
            String id, String userId, UpdateBannerRequest dto, String idempotencyKey) {
        return updateBanner(id, userId, dto);
    }
}
