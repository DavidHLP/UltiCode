package com.ulticode.modules.submission.projection;

import com.ulticode.modules.submission.dto.LearningProgressDTO;
import com.ulticode.modules.submission.dto.PerformanceStats;
import com.ulticode.modules.submission.dto.SubmissionDetailVO;
import com.ulticode.modules.submission.dto.SubmissionHistoryDTO;
import com.ulticode.modules.submission.dto.SubmissionListItemVO;
import com.ulticode.modules.submission.dto.SubmissionStatusMeta;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;

import java.util.List;

/**
 * Deep module that owns all entity-to-VO projection and read-side aggregation
 * for the submission domain.
 *
 * <p>Replaces the projection methods previously embedded in
 * {@code SubmissionServiceImpl}. Callers that only need projections (controllers
 * serving {@code /calendar}, {@code /learning-progress}, {@code /history}; the
 * read paths of {@code /submissions} and {@code /submissions/{id}}) cross this
 * seam and stay free of state-change concerns. Callers that mutate state
 * inject {@code SubmissionWritePort}; they delegate their projection work to
 * this interface so the projection rules live in one place.
 *
 * <p>Why a separate module and not "a helper class" or "moved methods":
 * <ul>
 *   <li><b>Locality</b>: the security-sensitive {@code toVO(Submission)} logic
 *       (hidden-case filtering, sample-first-failure extraction, compiler-error
 *       fan-out) and the histogram-bin normalisation both changed multiple times
 *       in the last year. Keeping them with state-change code made the diff
 *       noise. They are now concentrated here.</li>
 *   <li><b>Leverage</b>: four projection overloads share the same helpers
 *       ({@code normalizeBins}, user/problem summary builders, the hidden-case
 *       filter). The aggregation methods share the same {@code SubmissionMapper}.
 *       Sharing inside one module beats sharing across N call sites.</li>
 *   <li><b>Interface is the test surface</b>: the read paths are tested here
 *       with mocks for {@code SubmissionMapper}, {@code UserMapper},
 *       {@code ProblemMapper}, and {@code ObjectMapper}. The state-change
 *       paths in {@code SubmissionServiceImpl} no longer have to mock those
 *       collaborators just to exercise {@code toVO}.</li>
 * </ul>
 *
 * <p>Dependency category: <b>in-process</b> (no I/O that cannot be exercised
 * with mocks). No adapter is needed at the external seam.
 */
public interface SubmissionProjection {

    /**
     * Convert a pre-joined {@code SubmissionWithProblem} to a lightweight
     * list-item VO. Does not touch the {@code UserMapper} or
     * {@code ProblemMapper}; both are read from the pre-loaded DTO to
     * avoid N+1 lookups.
     *
     * @param submission the mapper-projected record carrying problem fields
     * @return the list-item VO; never {@code null}
     */
    SubmissionListItemVO toListItemVO(SubmissionMapper.SubmissionWithProblem submission);

    /**
     * Convert a {@code Submission} entity to a full {@code SubmissionDetailVO}.
     *
     * <p>Carries out the security projection: only user-visible (SAMPLE or
     * legacy) test cases populate {@code vo.tests} and the I/O preview;
     * HIDDEN-case failures set only {@code vo.errorDetail} so users cannot
     * probe hidden case contents.
     *
     * @param submission the submission entity
     * @param stats      pre-computed performance stats; may be {@code null}
     *                   for non-Accepted submissions or callers that want
     *                   the entity's stored fields to drive the histograms
     * @return the detail VO; never {@code null}
     */
    SubmissionDetailVO toDetailVO(Submission submission, PerformanceStats stats);

    /**
     * Convert a {@code Submission} entity to a {@code SubmissionVO}, applying
     * the same security projection as {@link #toDetailVO(Submission, PerformanceStats)}.
     * Used by the read paths of {@code submit} and {@code findBest} before
     * returning to controllers.
     *
     * @param submission the submission entity
     * @return the submission VO; never {@code null}
     */
    SubmissionVO toVO(Submission submission);

    /**
     * Overload: convert a pre-joined {@code SubmissionWithProblem} to a
     * {@code SubmissionVO} without N+1 lookups.
     *
     * @param submission the mapper-projected record
     * @return the submission VO; never {@code null}
     */
    SubmissionVO toVO(SubmissionMapper.SubmissionWithProblem submission);

    /**
     * Aggregate the {@code YYYY-MM-DD} dates on which {@code userId} made
     * submissions in the given year. Used by the calendar view.
     *
     * @param userId the user ID
     * @param year   the calendar year; {@code null} delegates to the mapper
     *               default (typically the current year)
     * @return the list of date strings; never {@code null}
     */
    List<String> aggregateDates(String userId, Integer year);

    /**
     * Aggregate the learning-progress rollup for {@code userId}: weekly
     * solved counts, time spent, total problems, current and longest streak.
     *
     * @param userId the user ID
     * @return the learning-progress DTO; never {@code null}
     */
    LearningProgressDTO aggregateLearningProgress(String userId);

    /**
     * Aggregate the submission-history rollup for {@code userId}: monthly
     * counts, accepted counts, language breakdown, acceptance rate.
     *
     * @param userId the user ID
     * @return the history DTO; never {@code null}
     */
    SubmissionHistoryDTO aggregateHistory(String userId);

    /**
     * Project the canonical status catalog for the public
     * {@code /submissions/statuses} endpoint.
     *
     * <p>One {@link SubmissionStatusMeta} entry per {@link
     * com.ulticode.modules.submission.enums.SubmissionStatus} value. The enum
     * owns the durable contract (displayName, category, terminal, kind); the
     * user-facing strings (description, suggestion) and the display sort order
     * live in {@link com.ulticode.modules.submission.enums.SubmissionStatusCatalog}.
     * Returns all 12 statuses (including transient {@code PENDING}/{@code JUDGING}
     * and infrastructure {@code SANDBOX_ERROR}); callers that want only terminal
     * verdicts can filter on {@code meta.isTerminal}.
     *
     * @return ordered list of status metadata; never {@code null}, never empty
     */
    List<SubmissionStatusMeta> getStatusCatalog();
}
