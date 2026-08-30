package com.ulticode.modules.admin.projection;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.submission.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.AdminSubmissionVO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.LanguageOption;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.StatusOption;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.SubmissionStatistics;

import java.util.List;

/**
 * Read-side projection for admin submission management &mdash; a deep module
 * that owns every entity-to-VO projection rule, paginated list-query builder,
 * statistics aggregation and filter-option derivation for the admin submission
 * surface.
 *
 * <p>This projection is independent of the Submission-owned rejudge command
 * service; it owns only the Admin read surface described below.
 * Stage 2 rollout of ADR-0011: the paginated list read
 * ({@link #getSubmissions}, ~110 LoC of query building + batch user/problem
 * enrichment + N+1-safe VO shaping), the single-detail read
 * ({@link #getSubmission}), the dashboard statistics aggregation
 * ({@link #getStatistics}, total / by-status / by-language / 24h / pending),
 * and the two filter-option derivations ({@link #getStatuses} from the
 * canonical enum, {@link #getLanguages} with humanised labels). Sitting next
 * to the ADR-003 fenced rejudge state machine in the same service made every
 * projection tweak land in the same file as the write paths.
 *
 * <p>After the deepening:
 * <ul>
 *   <li>The controller depends on this projection directly for reads.</li>
 *   <li>Rejudge commands pass through {@code SubmissionCutoverService} to
 *       the Submission owner and never call this projection.</li>
 * </ul>
 *
 * <p>All methods are pure reads; none mutate submission state. The single-item
 * read throws {@link com.ulticode.admin.error.AdminErrorCode#SUBMISSION_NOT_FOUND}
 * to preserve the access contract observed by the controller.
 *
 * <p>Mirrors the {@link com.ulticode.modules.problemlist.projection.ProblemListProjection}
 * / {@code ModerationProjection} / {@code AchievementProjection} shape exactly.
 * Cross-module entity imports ({@code User}, {@code Problem}) live behind this
 * seam; the admin service no longer imports them.
 *
 * @author ulticode
 * @see com.ulticode.modules.problemlist.projection.ProblemListProjection
 * @see com.ulticode.modules.moderation.projection.ModerationProjection
 */
public interface AdminSubmissionProjection {

    /**
     * Get a paginated list of submissions with filters (search, user, problem,
     * status, language, date range) and sorting. Cross-module enrichment
     * (username, problem title/slug) is batch-loaded to avoid N+1.
     *
     * @param query query parameters including filters, pagination, and sorting
     * @return paginated result of admin submission VOs (list view shape)
     */
    PageResult<AdminSubmissionVO> getSubmissions(SubmissionAdminQueryDTO query);

    /**
     * Get a single submission by ID with full detail fields (code, notes,
     * percentiles, test details, distribution bins).
     *
     * @param id submission ID
     * @return admin submission VO with full details
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.admin.error.AdminErrorCode#SUBMISSION_NOT_FOUND}
     *         when the submission does not exist
     */
    AdminSubmissionVO getSubmission(String id);

    /**
     * Get submission statistics for the admin dashboard: total count,
     * per-status breakdown, per-language breakdown, last-24h count and
     * pending count.
     *
     * @return submission statistics aggregate
     */
    SubmissionStatistics getStatistics();

    /**
     * Get the available status filter options, derived from the canonical
     * {@link com.ulticode.domain.submission.enums.SubmissionStatus} enum so
     * the dropdown stays in sync with both the DB display names and the
     * statistics categories. Returns all statuses including transient ones
     * (Judging) so admins can filter on every observed state.
     *
     * @return list of status options
     */
    List<StatusOption> getStatuses();

    /**
     * Get the available programming-language filter options with humanised
     * labels (e.g. {@code cpp &rarr; "C++"}, {@code javascript &rarr; "JavaScript"}).
     *
     * @return list of language options (key = DB code, label = humanised name)
     */
    List<LanguageOption> getLanguages();
}
