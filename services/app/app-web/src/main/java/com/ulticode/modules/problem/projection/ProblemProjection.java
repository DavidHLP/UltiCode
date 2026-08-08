package com.ulticode.modules.problem.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.dto.AdjacentProblemsVO;
import com.ulticode.modules.problem.dto.ProblemDetailAdminVO;
import com.ulticode.modules.problem.dto.ProblemDetailPublicVO;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.entity.Problem;

import java.util.List;
import java.util.Map;

/**
 * Deep module that owns all entity-to-VO projection and read-side aggregation
 * for the problem domain.
 *
 * <p>Replaces the projection methods previously embedded in
 * {@code ProblemServiceImpl}. Callers that only need read views
 * ({@code ProblemController} list/detail/adjacent/random paths,
 * {@code AdminProblemController} list/export paths) cross this seam and stay
 * free of state-change concerns. Callers that mutate state still hold a
 * {@code ProblemService} reference; the service delegates every view shape
 * here so the projection rules live in one place.
 *
 * <p>Why a separate module and not "a helper class" or "moved methods":
 * <ul>
 *   <li><b>Locality</b>: the detail-response assembly
 *       ({@code populatePublicFields} + the {@code buildDetailData} /
 *       {@code buildInteractions} / {@code buildExamples} / {@code buildLanguages}
 *       cluster), the {@code toVO} field mapping, and the list query builder
 *       ({@code buildProblemQueryWrapper} with its category-namespace /
 *       tag-or-match / search-as-id-or-title rules) all carry non-trivial
 *       projection policy that has changed multiple times
 *       (D-08 search-by-slug, D-09 tag id-style match, D-10 viewer reaction,
 *       D-11 adjacent existence check, D-12 random enrichment,
 *       D-14 non-numeric id → 404). Keeping them next to the problem
 *       state-machine made the diff noise. They are now concentrated here.</li>
 *   <li><b>Leverage</b>: the list endpoints ({@code listProblems} /
 *       {@code listAllProblems} / {@code findRandomPublished}) share the same
 *       batch-fetch helpers ({@code batchFetchTags},
 *       {@code batchFetchSubmissionCounts}, {@code batchFetchSolutionCounts})
 *       and the same {@code toVO} overload. Sharing inside one module beats
 *       sharing across N call sites.</li>
 *   <li><b>Interface is the test surface</b>: the read paths are tested here
 *       with mocks for the mappers and {@code EdgeOperationsService}. The
 *       state-change paths in {@code ProblemServiceImpl} no longer have to
 *       mock those collaborators just to exercise {@code toVO}.</li>
 * </ul>
 *
 * <p>Dependency category: <b>in-process</b> (no I/O that cannot be exercised
 * with mocks). No adapter is needed at the external seam; the default adapter
 * is the only implementation.
 *
 * @author ulticode
 */
public interface ProblemProjection {

    /**
     * Convert a {@code Problem} entity to a {@code ProblemVO} with no tags or
     * counts loaded. Used by the state-change paths (create/update/publish/
     * unpublish) and by the cross-module callers that hold a
     * {@code ProblemService} reference and call its {@code toVO} facade.
     *
     * @param problem the problem entity; may be {@code null}
     * @return the problem VO, or {@code null} if the input is {@code null}
     */
    ProblemVO toVO(Problem problem);

    /**
     * Convert a {@code Problem} entity to a {@code ProblemVO} using
     * pre-loaded tag, submission-count and solution-count maps. Used by the
     * list and random endpoints, which batch-fetch the maps once per page to
     * avoid N+1 lookups.
     *
     * @param problem          the problem entity; may be {@code null}
     * @param tagMap           problem id → list of tag VOs; never {@code null}
     * @param submissionCounts problem id → submission count; never {@code null}
     * @param solutionCounts   problem id → solution count; never {@code null}
     * @return the problem VO, or {@code null} if the input is {@code null}
     */
    ProblemVO toVO(Problem problem,
                   Map<Long, List<ProblemVO.ProblemTagVO>> tagMap,
                   Map<Long, Long> submissionCounts,
                   Map<Long, Long> solutionCounts);

    /**
     * Build the public detail response for the problem with the given id.
     * Owns the existence check (throws {@code PROBLEM_NOT_FOUND}) and the full
     * detail assembly: content, parsed JSON (constraints/hints/companies),
     * examples, languages, tags, real interaction counts.
     *
     * @param id the problem id
     * @return the public detail response; never {@code null}
     */
    ProblemDetailPublicVO publicDetailById(Long id);

    /**
     * Build the public detail response for the problem with the given slug.
     *
     * @param slug the URL-friendly identifier
     * @return the public detail response; never {@code null}
     */
    ProblemDetailPublicVO publicDetailBySlug(String slug);

    /**
     * Build the admin detail response, which carries every public field plus
     * the moderation/management fields (publish state, soft-delete state,
     * flag review state).
     *
     * @param id the problem id
     * @return the admin detail response; never {@code null}
     */
    ProblemDetailAdminVO adminDetailById(Long id);

    /**
     * Build the admin detail response for the problem with the given slug.
     *
     * @param slug the URL-friendly identifier
     * @return the admin detail response; never {@code null}
     */
    ProblemDetailAdminVO adminDetailBySlug(String slug);

    /**
     * List problems with pagination and filters. Handles the ARCHIVED
     * (soft-deleted) view via raw SQL that bypasses {@code @TableLogic}, and
     * caps the page size at 100.
     *
     * @param query the query parameters
     * @return paginated result of problem VOs; never {@code null}
     */
    PageResult<ProblemVO> listProblems(ProblemQueryDTO query);

    /**
     * List all problems matching the filters without pagination. Used by the
     * admin export endpoint (capped upstream at {@code MAX_EXPORT_SIZE}).
     *
     * @param query the query parameters
     * @return list of problem VOs; never {@code null}
     */
    List<ProblemVO> listAllProblems(ProblemQueryDTO query);

    /**
     * Get the previous and next published problem slugs for navigation.
     * Validates the id exists before computing neighbours.
     *
     * @param id the current problem id
     * @return the adjacent-problems response with prev/next slugs; never {@code null}
     */
    AdjacentProblemsVO adjacentProblems(Long id);

    /**
     * Get a random published problem, enriched with tags and counts so the
     * random endpoint returns the same data shape as the list endpoint.
     *
     * @return a random published problem VO; never {@code null}
     */
    ProblemVO findRandomPublished();
}
