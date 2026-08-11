package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ContestRankingEntryDTO;
import com.ulticode.common.response.PageResult;

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

    /**
     * Read a page of the current live ranking for a contest.
     *
     * <p>Unlike {@link #readLiveRanking(String, int)} (which clamps to a
     * hard cap), the returned page reports the <em>full</em> ranked
     * participant count as {@code total} and assigns ranks as
     * {@code offset + index + 1} so pagination is stable and complete.
     *
     * <p>The default throws so existing implementors keep compiling; owner,
     * Dubbo provider, and admin consumer adapters override with the real
     * paginated read.
     *
     * @param contestId the contest id (must not be {@code null} or blank)
     * @param page      1-based page number
     * @param limit     page size (clamped to the platform hard cap)
     * @return paginated live ranking entries sorted by score (desc), then
     *         penalty (asc); never {@code null}; possibly empty
     */
    default PageResult<ContestRankingEntryDTO> readLiveRankingPage(String contestId, int page, int limit) {
        throw new UnsupportedOperationException(
                "readLiveRankingPage is not implemented by this ContestLiveRankingReadPort");
    }
}
