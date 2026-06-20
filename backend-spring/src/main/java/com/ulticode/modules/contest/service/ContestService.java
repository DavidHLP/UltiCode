package com.ulticode.modules.contest.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.submission.dto.SubmissionVO;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for contest-related operations.
 */
public interface ContestService {

    // =========================================================================
    // CRUD Operations (Admin)
    // =========================================================================

    /**
     * Create a new contest.
     * Requires ADMIN role.
     *
     * @param dto    the create contest DTO
     * @param userId the user ID creating the contest
     * @return the created contest view object
     */
    ContestVO createContest(CreateContestDTO dto, String userId);

    /**
     * Update an existing contest.
     * Requires ADMIN role.
     *
     * @param id     the contest ID
     * @param dto    the update contest DTO
     * @return the updated contest view object
     */
    ContestVO updateContest(String id, UpdateContestDTO dto);

    /**
     * Delete a contest (soft delete).
     * Requires ADMIN role.
     *
     * @param id the contest ID
     */
    void deleteContest(String id);

    // =========================================================================
    // Query Operations
    // =========================================================================

    /**
     * Find all contests with lightweight list VO for list pages
     */
    PageResult<ContestListVO> findAllListVO(ContestQueryDTO query, String userId);

    /**
     * Find a contest by ID (internal use).
     *
     * @param id the contest ID
     * @return the contest if found
     */
    Optional<Contest> findById(String id);

    /**
     * Find a contest by slug.
     *
     * @param slug the contest slug
     * @return the contest if found
     */
    Optional<Contest> findBySlug(String slug);

    /**
     * Get a contest by ID (throws exception if not found).
     *
     * @param id     the contest ID
     * @param userId the current user ID (optional, for user-specific fields)
     * @return the contest view object
     */
    ContestVO getContestById(String id, String userId);

    /**
     * Get contest problems by contest ID
     */
    List<ContestProblemVO> getContestProblems(String contestId);

    /**
     * Get the current user's submissions for a contest problem.
     */
    List<SubmissionVO> getContestProblemSubmissions(String contestId, Long problemId, String userId);

    /**
     * Submit code for a problem in a specific contest.
     */
    SubmissionVO submitContestProblem(String contestId, Long problemId, String userId, CreateSubmissionDTO createDTO);

    /**
     * Get contest announcements by contest ID
     */
    List<ContestAnnouncement> getContestAnnouncements(String contestId);

    /**
     * Get upcoming contests.
     *
     * @param userId the current user ID (optional, for user-specific fields)
     * @return paginated list of upcoming contests
     */
    PageResult<ContestListVO> findUpcoming(String userId);

    /**
     * Get running contests.
     *
     * @param userId the current user ID (optional, for user-specific fields)
     * @return paginated list of running contests
     */
    PageResult<ContestListVO> findRunning(String userId);

    /**
     * Get past contests with pagination.
     *
     * @param page     the page number (1-based)
     * @param pageSize the number of items per page
     * @param userId   the current user ID (optional, for user-specific fields)
     * @return paginated list of past contests
     */
    PageResult<ContestListVO> findPast(Integer page, Integer pageSize, String userId);

    /**
     * Get global contest statistics.
     *
     * @return the global contest statistics
     */
    GlobalContestStatsVO getStats();

    /**
     * Get global ranking (top users).
     *
     * @param limit the maximum number of rankings to return
     * @return list of global rankings
     */
    List<ContestRankingVO> getGlobalRanking(Integer limit);

    /**
     * Get global ranking with pagination.
     *
     * @param page    the page number (1-based)
     * @param limit   the number of items per page
     * @param country optional country filter (ISO code or free-text match); null/blank = global
     * @return paginated list of global rankings
     */
    PageResult<ContestRankingVO> getGlobalRankingsPaginated(Integer page, Integer limit, String country);

    /**
     * Register a user for a contest.
     *
     * @param contestId the contest ID
     * @param userId    the user ID
     */
    void registerForContest(String contestId, String userId);

    /**
     * Unregister a user from a contest.
     *
     * @param contestId the contest ID
     * @param userId    the user ID
     */
    void unregisterFromContest(String contestId, String userId);

    /**
     * Get user's participation status for a contest.
     *
     * @param contestId the contest ID
     * @param userId    the user ID
     * @return the participation status
     */
    ParticipationStatusDTO getParticipationStatus(String contestId, String userId);

    /**
     * Get user's contests.
     *
     * @param userId the user ID
     * @param type   the type of contests (registered, participated, virtual)
     * @return list of contests the user participated in
     */
    List<ContestVO> getUserContests(String userId, String type);

    /**
     * Start a virtual contest.
     *
     * @param contestId the contest ID
     * @param userId    the user ID
     * @return the virtual session information
     */
    ParticipationStatusDTO startVirtualContest(String contestId, String userId);

    /**
     * Get virtual contest session status.
     *
     * @param contestId the contest ID
     * @param userId    the user ID
     * @return the virtual session status
     */
    ParticipationStatusDTO getVirtualSession(String contestId, String userId);

    /**
     * Finish a virtual contest.
     *
     * @param contestId the contest ID
     * @param sessionId the virtual session ID
     * @param userId    the user ID
     */
    void finishVirtualContest(String contestId, String sessionId, String userId);

    /**
     * Convert a Contest entity to ContestVO.
     *
     * @param contest the contest entity
     * @param userId  the current user ID (optional, for user-specific fields)
     * @return the contest view object
     */
    ContestVO toVO(Contest contest, String userId);

    /**
     * Convert a Contest entity to ContestListVO (lightweight).
     */
    ContestListVO toListVO(Contest contest, String userId);

    /**
     * Find all contests for admin (includes drafts and invisible).
     *
     * @param query  the query parameters
     * @param userId the current user ID (optional)
     * @return paginated list of all contests
     */
    PageResult<ContestListVO> findAllAdmin(ContestQueryDTO query, String userId);

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

    /**
     * Get contest ranking for admin.
     *
     * @param contestId the contest ID
     * @param page      the page number (1-based)
     * @param limit     the number of items per page
     * @return paginated list of rankings
     */
    PageResult<ContestRankingVO> getAdminContestRanking(String contestId, Integer page, Integer limit);

    /**
     * R9.1 / F-24: per-contest ranking with keyset cursor pagination.
     * Cache key template includes {@code contestId} so per-contest
     * eviction (R9.2) becomes possible. The {@code cursor} format
     * is "{@code rank:userId}"; null/blank means first page.
     */
    List<ContestRankingVO> getContestRanking(String contestId, Integer limit, String cursor);
}
