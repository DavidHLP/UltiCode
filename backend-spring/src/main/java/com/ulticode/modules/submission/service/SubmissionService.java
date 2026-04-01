package com.ulticode.modules.submission.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for submission-related operations.
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
     * @return the submission view object
     */
    SubmissionVO findById(String id, String userId);

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
     * @return paginated result of submission view objects
     */
    PageResult<SubmissionVO> findByProblemId(Long problemId, String userId, SubmissionQueryDTO query);

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
     * Convert a Submission entity to SubmissionVO.
     *
     * @param submission the submission entity
     * @return the submission view object
     */
    SubmissionVO toVO(Submission submission);

    /**
     * Get the list of dates (YYYY-MM-DD) when a user made submissions in a given year.
     *
     * @param userId the user ID
     * @param year   the year to filter by
     * @return list of date strings
     */
    List<String> getSubmissionDates(String userId, Integer year);
}
