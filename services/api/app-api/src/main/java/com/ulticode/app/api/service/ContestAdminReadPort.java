package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.common.response.PageResult;

import java.util.List;

/**
 * Entity-free contest read port consumed by backend-admin projections,
 * services, and adapters after the contest family relocated to backend-app.
 *
 * <p>Method set is the exact minimum derived from verified admin consumer
 * call sites (P7-RELOCATE-CONTEST-001 AC #7).
 *
 * @author ulticode
 */
public interface ContestAdminReadPort {

    /**
     * Fetch a single contest by id (for audit before-state and detail views).
     *
     * @param id the contest id
     * @return the contest DTO, or {@code null} when not found
     */
    ContestAdminDTO selectById(String id);

    /**
     * Paginated contest listing for admin projection.
     *
     * @param page      1-based page
     * @param size      page size
     * @param keyword   optional title/slug search (nullable)
     * @param status    optional status filter (nullable)
     * @param contestType optional type filter (nullable)
     * @return paginated contest DTOs
     */
    PageResult<ContestAdminDTO> selectPage(int page, int size, String keyword, String status, String contestType);

    /**
     * List all contests for analytics (optionally filtered by status names).
     *
     * @param statusNames optional status filter (null/empty = all)
     * @return contest DTOs
     */
    List<ContestAdminDTO> selectAll(List<String> statusNames);

    /**
     * Count contests (optionally filtered by status).
     *
     * @param statusName optional status filter (null = all)
     * @return contest count
     */
    long countByStatus(String statusName);

    /**
     * Count problems attached to a contest.
     *
     * @param contestId the contest id
     * @return problem count
     */
    long countProblemsByContestId(String contestId);

    /**
     * List contests starting after the given time (for analytics).
     *
     * @param afterStartTime the minimum start time (inclusive); null = no filter
     * @return contest DTOs
     */
    List<ContestAdminDTO> selectByStartTimeAfter(java.time.LocalDateTime afterStartTime);
}
