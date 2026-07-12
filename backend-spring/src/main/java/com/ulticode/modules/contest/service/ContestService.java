package com.ulticode.modules.contest.service;

import com.ulticode.modules.contest.dto.AddContestProblemDTO;
import com.ulticode.modules.contest.dto.ContestProblemVO;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;

/**
 * Write-side facade for the contest domain — the contest state machine only.
 *
 * <p>Read paths (catalog lists, detail, problems, announcements, stats, rankings)
 * live in {@link com.ulticode.modules.contest.projection.ContestProjection};
 * participation (register, unregister, status, virtual replay, history) lives
 * in {@link ContestParticipationService}. This facade keeps the contest
 * lifecycle (create, update, delete, start, end, problem management) and
 * contest submission. Write paths shape their return values through
 * {@link com.ulticode.modules.contest.projection.ContestProjection#toVO}.
 */
public interface ContestService {

    /**
     * Create a new contest. Requires ADMIN role.
     *
     * @param dto    the create contest DTO
     * @param userId the user ID creating the contest
     * @return the created contest view object
     */
    ContestVO createContest(CreateContestDTO dto, String userId);

    /**
     * Update an existing contest. Requires ADMIN role; only allowed while UPCOMING.
     *
     * @param id     the contest ID
     * @param dto    the update contest DTO
     * @return the updated contest view object
     */
    ContestVO updateContest(String id, UpdateContestDTO dto);

    /**
     * Delete a contest (soft delete + cascade). Requires ADMIN role.
     *
     * @param id the contest ID
     */
    void deleteContest(String id);

    /**
     * Submit code for a problem in a specific contest.
     */
    SubmissionVO submitContestProblem(String contestId, Long problemId, String userId, CreateSubmissionDTO createDTO);

    /**
     * Start a contest (transition from DRAFT/UPCOMING to RUNNING).
     *
     * @param id     the contest ID
     * @param userId the user ID performing the action
     * @return the updated contest view object
     */
    ContestVO startContest(String id, String userId);

    /**
     * End a contest (transition from RUNNING to FINISHED).
     *
     * @param id     the contest ID
     * @param userId the user ID performing the action
     * @return the updated contest view object
     */
    ContestVO endContest(String id, String userId);

    /**
     * Add a problem to a contest.
     *
     * @param contestId the contest ID
     * @param dto       the add problem DTO
     * @return the contest problem view object
     */
    ContestProblemVO addProblem(String contestId, AddContestProblemDTO dto);

    /**
     * Remove a problem from a contest.
     *
     * @param contestId the contest ID
     * @param problemId the problem ID to remove
     */
    void removeProblem(String contestId, Long problemId);
}
