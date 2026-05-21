package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminSolutionListItemVO;
import com.ulticode.modules.admin.dto.AdminSolutionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSolutionVO;

import java.util.List;

/**
 * Service interface for admin solution operations.
 */
public interface AdminSolutionService {

    /**
     * Get paginated list of solutions with filters.
     *
     * @param query the query parameters
     * @return paginated list of solutions
     */
    PageResult<AdminSolutionListItemVO> getSolutions(AdminSolutionQueryDTO query);

    /**
     * Get paginated list of flagged solutions.
     *
     * @param query the query parameters
     * @return paginated list of flagged solutions
     */
    PageResult<AdminSolutionListItemVO> getFlaggedSolutions(AdminSolutionQueryDTO query);

    /**
     * Get a solution by ID.
     *
     * @param id the solution ID
     * @return the solution VO
     */
    AdminSolutionVO getSolution(String id);

    /**
     * Flag a solution for review.
     *
     * @param id      the solution ID
     * @param reason  the reason for flagging
     * @param adminId the admin user ID who performed the action
     * @return the updated solution VO
     */
    AdminSolutionVO flagSolution(String id, String reason, String adminId);

    /**
     * Unflag a solution (remove flag).
     *
     * @param id the solution ID
     * @return the updated solution VO
     */
    AdminSolutionVO unflagSolution(String id);

    /**
     * Delete a solution (hard delete).
     *
     * @param id the solution ID
     */
    void deleteSolution(String id);

    /**
     * Bulk action on multiple solutions.
     *
     * @param ids   the solution IDs
     * @param action the action to perform (publish, unpublish, delete, unflag)
     * @return list of action results
     */
    List<BulkActionResult> bulkAction(List<String> ids, String action);

    /**
     * Result of a bulk action.
     */
    record BulkActionResult(
            String id,
            boolean success,
            String error
    ) {
        public static BulkActionResult success(String id) {
            return new BulkActionResult(id, true, null);
        }

        public static BulkActionResult failure(String id, String error) {
            return new BulkActionResult(id, false, error);
        }
    }
}
