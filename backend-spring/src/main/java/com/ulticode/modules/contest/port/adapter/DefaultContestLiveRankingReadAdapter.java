package com.ulticode.modules.contest.port.adapter;

import com.ulticode.modules.contest.dto.LiveRankingEntryVO;
import com.ulticode.modules.contest.port.ContestLiveRankingReadPort;
import com.ulticode.modules.contest.service.impl.RankingServiceImpl;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default adapter of {@link ContestLiveRankingReadPort}.
 *
 * <p>Delegates to {@link RankingServiceImpl#readLiveRanking(String, int)},
 * which holds the actual SQL/sort/limit logic. Keeping the adapter as a
 * thin wrapper means the live-ranking read port is registered as its own
 * Spring bean (matching the ADR-0009 pattern) and the implementation
 * stays testable through the {@code RankingServiceImpl} constructor.
 *
 * @author ulticode
 */
@Component
public class DefaultContestLiveRankingReadAdapter implements ContestLiveRankingReadPort {

    private final RankingServiceImpl rankingServiceImpl;

    public DefaultContestLiveRankingReadAdapter(RankingServiceImpl rankingServiceImpl) {
        this.rankingServiceImpl = rankingServiceImpl;
    }

    @Override
    public List<LiveRankingEntryVO> readLiveRanking(String contestId, int limit) {
        return rankingServiceImpl.readLiveRanking(contestId, limit);
    }
}
