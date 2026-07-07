package com.ulticode.modules.admin.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminSolutionListItemVO;
import com.ulticode.modules.admin.dto.AdminSolutionQueryDTO;
import com.ulticode.modules.admin.dto.AdminSolutionVO;

/**
 * Read-side projection for admin solution management &mdash; a deep module
 * that owns every entity-to-VO projection rule, paginated list-query builder
 * (including the soft-deleted raw-SQL path), flagged-list derivation, and
 * single-detail enrichment for the admin solution surface.
 *
 * <p>This is the same shallow cluster lifted out of
 * {@link com.ulticode.modules.admin.service.AdminSolutionService} for the
 * Stage 2 rollout of ADR-0011: the paginated list read
 * ({@link #getSolutions}, ~100 LoC of query building + batch user/problem
 * enrichment + N+1-safe VO shaping across two DB-union branches
 * (active via MyBatis-Plus {@code LambdaQueryWrapper}, soft-deleted via the
 * raw-SQL {@code selectDeletedSolutions}/{@code countDeletedSolutions} pair),
 * the flagged-list derivation ({@link #getFlaggedSolutions}, which forces
 * {@code isDeleted=false} and delegates to {@link #getSolutions}), and the
 * single-detail read ({@link #getSolution}, which enriches inline because
 * the row volume is 1). Sitting next to the {@code @Audited} flag / unflag /
 * delete / bulk state machine in the same service made every projection
 * tweak land in the same file as the write paths.
 *
 * <p>After the deepening:
 * <ul>
 *   <li>{@link com.ulticode.modules.admin.service.AdminSolutionService}
 *       keeps the write state machine only (flag / unflag / soft-delete /
 *       bulk action, all {@code @Audited}). Write paths return
 *       {@code AdminSolutionVO} by delegating to {@link #getSolution} for
 *       post-write VO composition and never call the other read methods.</li>
 *   <li>The controller depends on this projection directly for reads and on
 *       the service for writes.</li>
 * </ul>
 *
 * <p>All methods are pure reads; none mutate solution state. The single-item
 * read throws {@link com.ulticode.common.exception.ErrorCode#SOLUTION_NOT_FOUND}
 * to preserve the access contract observed by the controller.
 *
 * <p>Mirrors the {@link AdminSubmissionProjection} /
 * {@link com.ulticode.modules.admin.projection.AdminUserProjection} /
 * {@link com.ulticode.modules.problemlist.projection.ProblemListProjection}
 * shape exactly. Cross-module entity imports ({@code User}, {@code Problem})
 * live behind this seam; the admin solution service no longer imports them.
 *
 * @author ulticode
 * @see AdminSubmissionProjection
 * @see com.ulticode.modules.problemlist.projection.ProblemListProjection
 */
public interface AdminSolutionProjection {

    /**
     * Get a paginated list of solutions with filters (search, user, problem,
     * flagged, published, soft-deleted) and sorting. Cross-module enrichment
     * (author info, problem info) is batch-loaded to avoid N+1.
     *
     * <p>When {@code query.isDeleted == true}, the read bypasses MyBatis-Plus
     * logical-delete filtering via the raw-SQL
     * {@code selectDeletedSolutions}/{@code countDeletedSolutions} pair so
     * admins can audit removed rows; otherwise the standard
     * {@code LambdaQueryWrapper} path applies.
     *
     * @param query query parameters including filters, pagination, and sorting
     * @return paginated result of admin solution list-item VOs
     */
    PageResult<AdminSolutionListItemVO> getSolutions(AdminSolutionQueryDTO query);

    /**
     * Get a paginated list of currently-active (non-deleted) flagged
     * solutions. Forces {@code isDeleted=false} regardless of the caller's
     * query so the {@code /admin/solutions/flagged} endpoint title stays
     * truthful (BUG-Q9).
     *
     * @param query query parameters (the {@code isFlagged} and
     *              {@code isDeleted} fields are overridden)
     * @return paginated result of admin solution list-item VOs
     */
    PageResult<AdminSolutionListItemVO> getFlaggedSolutions(AdminSolutionQueryDTO query);

    /**
     * Get a single solution by ID with full detail fields (content, summary,
     * tags, audit fields) plus inline author / problem enrichment.
     *
     * @param id solution ID
     * @return admin solution VO with full details
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#SOLUTION_NOT_FOUND}
     *         when the solution does not exist
     */
    AdminSolutionVO getSolution(String id);
}
