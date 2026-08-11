package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.ContestRankingEntryDTO;
import com.ulticode.app.api.service.ContestLiveRankingReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.port.adapter.DefaultContestLiveRankingReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * Dubbo provider for {@link ContestLiveRankingReadPort} exported by
 * {@code backend-app} so backend-admin reads live rankings without importing
 * the contest module.
 *
 * <p>Delegates to the concrete {@link DefaultContestLiveRankingReadAdapter} —
 * never to the port interface itself — so the app bean graph keeps exactly
 * one primary local implementation plus this RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ContestLiveRankingReadProvider implements ContestLiveRankingReadPort {

    private final DefaultContestLiveRankingReadAdapter delegate;

    @Override
    public List<ContestRankingEntryDTO> readLiveRanking(String contestId, int limit) {
        return delegate.readLiveRanking(contestId, limit);
    }

    @Override
    public PageResult<ContestRankingEntryDTO> readLiveRankingPage(String contestId, int page, int limit) {
        return delegate.readLiveRankingPage(contestId, page, limit);
    }
}
