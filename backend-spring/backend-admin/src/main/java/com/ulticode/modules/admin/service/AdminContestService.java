package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.app.api.dto.ContestRankingEntryDTO;
import com.ulticode.modules.contest.entity.ContestAnnouncement;

import java.util.List;

/**
 * Read facade for the admin contest surface.
 *
 * <p>After the write side was concentrated behind
 * {@link AdminContestMutationService}, this interface keeps the read paths
 * the admin contest surface exposes: the paginated list, the single detail,
 * the announcement list, and the live-ranking passthrough.
 *
 * <p>The list and detail reads delegate to
 * {@link com.ulticode.modules.admin.projection.AdminContestProjection} &mdash;
 * the ADR-0011 read module that owns every entity-to-VO shape. The
 * announcement list and rankings reads stay here as thin mappers over the
 * contest module. This preserves the read contract exactly; the projection
 * decision is <em>not</em> reopened.
 *
 * @author ulticode
 * @see AdminContestMutationService the write module
 * @see com.ulticode.modules.admin.projection.AdminContestProjection the read module
 */
public interface AdminContestService {

    /**
     * Get a paginated list of contests with filters (delegates to the projection).
     *
     * @param query the query parameters
     * @return paginated list of admin contest VOs
     */
    PageResult<AdminContestVO> getContests(AdminContestQueryDTO query);

    /**
     * Get a single contest by ID (delegates to the projection).
     *
     * @param id the contest ID
     * @return the admin contest VO
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#CONTEST_NOT_FOUND}
     *         when the contest does not exist
     */
    AdminContestVO getContest(String id);

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
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.exception.ErrorCode#CONTEST_NOT_FOUND}
     *         when the contest does not exist
     */
    List<ContestRankingEntryDTO> getRankings(String contestId);
}
