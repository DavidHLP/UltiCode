package com.ulticode.modules.contest.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.ContestRankingVO;
import com.ulticode.modules.contest.dto.UserContestHistoryVO;

import java.util.List;

/**
 * Service interface for ranking-related operations that stay inside the
 * contest module (paginated snapshots and user history).
 *
 * <p><b>Note:</b> live-ranking reads are exposed to external modules
 * (websocket, admin) through
 * {@link com.ulticode.modules.contest.port.ContestLiveRankingReadPort}
 * rather than this service, so that no cross-module caller needs to
 * import the contest module's internal ranking API. See ADR-0010 for
 * the seam-inversion rationale.
 *
 * @author ulticode
 */
public interface RankingService {

    /**
     * Get a public contest ranking with pagination.
     *
     * <p>Invisible or soft-deleted contests are reported as not found. Admin
     * reads use {@link #getContestRanking(String, Integer, Integer)} instead.
     *
     * @param contestId the contest ID
     * @param page      the page number (1-based)
     * @param limit     the number of items per page
     * @return paginated list of rankings
     */
    PageResult<ContestRankingVO> getPublicContestRanking(String contestId, Integer page, Integer limit);

    /**
     * Get contest ranking with pagination for an owner/admin read.
     *
     * @param contestId the contest ID
     * @param page      the page number (1-based)
     * @param limit     the number of items per page
     * @return paginated list of rankings
     */
    PageResult<ContestRankingVO> getContestRanking(String contestId, Integer page, Integer limit);

    /**
     * Get user contest history.
     *
     * @param userId the user ID
     * @return list of contest rankings for the user
     */
    List<UserContestHistoryVO> getUserContestHistory(String userId);

}
