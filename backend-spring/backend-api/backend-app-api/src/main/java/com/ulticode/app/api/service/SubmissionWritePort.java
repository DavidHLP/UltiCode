package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.domain.submission.enums.SubmissionStatus;

import java.util.List;

/**
 * Write surface for the submission domain — the <b>Submission intake</b> and
 * the verdict writers.
 */
public interface SubmissionWritePort {

    /**
     * Create a new submission in {@code Pending} status and enqueue the judge job.
     *
     * @param userId    the submitting user id
     * @param createDTO the submission payload
     * @return the created submission view
     */
    SubmissionVO submit(String userId, CreateSubmissionDTO createDTO);

    /**
     * Write a verdict for a submission (flag-off legacy path).
     *
     * @param submissionId the submission id
     * @param status       the new status
     * @param runtime      runtime in ms
     * @param memory       memory in MB
     * @param testDetails  per-case results
     */
    void updateSubmissionResult(String submissionId, SubmissionStatus status,
                                int runtime, double memory, String testDetails);

    /**
     * Write a verdict using the ADR-003 M3b fenced path.
     * CAS-rejects stale workers whose generation was bumped.
     *
     * @param submissionId  the submission id
     * @param status        the new status
     * @param runtime       runtime in ms
     * @param memory        memory in MB
     * @param testDetails   per-case results
     * @param generation    the generation this worker observed
     * @param attemptNumber the attempt number
     */
    void updateSubmissionResultFenced(String submissionId, SubmissionStatus status,
                                      int runtime, double memory, String testDetails,
                                      long generation, int attemptNumber);

    /**
     * List submissions for a user.
     */
    List<SubmissionVO> listByUserId(String userId, int page, int limit);

    /**
     * List submissions for a problem.
     */
    List<SubmissionVO> listByProblemId(Long problemId, int page, int limit);

    /**
     * Get a submission by id.
     */
    SubmissionVO findById(String submissionId);

    /**
     * Count submissions for a user.
     */
    long countByUserId(String userId);
}
