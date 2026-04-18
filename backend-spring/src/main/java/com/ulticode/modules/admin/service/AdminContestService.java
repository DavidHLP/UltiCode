package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;

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
}
