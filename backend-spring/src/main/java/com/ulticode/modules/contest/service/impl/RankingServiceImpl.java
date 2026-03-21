package com.ulticode.modules.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.ContestRankingVO;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of RankingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {

    private final ContestParticipantMapper participantMapper;

    @Override
    public PageResult<ContestRankingVO> getContestRanking(String contestId, Integer page, Integer limit) {
        // Set default pagination values
        int currentPage = (page != null && page > 0) ? page : 1;
        int currentLimit = (limit != null && limit > 0) ? limit : 50;

        // Limit page size
        currentLimit = Math.min(currentLimit, 100);

        LambdaQueryWrapper<ContestParticipant> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ContestParticipant::getContestId, contestId)
                .isNotNull(ContestParticipant::getFinalRank)
                .orderByAsc(ContestParticipant::getFinalRank);

        Page<ContestParticipant> participantPage = new Page<>(currentPage, currentLimit);
        Page<ContestParticipant> result = participantMapper.selectPage(participantPage, queryWrapper);

        List<ContestRankingVO> rankingList = result.getRecords().stream()
                .map(this::toRankingVO)
                .collect(Collectors.toList());

        return PageResult.of(rankingList, result.getTotal(), currentPage, currentLimit);
    }

    @Override
    public List<ContestRankingVO> getLiveRanking(String contestId, Integer limit) {
        int currentLimit = (limit != null && limit > 0) ? limit : 100;
        currentLimit = Math.min(currentLimit, 200);

        LambdaQueryWrapper<ContestParticipant> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ContestParticipant::getContestId, contestId)
                .isNotNull(ContestParticipant::getTotalScore)
                .orderByDesc(ContestParticipant::getTotalScore)
                .orderByAsc(ContestParticipant::getTotalPenalty)
                .last("LIMIT " + currentLimit);

        List<ContestParticipant> participants = participantMapper.selectList(queryWrapper);

        return participants.stream()
                .map(this::toRankingVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ContestRankingVO> getUserContestHistory(String userId) {
        LambdaQueryWrapper<ContestParticipant> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ContestParticipant::getUserId, userId)
                .isNotNull(ContestParticipant::getFinalRank)
                .orderByDesc(ContestParticipant::getRegisteredAt);

        List<ContestParticipant> participants = participantMapper.selectList(queryWrapper);

        return participants.stream()
                .map(this::toRankingVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ContestRankingVO> getUserRatingHistory(String userId) {
        // This would typically query a rating history table
        // For now, return empty list as rating history is not implemented yet
        return List.of();
    }

    /**
     * Convert ContestParticipant to ContestRankingVO.
     */
    private ContestRankingVO toRankingVO(ContestParticipant participant) {
        if (participant == null) {
            return null;
        }

        ContestRankingVO vo = new ContestRankingVO();
        vo.setRank(participant.getFinalRank());
        vo.setUserId(Long.parseLong(participant.getUserId()));
        vo.setScore(participant.getTotalScore() != null ? participant.getTotalScore().longValue() : null);
        vo.setPenalty(participant.getTotalPenalty() != null ? participant.getTotalPenalty().longValue() : null);
        vo.setProblemsSolved(participant.getAttemptCount() != null ? participant.getAttemptCount() : 0);
        vo.setIsParticipating(true);
        return vo;
    }
}
