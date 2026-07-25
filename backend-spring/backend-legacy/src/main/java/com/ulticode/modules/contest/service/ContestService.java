package com.ulticode.modules.contest.service;

import com.ulticode.modules.contest.dto.AddContestProblemDTO;
import com.ulticode.modules.contest.dto.ContestProblemVO;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;

/**
 * User-facing write facade for the contest domain. After the admin-contest
 * mutation seam landed, only the user-reachable writes stay here:
 * <ul>
 *   <li>{@link #submitContestProblem} — a registered participant submits
 *       against a running contest.</li>
 *   <li>{@link #addProblem} / {@link #removeProblem} — contest-problem
 *       link management.</li>
 * </ul>
 *
 * <p>Admin lifecycle (create / update / soft-delete / start / end) moved to
 * {@link com.ulticode.modules.admin.service.AdminContestMutationService};
 * admin announcement CRUD and problem association sit alongside it. Read
 * paths (catalog, detail, problems, announcements, stats, rankings) live in
 * {@link com.ulticode.modules.contest.projection.ContestProjection};
 * participation (register, unregister, status, virtual replay, history) lives
 * in {@link ContestParticipationService}.
 */
public interface ContestService {

    /**
     * Submit code for a problem in a specific contest.
     */
    SubmissionVO submitContestProblem(String contestId, Long problemId, String userId, CreateSubmissionDTO createDTO);

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