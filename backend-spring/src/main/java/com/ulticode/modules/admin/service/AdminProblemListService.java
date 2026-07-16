package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.CreateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListDTO;
import com.ulticode.modules.problemlist.dto.UpdateProblemListProblemsDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;

/**
 * Service interface for admin problem list operations.
 */
public interface AdminProblemListService {

    /**
     * Get paginated list of problem lists with filters.
     *
     * @param query the query parameters
     * @return paginated result of problem lists
     */
    PageResult<ProblemListSummaryVO> getProblemLists(AdminProblemListQueryDTO query);

    /**
     * Get a problem list by ID with full details.
     *
     * @param id the problem list ID
     * @return the problem list detail
     */
    ProblemListDetailVO getProblemList(String id);

    /**
     * Create a new problem list.
     *
     * @param dto the create problem list DTO
     * @param authorId the author ID
     * @return the created problem list
     */
    ProblemListSummaryVO createProblemList(CreateProblemListDTO dto, String authorId);

    /**
     * Update an existing problem list.
     *
     * @param id the problem list ID
     * @param dto the update problem list DTO
     * @param userId the admin user ID making the request
     * @return the updated problem list
     */
    ProblemListSummaryVO updateProblemList(String id, UpdateProblemListDTO dto, String userId);

    /**
     * Delete a problem list.
     *
     * @param id the problem list ID
     */
    void deleteProblemList(String id, String userId);

    /**
     * Update the problems in a problem list (full replacement).
     *
     * @param id the problem list ID
     * @param dto the update DTO containing the new problem list
     */
    void updateListProblems(String id, UpdateProblemListProblemsDTO dto, String userId);

    /**
     * Update basic info of a problem list.
     *
     * @param id the problem list ID
     * @param userId the admin user ID making the request
     * @param dto the update basic info DTO
     * @return the updated problem list
     */
    ProblemListSummaryVO updateBasicInfo(String id, String userId, UpdateBasicInfoDTO dto);

    /**
     * Update visibility of a problem list.
     *
     * @param id the problem list ID
     * @param userId the admin user ID making the request
     * @param dto the update visibility DTO
     * @return the updated problem list
     */
    ProblemListSummaryVO updateVisibility(String id, String userId, UpdateVisibilityDTO dto);

    /**
     * Update banner of a problem list.
     *
     * @param id the problem list ID
     * @param userId the admin user ID making the request
     * @param dto the update banner DTO
     * @return the updated problem list
     */
    ProblemListSummaryVO updateBanner(String id, String userId, UpdateBannerDTO dto);
}
