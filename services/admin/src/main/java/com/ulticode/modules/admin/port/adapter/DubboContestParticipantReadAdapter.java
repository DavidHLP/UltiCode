package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.ContestParticipantReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import com.ulticode.common.rpc.RpcPolicy;

/**
 * Admin consumer adapter for the App-owned contest participant read contract.
 */
@Primary
@Component
public class DubboContestParticipantReadAdapter implements ContestParticipantReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
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
