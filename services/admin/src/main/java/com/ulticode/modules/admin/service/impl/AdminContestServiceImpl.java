package com.ulticode.modules.admin.service.impl;

import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.dto.ContestAnnouncementDTO;
import com.ulticode.app.api.dto.ContestRankingEntryDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.app.api.service.ContestAnnouncementReadPort;
import com.ulticode.app.api.service.ContestLiveRankingReadPort;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.projection.AdminContestProjection;
import com.ulticode.modules.admin.service.AdminContestService;
import com.ulticode.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read facade for the admin contest surface.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7: all contest entity/mapper imports
 * replaced with app-api read-ports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminContestServiceImpl implements AdminContestService {

    private final ContestAdminReadPort contestAdminReadPort;
    private final ContestAnnouncementReadPort contestAnnouncementReadPort;
    private final ContestLiveRankingReadPort liveRankingReadPort;
    private final AdminContestProjection adminContestProjection;

    @Override
    public PageResult<AdminContestVO> getContests(AdminContestQueryDTO query) {
        return adminContestProjection.getContests(query);
    }

    @Override
    public AdminContestVO getContest(String id) {
        return adminContestProjection.getContest(id);
    }

    @Override
    public List<ContestAnnouncementDTO> getAnnouncements(String contestId) {
        return contestAnnouncementReadPort.findByContestIdOrderByCreatedAtDesc(contestId);
    }

    @Override
    public PageResult<ContestRankingEntryDTO> getRankings(String contestId, int page, int limit) {
        ContestAdminDTO contest = contestAdminReadPort.selectByIdOrSlug(contestId);
        if (contest == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Contest not found");
        }
        return liveRankingReadPort.readLiveRankingPage(contest.getId(), page, limit);
    }
}
