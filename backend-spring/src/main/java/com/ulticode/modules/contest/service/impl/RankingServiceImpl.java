package com.ulticode.modules.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.ContestRankingVO;
import com.ulticode.modules.contest.dto.LiveRankingEntryVO;
import com.ulticode.modules.contest.dto.RatingHistoryVO;
import com.ulticode.modules.contest.dto.UserContestHistoryVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of RankingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {

    private final ContestParticipantMapper participantMapper;
    private final ContestMapper contestMapper;

    @Override
    public PageResult<ContestRankingVO> getContestRanking(String contestId, Integer page, Integer limit) {
        if (contestId == null || contestId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "contestId is required");
        }

        // Set default pagination values
        int currentPage = (page != null && page > 0) ? page : 1;
        int currentLimit = (limit != null && limit > 0) ? limit : 50;

        // Limit page size
        currentLimit = Math.min(currentLimit, 100);

        // Fetch all participants with user data in single query (eliminates N+1)
        List<ContestParticipantMapper.ContestParticipantWithUser> allParticipants =
                participantMapper.selectParticipantsWithUserByContestId(contestId);

        // Filter to only ranked participants and apply manual pagination
        List<ContestParticipantMapper.ContestParticipantWithUser> rankedParticipants =
                allParticipants.stream()
                        .filter(p -> p.finalRank() != null)
                        .collect(Collectors.toList());

        int total = rankedParticipants.size();
        int skip = (currentPage - 1) * currentLimit;
        List<ContestRankingVO> rankingList = rankedParticipants.stream()
                .skip(skip)
                .limit(currentLimit)
                .map(this::toRankingVO)
                .collect(Collectors.toList());

        return PageResult.of(rankingList, (long) total, currentPage, currentLimit);
    }

    @Override
    public List<LiveRankingEntryVO> getLiveRanking(String contestId, Integer limit) {
        if (contestId == null || contestId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "contestId is required");
        }
        int currentLimit = (limit != null && limit > 0) ? limit : 100;
        currentLimit = Math.min(currentLimit, 200);

        // Fetch participants with user data joined
        List<ContestParticipantMapper.ContestParticipantWithUser> allParticipants =
                participantMapper.selectParticipantsWithUserByContestId(contestId);

        return allParticipants.stream()
                .filter(p -> p.totalScore() != null)
                .sorted(Comparator
                        .comparing(ContestParticipantMapper.ContestParticipantWithUser::totalScore, Comparator.reverseOrder())
                        .thenComparing(ContestParticipantMapper.ContestParticipantWithUser::totalPenalty, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(currentLimit)
                .map(this::toLiveRankingEntryVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserContestHistoryVO> getUserContestHistory(String userId) {
        LambdaQueryWrapper<ContestParticipant> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ContestParticipant::getUserId, userId)
                .isNotNull(ContestParticipant::getFinalRank)
                .orderByDesc(ContestParticipant::getRegisteredAt);

        List<ContestParticipant> participants = participantMapper.selectList(queryWrapper);
        if (participants.isEmpty()) {
            return List.of();
        }

        // Batch fetch contests to avoid N+1
        List<String> contestIds = participants.stream()
                .map(ContestParticipant::getContestId)
                .distinct()
                .toList();
        Map<String, Contest> contestsById = contestMapper.selectBatchIds(contestIds).stream()
                .collect(Collectors.toMap(Contest::getId, c -> c, (a, b) -> a));

        return participants.stream()
                .map(p -> toUserContestHistoryVO(p, contestsById.get(p.getContestId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<RatingHistoryVO> getUserRatingHistory(String userId) {
        // This would typically query a rating history table
        // For now, return empty list as rating history is not implemented yet
        return List.of();
    }

    /**
     * Convert ContestParticipant entity to ContestRankingVO (no user data).
     */
    private ContestRankingVO toRankingVO(ContestParticipant participant) {
        if (participant == null) {
            return null;
        }
        ContestRankingVO vo = new ContestRankingVO();
        vo.setRank(participant.getFinalRank());
        vo.setUserId(participant.getUserId());
        vo.setScore(participant.getTotalScore() != null ? participant.getTotalScore().longValue() : null);
        vo.setPenalty(participant.getTotalPenalty() != null ? participant.getTotalPenalty().longValue() : null);
        vo.setProblemsSolved(participant.getAttemptCount() != null ? participant.getAttemptCount() : 0);
        vo.setIsParticipating(true);
        return vo;
    }

    /**
     * Convert ContestParticipantWithUser DTO to ContestRankingVO.
     */
    private ContestRankingVO toRankingVO(ContestParticipantMapper.ContestParticipantWithUser participant) {
        if (participant == null) {
            return null;
        }

        ContestRankingVO vo = new ContestRankingVO();
        vo.setRank(participant.finalRank());
        vo.setUserId(participant.userId());
        vo.setScore(participant.totalScore() != null ? participant.totalScore().longValue() : null);
        vo.setPenalty(participant.totalPenalty() != null ? participant.totalPenalty().longValue() : null);
        vo.setProblemsSolved(participant.attemptCount() != null ? participant.attemptCount() : 0);
        vo.setIsParticipating(true);
        vo.setUsername(participant.username());
        vo.setName(participant.name());
        vo.setAvatar(participant.avatar());
        return vo;
    }

    /**
     * Convert ContestParticipantWithUser to LiveRankingEntryVO.
     */
    private LiveRankingEntryVO toLiveRankingEntryVO(ContestParticipantMapper.ContestParticipantWithUser participant) {
        if (participant == null) {
            return null;
        }
        LiveRankingEntryVO vo = new LiveRankingEntryVO();
        vo.setRank(null); // rank is computed dynamically for live ranking
        vo.setUserId(participant.userId());
        vo.setUsername(participant.username());
        vo.setName(participant.name());
        vo.setAvatar(participant.avatar());
        vo.setScore(participant.totalScore() != null ? participant.totalScore().longValue() : null);
        vo.setPenalty(participant.totalPenalty() != null ? participant.totalPenalty().longValue() : null);
        vo.setProblemsSolved(participant.attemptCount() != null ? participant.attemptCount() : 0);
        vo.setIsCurrentUser(null); // set by controller if needed
        return vo;
    }

    /**
     * Convert ContestParticipant and Contest to UserContestHistoryVO.
     */
    private UserContestHistoryVO toUserContestHistoryVO(ContestParticipant participant, Contest contest) {
        if (participant == null) {
            return null;
        }
        return new UserContestHistoryVO(
                participant.getContestId(),
                contest != null ? contest.getTitle() : null,
                contest != null ? contest.getSlug() : null,
                contest != null ? contest.getStartTime() : null,
                participant.getFinishedAt(),
                participant.getFinalRank(),
                participant.getTotalScore() != null ? participant.getTotalScore().longValue() : null,
                participant.getTotalPenalty() != null ? participant.getTotalPenalty().longValue() : null,
                participant.getAttemptCount() != null ? participant.getAttemptCount() : 0,
                null, // totalParticipants not readily available
                contest != null ? contest.getIsRated() : null
        );
    }
}
