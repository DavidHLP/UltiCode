package com.ulticode.modules.admin.service;

import com.ulticode.modules.admin.dto.BatchRejudgeResponse;
import com.ulticode.modules.admin.dto.RejudgeResult;

import java.util.List;

/**
 * Write-side service for admin submission management.
 *
 * <p>After the ADR-0011 Stage 2 extraction, this interface owns only the
 * submission state machine: single rejudge and batch rejudge. Every read-side
 * concern (paginated list, single detail, statistics, filter options) moved
 * behind {@link com.ulticode.modules.admin.projection.AdminSubmissionProjection}.
 *
 * <p>The split mirrors the peer deep-module pattern established by
 * {@code ModerationProjection} / {@code AchievementProjection} /
 * {@code ProblemListProjection}: the projection owns reads, the service owns
 * writes, the controller depends on both.
 *
 * @author ulticode
 * @see com.ulticode.modules.admin.projection.AdminSubmissionProjection
 */
public interface AdminSubmissionService {

    /**
     * Rejudge a submission.
     *
     * @param id submission ID
     * @param notifyUser whether to notify the user
     * @return rejudge result including {@code rejudgedAt} and {@code retryCount}
     */
    RejudgeResult rejudge(String id, boolean notifyUser);

    /**
     * Batch rejudge multiple submissions.
     *
     * @param submissionIds list of submission IDs (non-null, non-empty, size &le; 50,
     *                      validated upstream via Bean Validation)
     * @param notifyUsers whether to notify users
     * @return batch rejudge result
     */
    BatchRejudgeResponse batchRejudge(List<String> submissionIds, boolean notifyUsers);
}
