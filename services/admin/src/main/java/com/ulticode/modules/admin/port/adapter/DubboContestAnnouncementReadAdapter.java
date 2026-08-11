package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.ContestAnnouncementDTO;
import com.ulticode.app.api.service.ContestAnnouncementReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Admin consumer adapter for the App-owned contest announcement read contract.
 */
@Primary
@Component
public class DubboContestAnnouncementReadAdapter implements ContestAnnouncementReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", timeout = 3000, retries = 0, check = false)
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
