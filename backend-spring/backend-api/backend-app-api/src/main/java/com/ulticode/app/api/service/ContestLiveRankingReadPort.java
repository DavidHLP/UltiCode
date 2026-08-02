package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ContestRankingEntryDTO;

import java.util.List;

/**
 * Live-ranking read port exposed to external modules (websocket, admin) so
 * they can fetch the current leaderboard without importing contest entities
 * or services.
 *
 * <p>Promoted from {@code com.ulticode.modules.contest.port.ContestLiveRankingReadPort}
 * during P7-RELOCATE-CONTEST-001.
 *
 * @author ulticode
 */
public interface ContestLiveRankingReadPort {

    /**
     * Read the current live ranking for a contest.
     *
     * @param contestId the contest id (must not be {@code null} or blank)
     * @param limit     the maximum number of entries to return; values
     *                  &le; 0 fall back to a sensible default, and values
     *                  above the implementation's hard cap are clamped down
     * @return list of live ranking entries sorted by score (desc), then
     *         penalty (asc); never {@code null}; possibly empty
     */
    List<ContestRankingEntryDTO> readLiveRanking(String contestId, int limit);
}
