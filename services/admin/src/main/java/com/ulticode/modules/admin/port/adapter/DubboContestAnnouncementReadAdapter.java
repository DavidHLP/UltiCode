package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.ContestAnnouncementDTO;
import com.ulticode.app.api.service.ContestAnnouncementReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import com.ulticode.common.rpc.RpcPolicy;

/**
 * Admin consumer adapter for the App-owned contest announcement read contract.
 */
@Primary
@Component
public class DubboContestAnnouncementReadAdapter implements ContestAnnouncementReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ContestAnnouncementReadPort announcementReadPort;

    @Override
    public List<ContestAnnouncementDTO> findByContestIdOrderByCreatedAtDesc(String contestId) {
        return announcementReadPort.findByContestIdOrderByCreatedAtDesc(contestId);
    }

    @Override
    public ContestAnnouncementDTO findByContestIdAndId(String contestId, String announcementId) {
        return announcementReadPort.findByContestIdAndId(contestId, announcementId);
    }
}
