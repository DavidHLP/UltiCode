package com.ulticode.modules.admin.port;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.submission.entity.Submission;

import java.util.Optional;

/**
 * Typed port the admin problem module uses to interact with the problem
 * and submission modules.
 *
 * <p>Replaces the direct dependencies {@code AdminProblemServiceImpl} had on
 * {@code com.ulticode.modules.problem.service.ProblemService} and
 * {@code com.ulticode.modules.submission.mapper.SubmissionMapper}. The admin
 * problem management page needs cross-module data — problem lifecycle writes
 * (publish / unpublish / delete), VO conversion, slug lookup, and submission
 * pagination — that are none of admin's business to reach by raw service /
 * mapper imports. This port narrows that surface to six typed methods; the
 * production adapter
 * ({@link com.ulticode.modules.admin.port.adapter.AdminProblemAdapter})
 * hides the two cross-module dependencies.
 *
 * <p>Phase four of the AdminReadModel seam (after
 * {@link AdminSubmissionReadPort}, {@link AdminUserStatsReadPort}, and
 * {@link AdminCommentReadPort}). The deletion test passes: deleting this port
 * would force {@code AdminProblemServiceImpl} back into importing
 * {@code ProblemService} and {@code SubmissionMapper} directly.
 *
 * @author ulticode
 */
public interface AdminProblemPort {

    // ─── Read operations ───────────────────────────────────────

    /**
     * Convert a Problem entity to its VO representation.
     *
     * @param problem the entity to convert
     * @return the view object
     */
    ProblemVO toVO(Problem problem);

    /**
     * Find a problem by its slug.
     *
     * @param slug the slug to search for
     * @return the problem, or empty if not found
     */
    Optional<Problem> findBySlug(String slug);

    /**
     * Paginated submissions for a specific problem, newest first.
     *
     * @param problemId the problem id
     * @param page      1-based page number
     * @param limit     page size
     * @return paginated submission list
     */
    PageResult<Submission> findSubmissionsByProblemId(Long problemId, int page, int limit);

    // ─── Write operations ──────────────────────────────────────

    /**
     * Publish a problem (make it visible to users).
     *
     * @param id the problem id
     */
    void publishProblem(Long id);

    /**
     * Unpublish a problem (hide it from users).
     *
     * @param id the problem id
     */
    void unpublishProblem(Long id);

    /**
     * Soft-delete a problem.
     *
     * @param id the problem id
     */
    void deleteProblem(Long id);
}
