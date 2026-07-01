package com.ulticode.modules.submission.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionDetailVO;
import com.ulticode.modules.submission.dto.SubmissionListItemVO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionStatusMeta;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for submission state changes and single-row reads.
 *
 * <p>Read-side rollups (calendar dates, learning progress, submission history)
 * live behind {@link com.ulticode.modules.submission.projection.SubmissionProjection}.
 * The two interfaces share the same {@code Submission} domain but sit at
 * different seams: this one owns the <em>state machine</em> (submit, judge
 * updates, fenced writes); the projection owns the <em>view shape</em>.
 *
 * <p>Returning {@link SubmissionVO} from {@code submit} and {@code findBest}
 * stays on this interface because the caller has just crossed the state
 * boundary and wants a directly usable payload. The actual entity-to-VO
 * projection is delegated to {@code SubmissionProjection} so the rules
 * live in one place.
 */
public interface SubmissionService {

    /**
     * Submit code for a problem.
     * Creates a new submission with Pending status.
     *
     * @param userId    the user ID submitting
     * @param createDTO the submission data
     * @return the created submission view object
     */
    SubmissionVO submit(String userId, CreateSubmissionDTO createDTO);

    /**
     * Find a submission by its ID.
     *
     * @param id     the submission ID
     * @param userId optional user ID for access control
     * @return the submission detail view object
     */
    SubmissionDetailVO findById(String id, String userId);

    /**
     * Find submissions by user ID with pagination.
     *
     * @param userId   the user ID
     * @param query    the query parameters
     * @return paginated result of submission view objects
     */
    PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query);

    /**
     * Find submissions by problem ID with pagination.
     *
     * @param problemId the problem ID
     * @param userId    optional user ID filter
     * @param query     the query parameters
     * @return paginated result of lightweight submission list items
     */
    PageResult<SubmissionListItemVO> findByProblemId(Long problemId, String userId, SubmissionQueryDTO query);

    /**
     * Find the best (fastest accepted) submission for a problem by user.
     *
     * @param problemId the problem ID
     * @param userId    the user ID
     * @return the best submission view object, or null if not found
     */
    SubmissionVO findBest(Long problemId, String userId);

    /**
     * Get the raw submission entity by ID.
     *
     * @param id the submission ID
     * @return the submission entity, or empty if not found
     */
    Optional<Submission> getSubmissionEntity(String id);

    /**
     * Get submission status metadata for frontend display.
     *
     * @return list of submission status metadata
     */
    List<SubmissionStatusMeta> getStatuses();

    /**
     * Update submission result after judge processing.
     *
     * @param submissionId the submission ID
     * @param status       the new status
     * @param runtime      runtime in milliseconds
     * @param memory       memory usage in MB
     * @param testDetails  test case execution details
     */
    void updateSubmissionResult(String submissionId, String status, int runtime,
                                Double memory, List<Submission.TestCaseDetail> testDetails);

    /**
     * ADR-003 M3b fenced verdict write. Writes the verdict behind the
     * generation+attempt CAS so a stale worker whose generation was bumped
     * (rejudge / reaper) cannot overwrite the newer result. On fence mismatch
     * the result is dropped and {@code judge.stale_result.dropped} increments.
     *
     * @param submissionId  submission id
     * @param generation    generation the worker observed at acquire (fence axis 1)
     * @param attemptId     attempt UUID held by the worker (fence axis 2)
     * @param status        terminal verdict wire value
     * @param runtime       runtime in ms
     * @param memory        memory in MB
     * @param testDetails   test case details
     * @return {@code true} if the verdict was written; {@code false} if the
     *         fence rejected it (stale result dropped)
     */
    boolean updateSubmissionResultFenced(String submissionId, long generation, String attemptId,
                                         String status, int runtime, Double memory,
                                         List<Submission.TestCaseDetail> testDetails);
}
