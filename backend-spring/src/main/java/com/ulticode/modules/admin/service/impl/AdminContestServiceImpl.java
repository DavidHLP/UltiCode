package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.projection.AdminContestProjection;
import com.ulticode.modules.admin.service.AdminContestService;
import com.ulticode.modules.contest.dto.LiveRankingEntryVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.port.ContestLiveRankingReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read facade for the admin contest surface.
 *
 * <p>After the write state machine moved to
 * {@link AdminContestMutationServiceImpl}, this service keeps only the read
 * paths the admin contest surface exposes:
 * <ul>
 *   <li>{@link #getContests} and {@link #getContest} delegate to
 *       {@link AdminContestProjection} &mdash; the ADR-0011 read module that
 *       owns every entity-to-{@link AdminContestVO} shape and the
 *       cross-module problem-count enrichment.</li>
 *   <li>{@link #getAnnouncements} reads the announcement list from
 *       {@link ContestAnnouncementMapper} (ordered by pinned then recency).</li>
 *   <li>{@link #getRankings} validates the contest exists then reads the
 *       live ranking through {@link ContestLiveRankingReadPort}.</li>
 * </ul>
 *
 * <p>The read contract is unchanged from the legacy single-service shape;
 * only the writes moved. This completes the write side of ADR-0011 Stage 3
 * <em>without</em> reopening the projection decision.
 *
 * @author ulticode
 * @see AdminContestMutationServiceImpl the write module
 * @see AdminContestProjection the read module
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminContestServiceImpl implements AdminContestService {

    private final ContestMapper contestMapper;
    private final ContestAnnouncementMapper contestAnnouncementMapper;
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
    public List<ContestAnnouncement> getAnnouncements(String contestId) {
        return contestAnnouncementMapper.findByContestIdOrderByCreatedAtDesc(contestId);
    }

    @Override
    public List<LiveRankingEntryVO> getRankings(String contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return liveRankingReadPort.readLiveRanking(contestId, 100);
    }
}
