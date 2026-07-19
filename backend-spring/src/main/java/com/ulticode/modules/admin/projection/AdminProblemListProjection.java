package com.ulticode.modules.admin.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;

/**
 * Read-side deep module for the admin problem-list management surface &mdash;
 * owns the paginated list-query builder, the single-detail read, and the
 * entity-to-VO projection rules that previously leaked across the
 * {@link com.ulticode.modules.admin.service.AdminProblemListService} seam.
 *
 * <p>Lifted out per architecture-review 2026-07-19 candidate #3. Before the
 * extraction, the admin service built its own {@code LambdaQueryWrapper},
 * ran {@code selectPage}, then mapped each entity through the cross-module
 * {@code ProblemListProjection.toSummaryVO} conversion helper &mdash; the
 * page-assembly mechanics and the conversion helper leaked across the
 * module boundary, and the feature-side {@code ProblemListProjection} had
 * to import {@code AdminProblemListQueryDTO} (admin DTO) just to host the
 * admin read. This projection restores the dependency direction: admin
 * depends on feature (mapper + entity), never the reverse.
 *
 * <p>Mirrors the {@link AdminContestProjection} /
 * {@link AdminSubmissionProjection} / {@link AdminUserProjection} /
 * {@link AdminSolutionProjection} / {@link AdminForumProjection} shape
 * exactly. The admin service keeps only its write state machine + audit
 * context; this projection owns the reads.
 *
 * @author ulticode
 * @see AdminContestProjection
 * @see com.ulticode.modules.problemlist.projection.ProblemListProjection
 */
public interface AdminProblemListProjection {

    /**
     * Paginated, filtered list of problem-list summary VOs the management
     * console renders. Owns the page normalization
     * ({@link com.ulticode.common.response.PaginationRequest#of} with the
     * admin default of 10), the {@code LambdaQueryWrapper} assembly for
     * search / featured / public filters and the sort selector, the
     * {@code selectPage} call, and the entity → summary projection.
     *
     * @param query admin query (search / filters / pagination / sort)
     * @return paged summary VOs with the platform-standard pagination envelope
     */
    PageResult<ProblemListSummaryVO> findAdminLists(AdminProblemListQueryDTO query);

    /**
     * Admin detail: load and project a problem-list into the detail VO the
     * management console renders. Owns the entity load (404 on missing),
     * the batched problem / tag enrichment, the solved / attempted / todo
     * stats aggregation, and the admin-specific shaping (no viewer state,
     * no categories).
     *
     * @param id list ID
     * @return admin-facing detail VO
     */
    ProblemListDetailVO getAdminListDetail(String id);
}
