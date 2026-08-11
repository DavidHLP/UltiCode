package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.ContestParticipantReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Admin consumer adapter for the App-owned contest participant read contract.
 */
@Primary
@Component
public class DubboContestParticipantReadAdapter implements ContestParticipantReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", timeout = 3000, retries = 0, check = false)
    private ContestParticipantReadPort participantReadPort;

    @Override
    public long countByContestIds(List<String> contestIds) {
        return participantReadPort.countByContestIds(contestIds);
    }

    @Override
    public List<ParticipantInfo> findByContestIds(List<String> contestIds) {
        return participantReadPort.findByContestIds(contestIds);
    }
}
