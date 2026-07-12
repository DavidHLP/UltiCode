package com.ulticode.modules.submission.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.dto.SubmissionDetailVO;
import com.ulticode.modules.submission.dto.SubmissionListItemVO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionStatusMeta;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;

import java.util.List;
import java.util.Optional;

/**
 * Read boundary for the submission state machine.
 *
 * <p>The write surface — Submission intake and the two verdict writers — lives
 * behind {@link com.ulticode.modules.submission.port.SubmissionWritePort},
 * the single seam whose implementation owns every state mutation on submission
 * records (transaction, contest rules, outbox, fenced CAS). This interface now
 * owns only the <em>boundary reads</em> a caller crosses just after the state
 * boundary: {@link #findById}, {@link #findByUserId}, {@link #findByProblemId},
 * {@link #findBest}, {@link #getSubmissionEntity}, and the static status
 * catalog {@link #getStatuses}.
 *
 * <p>Read-side rollups (calendar dates, learning progress, submission history)
 * live behind {@link com.ulticode.modules.submission.projection.SubmissionProjection}.
 * Returning {@link SubmissionVO} from {@code findBest} stays on this interface
 * because the caller wants a directly usable payload; the entity-to-VO
 * projection itself delegates to {@code SubmissionProjection} so the shaping
 * rules live in one place.
 *
 * @author ulticode
 */
public interface SubmissionService {

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
}
