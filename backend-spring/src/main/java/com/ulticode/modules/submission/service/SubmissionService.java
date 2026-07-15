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
 * Submission domain facade — controller-facing seam for the submission state
 * machine.
 *
 * <p>The <em>state mutation</em> work — Submission intake and the two verdict
 * writers — lives behind
 * {@link com.ulticode.modules.submission.port.SubmissionWritePort}, the single
 * collaborator whose implementation owns every state mutation on submission
 * records (transaction, contest rules, outbox, fenced CAS).
 *
 * <p>This interface owns:
 * <ul>
 *   <li>the <em>write delegate</em> {@link #submit} that exposes intake to the
 *       in-module controller layer (so {@code Controller → Service → Port}
 *       ordering holds within {@code modules/submission}); cross-module write
 *       callers ({@code ContestServiceImpl#submitContestProblem},
 *       {@code DefaultJudgeAttemptExecutor}) inject the port directly, which
 *       is the legitimate cross-module consumer-seam pattern;</li>
 *   <li>the <em>boundary reads</em> a caller crosses just after the state
 *       boundary: {@link #findById}, {@link #findByUserId},
 *       {@link #findByProblemId}, {@link #findBest}, {@link #getSubmissionEntity},
 *       and the static status catalog {@link #getStatuses}.</li>
 * </ul>
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
     * Submit code for a problem — the Submission intake.
     *
     * <p>Thin facade delegate that forwards to
     * {@link com.ulticode.modules.submission.port.SubmissionWritePort#submit}.
     * Present so in-module controllers stay on the {@code Controller → Service}
     * path; cross-module write callers inject the port directly.
     *
     * @param userId    the submitting user id
     * @param createDTO the submission payload
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
}
