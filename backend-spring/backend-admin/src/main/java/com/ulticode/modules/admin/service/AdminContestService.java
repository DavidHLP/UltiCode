package com.ulticode.modules.admin.service;

import com.ulticode.app.api.dto.ContestAnnouncementDTO;
import com.ulticode.app.api.dto.ContestRankingEntryDTO;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;

import java.util.List;

/**
 * Read facade for the admin contest surface.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7: return types changed from contest
 * entities to app-api DTOs.
 */
public interface AdminContestService {

    PageResult<AdminContestVO> getContests(AdminContestQueryDTO query);

    AdminContestVO getContest(String id);

    List<ContestAnnouncementDTO> getAnnouncements(String contestId);

    List<ContestRankingEntryDTO> getRankings(String contestId);
}
