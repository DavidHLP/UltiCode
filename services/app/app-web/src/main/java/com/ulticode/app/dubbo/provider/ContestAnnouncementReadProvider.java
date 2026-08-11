package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.ContestAnnouncementDTO;
import com.ulticode.app.api.service.ContestAnnouncementReadPort;
import com.ulticode.modules.contest.port.adapter.DefaultContestAnnouncementReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * Dubbo provider for the App-owned contest announcement read contract.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ContestAnnouncementReadProvider implements ContestAnnouncementReadPort {

    private final DefaultContestAnnouncementReadAdapter delegate;

    @Override
    public List<ContestAnnouncementDTO> findByContestIdOrderByCreatedAtDesc(String contestId) {
        return delegate.findByContestIdOrderByCreatedAtDesc(contestId);
    }

    @Override
    public ContestAnnouncementDTO findByContestIdAndId(String contestId, String announcementId) {
        return delegate.findByContestIdAndId(contestId, announcementId);
    }
}
