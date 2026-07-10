package com.ulticode.modules.problemlist.projection;

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
 * aggregation.
 *
 * <p>This is the same shallow cluster lifted out of
 * {@link com.ulticode.modules.problemlist.service.ProblemListService} for
 * moderation, submission, problem and contest: three projection helpers
 * ({@link #toSummaryVO}, {@link #toSummaryVOWithSavedStatus},
 * {@link #toCategorySummaryVO}, each carrying cross-mapper enrichment —
 * problem count, author info, saved-status, category list count), three
 * list-overview reads ({@link #findAll}, {@link #getUserProblemLists},
 * {@link #getUserListsForProblem}) and the heavyweight detail projection
 * {@link #getListOverview} (~140 LoC of entity-to-DetailVO shaping, batched
 * problem/tag enrichment, solved/attempted/todo stats aggregation, viewer
 * state and category options). Sitting next to the problem-list state machine
 * (create / update / delete / fork / addProblem / removeProblem / save /
 * category CRUD) made every projection tweak land in the same file as the
 * write paths.
 *
 * <p>After the deepening:
 * <ul>
 *   <li>{@link com.ulticode.modules.problemlist.service.ProblemListService}
 *       keeps the write state machine. Write paths shape their return values
 *       through {@link #toSummaryVO} / {@link #toSummaryVOWithSavedStatus} /
 *       {@link #toCategorySummaryVO}.</li>
 *   <li>The controller depends on this projection directly for reads and on
 *       the service for writes.</li>
 * </ul>
 *
 * <p>All methods are pure reads; none mutate list state. Single-item endpoints
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

    /**
     * Admin-specific detail projection: the management console does not need
     * viewer state (saved / is-owner) or category options, but it does need
     * the same author / problem / tag enrichment plus solved/attempted/todo
     * stats as the user-facing overview. Completes the projection seam so
     * the admin service owns only mutations and audit, not cross-mapper
     * reads. Architecture-review candidate #3.
     *
     * @param list the list entity (must already be loaded by the caller)
     * @return the admin-facing detail view object
     */
    ProblemListDetailVO toAdminDetailVO(ProblemList list);
}
