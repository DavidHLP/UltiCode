package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.ProblemListDetailDTO;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;

/**
 * Read-side deep module for the admin problem-list management surface &mdash;
 * owns the paginated list-query read, the single-detail read, the author
 * enrichment, and the admin-specific detail shaping.
 *
 * <p>ADMIN-005 (P7-RELOCATE-PROBLEMLIST-001): all reads are backed by the
 * entity-free app-api read ports ({@code ProblemListSearchReadPort} /
 * {@code ProblemListChainReadPort}) consumed over Dubbo; the projection no
 * longer imports any App-private entity or mapper. The admin service keeps
 * only its write state machine + audit context; this projection owns the
 * reads.
 *
 * <p>Mirrors the {@link AdminContestProjection} /
 * {@link AdminSubmissionProjection} / {@link AdminUserProjection} shape.
 */
public interface AdminProblemListProjection {

    /**
     * Paginated, filtered list of problem-list summary DTOs the management
     * console renders. Owns the page normalization
     * ({@link com.ulticode.common.response.PaginationRequest#of} with the
     * admin default of 10) and the author enrichment on top of the remote
     * search provider's paged result.
     *
     * @param query admin query (search / filters / pagination / sort)
     * @return paged summary DTOs with the platform-standard pagination envelope
     */
    PageResult<ProblemListSummaryDTO> findAdminLists(AdminProblemListQueryDTO query);

    /**
     * Admin detail: load and project a problem-list into the detail DTO the
     * management console renders. Owns the remote chain read (404 on
     * missing), the author enrichment, the solved / attempted / todo stats
     * aggregation, and the admin-specific shaping (no viewer state, no
     * categories).
     *
     * @param id list ID
     * @return admin-facing detail DTO
     */
    ProblemListDetailDTO getAdminListDetail(String id);
}
