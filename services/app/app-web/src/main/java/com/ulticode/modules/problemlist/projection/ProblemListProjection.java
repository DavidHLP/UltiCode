package com.ulticode.modules.problemlist.projection;

import com.ulticode.modules.problemlist.dto.CategorySummaryVO;
import com.ulticode.modules.problemlist.dto.ProblemListDetailVO;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UserListsForProblemVO;
import com.ulticode.modules.problemlist.dto.UserProblemListsVO;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListCategory;

/**
 * Read-side projection for the problem-list domain &mdash; owns every
 * user-facing list read (overview, detail, per-problem list-status) and
 * the entity-to-VO conversion helpers the in-module write state machine
 * uses to shape its return values.
 *
 * <p>Architecture-review 2026-07-19 candidate #3 noted the interface
 * previously also hosted {@code toAdminDetailVO(ProblemList)}, a
 * cross-module conversion helper that let the admin service reach into
 * feature-side projection mechanics. That helper is gone; admin-facing
 * reads now live on the admin-side
 * {@link com.ulticode.modules.admin.projection.AdminProblemListProjection}
 * (admin &rarr; feature direction, matching the existing
 * {@code AdminContestProjection} / {@code AdminSubmissionProjection} /
 * {@code AdminUserProjection} series). This interface no longer imports
 * any type from the admin module.
 *
 * <p>The conversion helpers ({@link #toSummaryVO},
 * {@link #toSummaryVOWithSavedStatus}, {@link #toCategorySummaryVO}) stay
 * on this interface because the in-module
 * {@link com.ulticode.modules.problemlist.service.ProblemListService}
 * write state machine legitimately uses them to shape create / update /
 * fork / category-CRUD return values &mdash; they are same-module
 * collaborators, not cross-module leakage.
 *
 * <p>Single-item endpoints throw {@link com.ulticode.common.exception.ErrorCode#PROBLEM_LIST_NOT_FOUND}
 * / {@link com.ulticode.common.exception.ErrorCode#PROBLEM_LIST_PRIVATE}
 * to preserve the access contract observed by the controller.
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
}
