package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.ContestRankingEntryDTO;
import com.ulticode.app.api.service.ContestLiveRankingReadPort;
import com.ulticode.common.response.PageResult;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dubbo consumer adapter registering {@link ContestLiveRankingReadPort} as a
 * local admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.ContestLiveRankingReadProvider}).
 *
 * <p>Admin services keep depending on the entity-free port contract; this
 * adapter is the only local bean of that type.
 */
@Primary
@Component
public class DubboContestLiveRankingReadAdapter implements ContestLiveRankingReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", timeout = 3000, retries = 0, check = false)
    private ContestLiveRankingReadPort liveRankingReadPort;

    @Override
    public List<ContestRankingEntryDTO> readLiveRanking(String contestId, int limit) {
        return liveRankingReadPort.readLiveRanking(contestId, limit);
    }

    @Override
    public PageResult<ContestRankingEntryDTO> readLiveRankingPage(String contestId, int page, int limit) {
        return liveRankingReadPort.readLiveRankingPage(contestId, page, limit);
    }
}
