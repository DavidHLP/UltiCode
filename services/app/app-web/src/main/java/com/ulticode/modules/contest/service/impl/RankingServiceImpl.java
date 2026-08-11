package com.ulticode.modules.contest.service.impl;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.app.error.ContestErrorCode;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.contest.dto.ContestRankingVO;
import com.ulticode.modules.contest.dto.LiveRankingEntryVO;
import com.ulticode.modules.contest.dto.UserContestHistoryVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.scoring.ContestRankingComparator;
import com.ulticode.modules.contest.scoring.ScoringStrategyResolver;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Default implementation of {@link RankingService}. The live-ranking
 * read is exposed via the {@link ContestLiveRankingReadPort} port through
 * {@code DefaultContestLiveRankingReadAdapter} so external modules
 * (websocket, admin) and the contest module's own controllers depend on
 * a narrow intent surface rather than the full {@code RankingService}
 * API. See ADR-0010.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {

    private static final int DEFAULT_LIVE_LIMIT = 100;
    private static final int MAX_LIVE_LIMIT = 200;

    private final ContestParticipantMapper participantMapper;
    private final ContestMapper contestMapper;
    private final ScoringStrategyResolver scoringStrategyResolver;
    private final SubmissionUserReadPort submissionUserReadPort;

    /**
     * Public ranking read. Invisible contests are indistinguishable from
     * missing contests; admin uses {@link #getContestRanking} instead.
     */
    @Override
    public PageResult<ContestRankingVO> getPublicContestRanking(
            String contestId, Integer page, Integer limit) {
        requirePublicContest(contestId);
        return getContestRanking(contestId, page, limit);
    }

    /**
     * Public live ranking read with the same visibility rule as the catalog.
     */
    public List<LiveRankingEntryVO> readPublicLiveRanking(String contestId, int limit) {
        requirePublicContest(contestId);
        return readLiveRanking(contestId, limit);
    }

    private void requirePublicContest(String contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null
                || Boolean.TRUE.equals(contest.getIsDeleted())
                || !Boolean.TRUE.equals(contest.getIsVisible())) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_FOUND);
        }
    }

    @Override
    public PageResult<ContestRankingVO> getContestRanking(String contestId, Integer page, Integer limit) {
        if (contestId == null || contestId.isBlank()) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "contestId is required");
        }

        PaginationRequest pageRequest = PaginationRequest.of(page, limit, 50);
        int currentPage = pageRequest.page();
        int currentLimit = pageRequest.pageSize();

        long total = participantMapper.countRankedParticipantsByContestId(contestId);
        int offset = (int) ((long) (currentPage - 1) * currentLimit);
        List<ContestParticipantMapper.ContestParticipantWithUser> rankedParticipants =
                participantMapper.selectParticipantsWithUserByContestIdPaginated(contestId, currentLimit, offset);
        Map<String, SubmissionUserReadPort.UserSummary> userSummaries = findUserSummaries(rankedParticipants);
        List<ContestRankingVO> rankingList = rankedParticipants.stream()
                .map(participant -> toRankingVO(participant, userSummaries.get(participant.userId())))
                .collect(Collectors.toList());

        return PageResult.of(rankingList, total, currentPage, currentLimit);
    }

    /**
     * Read the current live ranking for a contest. Public so the
     * {@code DefaultContestLiveRankingReadAdapter} can delegate without
     * the impl having to expose the port interface itself.
     */
    public List<LiveRankingEntryVO> readLiveRanking(String contestId, int limit) {
        if (contestId == null || contestId.isBlank()) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "contestId is required");
        }
        int currentLimit = (limit > 0) ? limit : DEFAULT_LIVE_LIMIT;
        currentLimit = Math.min(currentLimit, MAX_LIVE_LIMIT);

        List<ContestParticipantMapper.ContestParticipantWithUser> ranked = rankAll(contestId);
        Map<String, SubmissionUserReadPort.UserSummary> userSummaries = findUserSummaries(ranked);
        int count = Math.min(currentLimit, ranked.size());
        return IntStream.range(0, count)
                .mapToObj(index -> toLiveRankingEntryVO(
                        ranked.get(index), index + 1, userSummaries.get(ranked.get(index).userId())))
                .collect(Collectors.toList());
    }

    /**
     * Paginated live ranking read. Unlike {@link #readLiveRanking(String, int)}
     * (hard-capped), the returned page reports the full ranked participant
     * count as total and assigns ranks as {@code offset + index + 1}, so
     * admin pagination stays stable and complete.
     */
    public PageResult<LiveRankingEntryVO> readLiveRankingPage(String contestId, int page, int limit) {
        if (contestId == null || contestId.isBlank()) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "contestId is required");
        }
        PaginationRequest pageRequest = PaginationRequest.of(page, limit, 50);
        int currentPage = pageRequest.page();
        int currentLimit = pageRequest.pageSize();

        List<ContestParticipantMapper.ContestParticipantWithUser> ranked = rankAll(contestId);
        Map<String, SubmissionUserReadPort.UserSummary> userSummaries = findUserSummaries(ranked);
        long total = ranked.size();
        long offset = pageRequest.offset();
        int from = (int) Math.min(offset, ranked.size());
        int to = (int) Math.min(offset + currentLimit, ranked.size());
        List<LiveRankingEntryVO> items = IntStream.range(from, to)
                .mapToObj(index -> toLiveRankingEntryVO(
                        ranked.get(index), index + 1, userSummaries.get(ranked.get(index).userId())))
                .collect(Collectors.toList());
        return PageResult.of(items, total, currentPage, currentLimit);
    }

    /**
     * Rank every scored participant of a contest (no cap), using the
     * contest's scoring mode and tie breaker.
     */
    private List<ContestParticipantMapper.ContestParticipantWithUser> rankAll(String contestId) {
        List<ContestParticipantMapper.ContestParticipantWithUser> allParticipants =
                participantMapper.selectParticipantsWithUserByContestId(contestId);
        Contest contest = contestMapper.selectById(contestId);
        Comparator<ContestParticipantMapper.ContestParticipantWithUser> comparator =
                scoringStrategyResolver.resolveFromString(contest == null ? null : contest.getScoringMode())
                        .getRankingComparator(ContestRankingComparator.resolveTieBreaker(
                                contest == null ? null : contest.getTieBreaker()));
        return allParticipants.stream()
                .filter(p -> p.totalScore() != null)
                .sorted(comparator)
                .toList();
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
        List<String> participantIds = participants.stream()
                .map(ContestParticipant::getId)
                .toList();
        Map<String, Integer> solvedByParticipantId = participantMapper
                .countSolvedByParticipantIds(participantIds).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("participantId"),
                        row -> ((Number) row.get("solvedCount")).intValue(),
                        (a, b) -> a));

        return participants.stream()
                .map(p -> toUserContestHistoryVO(
                        p, contestsById.get(p.getContestId()), solvedByParticipantId.getOrDefault(p.getId(), 0)))
                .collect(Collectors.toList());
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
        vo.setProblemsSolved(null);
        vo.setIsParticipating(true);
        return vo;
    }

    private Map<String, SubmissionUserReadPort.UserSummary> findUserSummaries(
            List<ContestParticipantMapper.ContestParticipantWithUser> participants) {
        List<String> userIds = participants.stream()
                .map(ContestParticipantMapper.ContestParticipantWithUser::userId)
                .filter(userId -> userId != null && !userId.isBlank())
                .distinct()
                .toList();
        Map<String, SubmissionUserReadPort.UserSummary> summaries = submissionUserReadPort.findAllById(userIds);
        return summaries == null ? Map.of() : summaries;
    }

    /**
     * Convert ContestParticipantWithUser DTO to ContestRankingVO.
     */
    private ContestRankingVO toRankingVO(
            ContestParticipantMapper.ContestParticipantWithUser participant,
            SubmissionUserReadPort.UserSummary userSummary) {
        if (participant == null) {
            return null;
        }

        ContestRankingVO vo = new ContestRankingVO();
        vo.setRank(participant.finalRank());
        vo.setUserId(participant.userId());
        vo.setScore(participant.totalScore() != null ? participant.totalScore().longValue() : null);
        vo.setPenalty(participant.totalPenalty() != null ? participant.totalPenalty().longValue() : null);
        vo.setProblemsSolved(participant.problemsSolved());
        vo.setIsParticipating(true);
        vo.setUsername(userSummary != null && userSummary.username() != null
                ? userSummary.username() : participant.username());
        vo.setName(userSummary != null ? userSummary.name() : participant.name());
        vo.setAvatar(userSummary != null ? userSummary.avatar() : participant.avatar());
        return vo;
    }

    /**
     * Convert ContestParticipantWithUser to LiveRankingEntryVO.
     */
    private LiveRankingEntryVO toLiveRankingEntryVO(
            ContestParticipantMapper.ContestParticipantWithUser participant,
            int rank,
            SubmissionUserReadPort.UserSummary userSummary) {
        if (participant == null) {
            return null;
        }
        LiveRankingEntryVO vo = new LiveRankingEntryVO();
        vo.setRank(rank);
        vo.setUserId(participant.userId());
        vo.setUsername(userSummary != null && userSummary.username() != null
                ? userSummary.username() : participant.username());
        vo.setName(userSummary != null ? userSummary.name() : participant.name());
        vo.setAvatar(userSummary != null ? userSummary.avatar() : participant.avatar());
        vo.setScore(participant.totalScore() != null ? participant.totalScore().longValue() : null);
        vo.setPenalty(participant.totalPenalty() != null ? participant.totalPenalty().longValue() : null);
        vo.setProblemsSolved(participant.problemsSolved());
        vo.setIsCurrentUser(null); // set by controller if needed
        return vo;
    }

    /**
     * Convert ContestParticipant and Contest to UserContestHistoryVO.
     */
    private UserContestHistoryVO toUserContestHistoryVO(
            ContestParticipant participant, Contest contest, int problemsSolved) {
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
                problemsSolved,
                null, // totalParticipants not readily available
                contest != null ? contest.getIsRated() : null
        );
    }
}
