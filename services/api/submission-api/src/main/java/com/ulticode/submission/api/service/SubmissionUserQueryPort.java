package com.ulticode.submission.api.service;

import com.ulticode.submission.api.dto.LearningProgressDTO;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.common.response.PageResult;

import java.util.List;

/**
 * Owner read seam for user-facing Submission queries.
 *
 * <p>SPLIT-004 slice-7 added the aggregations (calendar dates, learning
 * progress, submission history, status catalog); slice-8 extends the same
 * contract with the per-user detail/list/best reads so the App
 * {@code SubmissionController} read endpoints can route through the
 * Submission owner instead of the App schema.
 *
 * <p>List reads resolve problem display facts through the
 * App-provided Problem facts batch seam — never a cross-owner JOIN
 * (DEC-011). All methods are non-throwing: missing or unauthorized rows
 * surface as {@code null} / empty pages and the caller maps them to HTTP
 * semantics.
 */
public interface SubmissionUserQueryPort {

    /** Calendar dates (YYYY-MM-DD) for {@code userId} in {@code year}. */
    List<String> aggregateDates(String userId, Integer year);

    /** Learning-progress rollup for {@code userId}. */
    LearningProgressDTO aggregateLearningProgress(String userId);

    /** Submission-history rollup for {@code userId}. */
    SubmissionHistoryDTO aggregateHistory(String userId);

    /** Canonical status catalog for the public {@code /submissions/statuses}. */
    List<SubmissionStatusMeta> getStatusCatalog();

    /**
     * Full detail for a submission owned by {@code userId}.
     *
     * @param id     submission id
     * @param userId the requesting user; {@code null} results in no rows
     * @return detail VO with problem/user enrichment, or {@code null} when
     *         the submission is missing or not owned by {@code userId}
     */
    SubmissionDetailVO findById(String id, String userId);

    /**
     * Paginated list of {@code userId}'s submissions, newest first, with
     * problem display facts enriched through the batch facts seam.
     *
     * @return non-null page; empty records when the user has no rows
     */
    PageResult<SubmissionVO> findByUserId(String userId, SubmissionQueryDTO query);
    /**
     * Paginated submissions for a problem visible to {@code userId}, newest
     * first. Problem display facts are enriched through the owner facts seam.
     */
    PageResult<SubmissionListItemVO> findByProblemId(
            Long problemId, String userId, SubmissionQueryDTO query);

    /**
     * Best (fastest accepted) submission for {@code userId} and problem,
     * or {@code null} when none exists.
     */
    SubmissionVO findBest(Long problemId, String userId);
}
