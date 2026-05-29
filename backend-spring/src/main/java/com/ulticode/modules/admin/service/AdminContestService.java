package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.ContestAnnouncement;

import java.util.List;

/**
 * Service interface for admin contest operations.
 */
public interface AdminContestService {

    /**
     * Get paginated list of contests with filters.
     *
     * @param query the query parameters
     * @return paginated list of contests
     */
    PageResult<AdminContestVO> getContests(AdminContestQueryDTO query);

    /**
     * Get a contest by ID.
     *
     * @param id the contest ID
     * @return the contest VO
     */
    AdminContestVO getContest(String id);

    /**
     * Create a new contest with optional problem assignment.
     *
     * @param dto    the contest creation data
     * @param userId the creating admin's user ID
     * @return the created contest VO
     */
    AdminContestVO createContest(CreateContestDTO dto, String userId);

    /**
     * Update an existing contest (only UPCOMING status allowed).
     *
     * @param id  the contest ID
     * @param dto the update data
     * @return the updated contest VO
     */
    AdminContestVO updateContest(String id, UpdateContestDTO dto);

    /**
     * Soft-delete a contest (UPCOMING or FINISHED only).
     *
     * @param id the contest ID
     */
    void deleteContest(String id);

    /**
     * Start a contest (UPCOMING -> RUNNING, requires at least one problem).
     *
     * @param id the contest ID
     * @return the updated contest VO
     */
    AdminContestVO startContest(String id);

    /**
     * End a contest (RUNNING -> FINISHED).
     *
     * @param id the contest ID
     * @return the updated contest VO
     */
    AdminContestVO endContest(String id);

    // Announcement CRUD (D-11)

    /**
     * Create a contest announcement and push via WebSocket.
     *
     * @param contestId the contest ID
     * @param title     the announcement title
     * @param content   the announcement content
     * @param isPinned  whether to pin the announcement
     * @return the created announcement
     */
    ContestAnnouncement createAnnouncement(String contestId, String title, String content, Boolean isPinned);

    /**
     * Update an existing contest announcement.
     *
     * @param contestId      the contest ID
     * @param announcementId the announcement ID
     * @param title          the new title (optional)
     * @param content        the new content (optional)
     * @param isPinned       the new pinned status (optional)
     * @return the updated announcement
     */
    ContestAnnouncement updateAnnouncement(String contestId, String announcementId, String title, String content, Boolean isPinned);

    /**
     * Delete a contest announcement.
     *
     * @param contestId      the contest ID
     * @param announcementId the announcement ID
     */
    void deleteAnnouncement(String contestId, String announcementId);

    /**
     * Get all announcements for a contest, ordered by pinned status then creation time.
     *
     * @param contestId the contest ID
     * @return list of announcements
     */
    List<ContestAnnouncement> getAnnouncements(String contestId);

    /**
     * Get contest rankings (live ranking for admin view).
     *
     * @param contestId the contest ID
     * @return list of contest rankings
     */
    List<com.ulticode.modules.contest.dto.LiveRankingEntryVO> getRankings(String contestId);

    com.ulticode.modules.contest.entity.ContestProblem addProblemToContest(String contestId, Long problemId, Integer score);
}
