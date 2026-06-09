package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.*;

import java.util.List;

/**
 * Service interface for admin submission management.
 */
public interface AdminSubmissionService {

    /**
     * Get paginated list of submissions with filters.
     *
     * @param query query parameters including filters, pagination, and sorting
     * @return paginated result of admin submission VOs
     */
    PageResult<AdminSubmissionVO> getSubmissions(AdminSubmissionQueryDTO query);

    /**
     * Get submission details by ID.
     *
     * @param id submission ID
     * @return admin submission VO with full details
     */
    AdminSubmissionVO getSubmission(String id);

    /**
     * Get submission statistics for admin dashboard.
     *
     * @return submission statistics
     */
    SubmissionStatistics getStatistics();

    /**
     * Get available status options for filtering.
     *
     * @return list of status options
     */
    List<StatusOption> getStatuses();

    /**
     * Get available programming languages for filtering.
     *
     * @return list of language options (key = DB code, label = humanised name)
     */
    List<LanguageOption> getLanguages();

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
