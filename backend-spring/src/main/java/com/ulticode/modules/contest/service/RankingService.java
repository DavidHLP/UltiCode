package com.ulticode.modules.contest.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.ContestRankingVO;
import com.ulticode.modules.contest.dto.LiveRankingEntryVO;
import com.ulticode.modules.contest.dto.UserContestHistoryVO;

import java.util.List;

/**
 * Service interface for ranking-related operations.
 */
public interface RankingService {

    /**
     * Get contest ranking with pagination.
     *
     * @param contestId the contest ID
     * @param page      the page number (1-based)
     * @param limit     the number of items per page
     * @return paginated list of rankings
     */
    PageResult<ContestRankingVO> getContestRanking(String contestId, Integer page, Integer limit);

    /**
     * Get live ranking for a contest.
     *
     * @param contestId the contest ID
     * @param limit     the maximum number of rankings to return
     * @return list of live rankings
     */
    List<LiveRankingEntryVO> getLiveRanking(String contestId, Integer limit);

    /**
     * Get user contest history.
     *
     * @param userId the user ID
     * @return list of contest rankings for the user
     */
    List<UserContestHistoryVO> getUserContestHistory(String userId);

}
