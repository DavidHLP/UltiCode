package com.ulticode.modules.problemlist.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminProblemListQueryDTO;
import com.ulticode.modules.problemlist.dto.CategorySummaryVO;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UserListsForProblemVO;
import com.ulticode.modules.problemlist.dto.UserProblemListsVO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListCategory;

/**
 * Read-side projection for the problem-list domain — a deep module that owns
 * every entity-to-VO projection rule, list-query builder and read-side
 * aggregation for both the user-facing console and the management console.
 *
 * <p>Three kinds of reads cross this seam:
 * <ul>
 *   <li><b>User-facing overview reads</b> — {@link #findAll},
 *       {@link #getUserProblemLists}, {@link #getListOverview},
 *       {@link #getUserListsForProblem}. Each returns a fully-shaped VO
 *       (batched problem / tag enrichment, solved / attempted / todo stats,
 *       viewer state, saved status) so callers never see query assembly.</li>
 *   <li><b>Admin intent-level reads</b> — {@link #findAdminLists} and
 *       {@link #getAdminListDetail}. These own the page query, the
 *       filter-wrapper assembly, the entity load, and the entity-to-VO
 *       projection internally; the management console asks for an admin
 *       list page or admin detail and receives a typed VO. Added in
 *       architecture-review 2026-07-19 candidate #3 to retire the
 *       cross-module conversion helper {@code toAdminDetailVO} that
 *       previously leaked through this interface.</li>
 *   <li><b>In-module write-side conversion helpers</b> — {@link #toSummaryVO},
 *       {@link #toSummaryVOWithSavedStatus}, {@link #toCategorySummaryVO}.
 *       Exposed because the write state machine in
 *       {@link com.ulticode.modules.problemlist.service.ProblemListService}
 *       legitimately needs to shape create / update / fork / category-CRUD
 *       return values without duplicating the projection rules.</li>
 * </ul>
 *
 * <p>All methods are pure reads; none mutate list state. Single-item reads
 * throw {@link com.ulticode.common.exception.ErrorCode#PROBLEM_LIST_NOT_FOUND}
 * / {@link com.ulticode.common.exception.ErrorCode#PROBLEM_LIST_PRIVATE} to
 * preserve the access contract observed by the controller.
 *
 * @author ulticode
 */
public interface ProblemListProjection {

    /**
     * Find all public problem lists (featured + public), unauthenticated view.
     *
     * @param locale the locale for i18n
     * @return user problem lists overview (saved-lists / categories empty)
     */
    UserProblemListsVO findAll(String locale);

    /**
     * Get a user's problem-list overview (own + saved + featured + categories).
     *
     * @param userId the user ID
     * @return user problem lists overview
     */
    UserProblemListsVO getUserProblemLists(String userId);

    /**
     * Get a problem-list detail by ID with access check, batched problem/tag
     * enrichment, solved/attempted/todo stats, viewer state and category
     * options.
     *
     * @param id     the list ID
     * @param userId the current user ID (optional; drives ownership / saved / viewer state)
     * @param locale the locale for i18n
     * @return the problem list detail
     */
    ProblemListDetailVO getListOverview(String id, String userId, String locale);

    /**
     * Get the user's lists with per-list hasProblem + problemCount for a given
     * problem. Batch-loads the two aggregates in 2 queries (no N+1).
     *
     * @param userId    the user ID
     * @param problemId the problem ID
     * @return user lists for problem
     */
    UserListsForProblemVO getUserListsForProblem(String userId, Long problemId);

    /**
     * Admin overview: paged, filtered list of problem-list summary VOs the
     * management console renders. Owns the page normalization
     * ({@link com.ulticode.common.response.PaginationRequest#of} with the
     * admin default of 10), the {@code LambdaQueryWrapper} assembly for
     * search / featured / public filters and the sort selector, the
     * {@code selectPage} call, and the entity → summary projection. The
     * admin service is left with only the audit context it owns around the
     * call — no page-assembly mechanics cross the module boundary.
     *
     * <p>Architecture-review 2026-07-19 candidate #3: replaces the previous
     * pattern where the admin service built the wrapper, ran the page query,
     * then mapped each entity through {@link #toSummaryVO} — which forced
     * the conversion helper to be the cross-module API.
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
     * no categories). Replaces the cross-module conversion helper
     * {@code toAdminDetailVO} that previously leaked through this interface.
     *
     * @param id list ID
     * @return admin-facing detail VO
     */
    ProblemListDetailVO getAdminListDetail(String id);

    /**
     * Project a {@link ProblemList} entity into a {@link ProblemListSummaryVO}
     * (with problem-count and author enrichment). Exposed so the write-side
     * service can shape its return values without re-implementing the rule.
     *
     * @param list the list entity
     * @return the summary view object
     */
    ProblemListSummaryVO toSummaryVO(ProblemList list);

    /**
     * Project a {@link ProblemList} entity into a {@link ProblemListSummaryVO}
     * with the caller's saved-status attached.
     *
     * @param list   the list entity
     * @param userId the current user ID (optional)
     * @return the summary view object with isSaved populated
     */
    ProblemListSummaryVO toSummaryVOWithSavedStatus(ProblemList list, String userId);

    /**
     * Project a {@link ProblemListCategory} entity into a
     * {@link CategorySummaryVO} (with list-count enrichment). Exposed so the
     * write-side service can shape category-create / category-update returns.
     *
     * @param category the category entity
     * @return the category summary view object
     */
    CategorySummaryVO toCategorySummaryVO(ProblemListCategory category);
}
