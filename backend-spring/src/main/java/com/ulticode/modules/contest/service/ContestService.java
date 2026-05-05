package com.ulticode.modules.contest.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.entity.Contest;

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
     * Find all contests with pagination and filters.
     *
     * @param query  the query parameters
     * @param userId the current user ID (optional, for user-specific fields)
     * @return paginated list of contests
     */
    PageResult<ContestVO> findAll(ContestQueryDTO query, String userId);

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
     * Get upcoming contests.
     *
     * @param userId the current user ID (optional, for user-specific fields)
     * @return list of upcoming contests
     */
    List<ContestVO> findUpcoming(String userId);

    /**
     * Get running contests.
     *
     * @param userId the current user ID (optional, for user-specific fields)
     * @return list of running contests
     */
    List<ContestVO> findRunning(String userId);

    /**
     * Get past contests with pagination.
     *
     * @param page     the page number (1-based)
     * @param pageSize the number of items per page
     * @param userId   the current user ID (optional, for user-specific fields)
     * @return paginated list of past contests
     */
    PageResult<ContestVO> findPast(Integer page, Integer pageSize, String userId);

    /**
     * Get contest statistics.
     *
     * @return the contest statistics
     */
    ContestStatsVO getStats();

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
     * @param page  the page number (1-based)
     * @param limit the number of items per page
     * @return paginated list of global rankings
     */
    PageResult<ContestRankingVO> getGlobalRankingsPaginated(Integer page, Integer limit);

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
}
