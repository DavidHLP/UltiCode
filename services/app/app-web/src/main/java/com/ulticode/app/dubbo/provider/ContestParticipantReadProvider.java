package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.ContestParticipantReadPort;
import com.ulticode.modules.contest.port.adapter.DefaultContestParticipantReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * Dubbo provider for the App-owned contest participant read contract.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ContestParticipantReadProvider implements ContestParticipantReadPort {

    private final DefaultContestParticipantReadAdapter delegate;

    @Override
    public long countByContestIds(List<String> contestIds) {
        return delegate.countByContestIds(contestIds);
    }

    @Override
    public List<ParticipantInfo> findByContestIds(List<String> contestIds) {
        return delegate.findByContestIds(contestIds);
    }
}
