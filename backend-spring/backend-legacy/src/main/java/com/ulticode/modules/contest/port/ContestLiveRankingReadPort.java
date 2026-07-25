package com.ulticode.modules.contest.port;

import com.ulticode.modules.contest.dto.LiveRankingEntryVO;

import java.util.List;

/**
 * Live-ranking read port the contest module exposes to external modules
 * (websocket, admin) so they can fetch the current leaderboard without
 * reaching across into {@code com.ulticode.modules.contest.service.RankingService}.
 *
 * <p>Replaces the two cross-module leak points that previously
 * imported {@code RankingService.getLiveRanking} directly:
 * <ul>
 *   <li>{@code WebSocketContestRankingFlusher.flushPendingRankings} —
 *       reads the live leaderboard every flush tick (cap 200).</li>
 *   <li>{@code AdminContestServiceImpl.getRankings} — reads the live
 *       leaderboard for the admin console (cap 100).</li>
 * </ul>
 *
 * <p>The port owns one method (read) rather than exposing the entire
 * {@code RankingService} surface. Callers that only need a paginated
 * snapshot or a user history continue to depend on the internal
 * {@code RankingService} — those callers are all inside the contest
 * module, so no cross-module leak exists on those methods.
 *
 * <p>Contract: read-only, no side effects. Implementations MUST honour
 * the {@code limit} cap (the {@code RankingServiceImpl} already enforces
 * a 200-item hard ceiling on top of the requested limit). Implementations
 * SHOULD throw {@code BusinessException(BAD_REQUEST)} when
 * {@code contestId} is {@code null} or blank, matching the legacy
 * {@code RankingService.getLiveRanking} contract.
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
     *         penalty (asc); never {@code null}; possibly empty if no
     *         participants have a score yet
     */
    List<LiveRankingEntryVO> readLiveRanking(String contestId, int limit);
}
