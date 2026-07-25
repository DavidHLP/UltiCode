package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.AdminSolutionVO;

import java.util.List;

/**
 * Service interface for admin solution operations.
 *
 * <p><strong>Writes only</strong> after ADR-0011 Stage 2 extraction. Every
 * read-side concern (paginated list, flagged-list derivation, single-detail
 * enrichment) lives on
 * {@link com.ulticode.modules.admin.projection.AdminSolutionProjection}.
 * Write methods that return an {@link AdminSolutionVO} compose it by
 * delegating to {@code AdminSolutionProjection.getSolution(id)} for the
 * post-write VO shape (mirrors the AdminSubmission / AdminUser pattern).
 *
 * <p>All write methods are {@code @Audited}; entries are catalogued in
 * {@code common/audit/AuditPolicy} under
 * {@code com.ulticode.modules.admin.service.impl.AdminSolutionServiceImpl}.
 */
public interface AdminSolutionService {

    /**
     * Flag a solution for review.
     *
     * <p>The admin performer is resolved from the Spring Security context inside the
     * service / aspect; callers should not pass admin id as a parameter.
     *
     * @param id     the solution ID
     * @param reason the reason for flagging
     * @return the updated solution VO
     */
    AdminSolutionVO flagSolution(String id, String reason);

    /**
     * Unflag a solution (remove flag).
     *
     * @param id the solution ID
     * @return the updated solution VO
     */
    AdminSolutionVO unflagSolution(String id);

    /**
     * Soft-delete a solution by setting {@code is_deleted=1}.
     *
     * <p>The row remains in the database and can be inspected via the list endpoint
     * with {@code isDeleted=true}. Hard delete is not exposed in this version.
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
        /** Standard error message when a solution id is not found. */
        public static final String NOT_FOUND_MESSAGE = "Solution not found";

        public static BulkActionResult success(String id) {
            return new BulkActionResult(id, true, null);
        }

        public static BulkActionResult failure(String id, String error) {
            return new BulkActionResult(id, false, error);
        }
    }
}
