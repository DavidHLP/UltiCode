package com.ulticode.modules.contest.port.adapter;

import com.ulticode.app.api.dto.ContestRankingEntryDTO;
import com.ulticode.app.api.service.ContestLiveRankingReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.LiveRankingEntryVO;
import com.ulticode.modules.contest.service.impl.RankingServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default adapter of {@link ContestLiveRankingReadPort}.
 *
 * <p>Delegates to {@link RankingServiceImpl#readLiveRanking(String, int)},
 * then maps the internal {@link LiveRankingEntryVO} to the entity-free
 * {@link ContestRankingEntryDTO} so the contract stays decoupled.
 *
 * @author ulticode
 */
@Component
@Primary
public class DefaultContestLiveRankingReadAdapter implements ContestLiveRankingReadPort {

    private final RankingServiceImpl rankingServiceImpl;

    public DefaultContestLiveRankingReadAdapter(RankingServiceImpl rankingServiceImpl) {
        this.rankingServiceImpl = rankingServiceImpl;
    }

    @Override
    public List<ContestRankingEntryDTO> readLiveRanking(String contestId, int limit) {
        return rankingServiceImpl.readLiveRanking(contestId, limit).stream()
                .map(DefaultContestLiveRankingReadAdapter::toDTO)
                .toList();
    }

    @Override
    public PageResult<ContestRankingEntryDTO> readLiveRankingPage(String contestId, int page, int limit) {
        PageResult<LiveRankingEntryVO> result = rankingServiceImpl.readLiveRankingPage(contestId, page, limit);
        List<ContestRankingEntryDTO> items = result.getItems().stream()
                .map(DefaultContestLiveRankingReadAdapter::toDTO)
                .toList();
        return PageResult.of(items, result.getTotal(), result.getPage(), result.getPageSize());
    }

    private static ContestRankingEntryDTO toDTO(LiveRankingEntryVO vo) {
        ContestRankingEntryDTO dto = new ContestRankingEntryDTO();
        dto.setRank(vo.getRank());
        dto.setUserId(vo.getUserId());
        dto.setUsername(vo.getUsername());
        dto.setName(vo.getName());
        dto.setAvatar(vo.getAvatar());
        dto.setScore(vo.getScore());
        dto.setPenalty(vo.getPenalty());
        dto.setProblemsSolved(vo.getProblemsSolved());
        dto.setIsCurrentUser(vo.getIsCurrentUser());
        return dto;
    }
}
