package com.ulticode.modules.problem.service;

import com.ulticode.modules.problem.vo.ProblemNoteVO;

/**
 * Service for per-user private problem notes.
 *
 * @author Claude
 * @since 2026-06-11
 */
public interface ProblemNoteService {

    /**
     * Get the note for a problem owned by the given user.
     *
     * @param userId    the user ID
     * @param problemId the problem ID
     * @return the note VO, or {@code null} if the user has no note for this problem
     */
    ProblemNoteVO getNote(String userId, Long problemId);

    /**
     * Create or update the note for a problem owned by the given user.
     *
     * @param userId    the user ID
     * @param problemId the problem ID
     * @param content   the new content
     * @return the upserted note VO
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@code PROBLEM_NOT_EXISTS} if the problem does not exist
     */
    ProblemNoteVO upsertNote(String userId, Long problemId, String content);
}
