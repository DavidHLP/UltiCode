package com.ulticode.modules.contest.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.ContestListVO;
import com.ulticode.modules.contest.dto.ContestProblemVO;
import com.ulticode.modules.contest.dto.ContestQueryDTO;
import com.ulticode.modules.contest.dto.ContestRankingVO;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.GlobalContestStatsVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.GlobalRanking;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link ContestProjection}. Owns every
 * entity-to-VO projection rule and read-side aggregation for the contest
 * domain — see the interface javadoc for why this is a deep module.
 *
 * <p>All methods are pure reads; none mutate contest state. The existence
 * checks on the single-item endpoints throw {@link ErrorCode#CONTEST_NOT_FOUND}
 * when the contest is missing or soft-deleted, preserving the contract observed
 * by the catalog, admin and submission-bridge controllers.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultContestProjection implements ContestProjection {

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestParticipantMapper participantMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final GlobalRankingMapper globalRankingMapper;
    private final ContestAnnouncementMapper contestAnnouncementMapper;
    private final ProblemFactsPort problemFactsPort;
    private final RankingService rankingService;
    private final SubmissionReadPort submissionProjection;

    @Override
    public java.util.List<ContestVO> findUserContests(String userId, String type) {
        if (userId == null || userId.isBlank()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<ContestParticipant> all = participantMapper.findByUserId(userId);
        if (all == null || all.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.Set<String> contestIds = new HashSet<>();
        java.util.Map<String, ContestParticipant> byContestId = new HashMap<>();
        for (ContestParticipant p : all) {
            String s = p.getStatus();
            boolean keep = switch (type == null ? "" : type) {
                case "registered" -> ContestParticipantStatus.REGISTERED.wireValue().equals(s);
                case "virtual" -> Boolean.TRUE.equals(p.getIsVirtual());
                default -> ContestParticipantStatus.FINISHED.wireValue().equals(s)
                        || ContestParticipantStatus.STARTED.wireValue().equals(s);
            };
            if (keep && p.getContestId() != null) {
                contestIds.add(p.getContestId());
                byContestId.put(p.getContestId(), p);
            }
        }
        if (contestIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Contest> contests = contestMapper.selectBatchIds(contestIds);
        if (contests == null || contests.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<ContestVO> result = new ArrayList<>(contests.size());
        for (Contest contest : contests) {
            if (contest == null) continue;
            long problemCount = contestProblemMapper.countByContestId(contest.getId());
            ContestParticipant participant = byContestId.get(contest.getId());
            ContestVO vo = toVOInternal(contest, userId, problemCount, participant);
            if (participant != null) {
                vo.setUserRanking(participant.getFinalRank());
                if (participant.getTotalScore() != null) {
                    vo.setUserScore(participant.getTotalScore().longValue());
                }
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public Optional<Contest> findById(String id) {
        return Optional.ofNullable(contestMapper.selectById(id));
    }

    @Override
    public Optional<Contest> findBySlug(String slug) {
        return Optional.ofNullable(contestMapper.findBySlug(slug));
    }

    @Override
    public ContestVO getContestById(String idOrSlug, String userId) {
        Contest contest = findById(idOrSlug).orElse(null);
        if (contest == null) {
            contest = findBySlug(idOrSlug)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        }
        return toVO(contest, userId);
    }

    @Override
    public ContestVO toVO(Contest contest, String userId) {
        if (contest == null) return null;
        long problemCount = contestProblemMapper.countByContestId(contest.getId());
        ContestParticipant participant = null;
        if (userId != null && !userId.isBlank()) {
            participant = participantMapper.findByContestIdAndUserId(contest.getId(), userId).orElse(null);
        }
        return toVOInternal(contest, userId, problemCount, participant);
    }

    private ContestVO toVOInternal(Contest contest, String userId, long problemCount, ContestParticipant participant) {
        if (contest == null) return null;
        ContestVO vo = new ContestVO();
        BeanUtils.copyProperties(contest, vo);
        vo.setId(contest.getId());
        vo.setDuration(contest.getDurationMinutes());
        vo.setCurrentParticipants(contest.getParticipantCount());
        vo.setIsPremium(false);
        vo.setIsPublished(contest.getIsVisible());
        try {
            vo.setCreatedById(contest.getCreatedBy() != null ? Long.parseLong(contest.getCreatedBy()) : null);
        } catch (NumberFormatException e) {
            vo.setCreatedById(null);
        }
        vo.setContestType(contest.getContestType());
        vo.setIsVisible(contest.getIsVisible());
        vo.setParticipantCount(contest.getParticipantCount());
        vo.setScoringRuleId(contest.getScoringRuleId());
        vo.setProblemCount((int) problemCount);
        if (participant != null) {
            vo.setIsParticipating(true);
            vo.setUserRanking(participant.getFinalRank());
            vo.setUserScore(participant.getTotalScore() != null ? participant.getTotalScore().longValue() : null);
        } else if (userId != null && !userId.isBlank()) {
            vo.setIsParticipating(false);
        }
        return vo;
    }

    private ContestListVO toListVOInternal(Contest contest, String userId, long problemCount, ContestParticipant participant) {
        if (contest == null) return null;
        Boolean isParticipating = null;
        Integer userRanking = null;
        if (participant != null) {
            isParticipating = true;
            userRanking = participant.getFinalRank();
        } else if (userId != null && !userId.isBlank()) {
            isParticipating = false;
        }
        return new ContestListVO(
                contest.getId(),
                contest.getSlug(),
                contest.getTitle(),
                contest.getStatus(),
                contest.getStartTime(),
                contest.getEndTime(),
                contest.getDurationMinutes(),
                contest.getContestType(),
                contest.getParticipantCount(),
                (int) problemCount,
                false,
                contest.getIsVisible(),
                contest.getIsVisible(),
                contest.getMaxParticipants(),
                contest.getRegisteredCount(),
                isParticipating,
                userRanking,
                contest.getIsRated(),
                contest.getScoringMode(),
                contest.getPenaltyPerWrong(),
                contest.getCoverImage()
        );
    }

    @Override
    public List<ContestProblemVO> getContestProblems(String contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || contest.getIsDeleted()) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return contestProblemMapper.findByContestId(contestId).stream()
                .map(cp -> {
                    ContestProblemVO vo = new ContestProblemVO();
                    BeanUtils.copyProperties(cp, vo);
                    ProblemFactsPort.ContestProblemFacts facts =
                            problemFactsPort.findContestProblemFacts(cp.getProblemId());
                    if (facts != null) {
                        vo.setTitle(facts.title());
                        vo.setSlug(facts.slug());
                        vo.setDifficulty(facts.difficulty());
                        vo.setAcceptanceRate(facts.acceptanceRate());
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<SubmissionVO> getContestProblemSubmissions(String contestId, Long problemId, String userId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || Boolean.TRUE.equals(contest.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        ContestProblem contestProblem = contestProblemMapper.findByContestIdAndProblemId(contestId, problemId);
        if (contestProblem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        return contestSubmissionMapper
                .findSubmissionsByContestProblemAndUser(contestId, contestProblem.getId(), userId)
                .stream()
                .map(s -> submissionProjection.toVO(s.getId()))
                .toList();
    }

    @Override
    public List<ContestAnnouncement> getContestAnnouncements(String contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || contest.getIsDeleted()) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return contestAnnouncementMapper.findByContestIdOrderByCreatedAtDesc(contestId);
    }

    @Override
    public Long resolveContestProblemId(String contestId, String problemPath) {
        if (problemPath == null || problemPath.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Problem id is required");
        }
        try {
            return Long.parseLong(problemPath);
        } catch (NumberFormatException ignored) {
            // fall through to contest_problem.id lookup
        }
        return contestProblemMapper.findByContestIdAndId(contestId, problemPath)
                .map(cp -> {
                    if (cp.getProblemId() == null) {
                        throw new BusinessException(ErrorCode.NOT_FOUND,
                                "Contest problem has no underlying problem id: " + problemPath);
                    }
                    return cp.getProblemId();
                })
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Contest problem not found: " + problemPath));
    }

    private record ContestEnrichment(Map<String, Long> problemCounts, Map<String, ContestParticipant> participants) {}

    private ContestEnrichment batchEnrich(List<Contest> contests, String userId) {
        List<String> contestIds = contests.stream().map(Contest::getId).toList();
        Map<String, Long> problemCounts = Map.of();
        Map<String, ContestParticipant> participants = Map.of();
        if (!contestIds.isEmpty()) {
            problemCounts = contestProblemMapper.countByContestIds(contestIds).stream()
                    .collect(Collectors.toMap(m -> (String) m.get("contestId"), m -> ((Number) m.get("cnt")).longValue(), (a, b) -> a));
            if (userId != null && !userId.isBlank()) {
                participants = participantMapper.findByContestIdsAndUserId(contestIds, userId).stream()
                        .collect(Collectors.toMap(ContestParticipant::getContestId, p -> p, (a, b) -> a));
            }
        }
        return new ContestEnrichment(problemCounts, participants);
    }

    @Override
    public PageResult<ContestListVO> findAllListVO(ContestQueryDTO query, String userId) {
        int currentPage = (query.getPage() != null && query.getPage() > 0) ? query.getPage() : 1;
        int currentPageSize = Math.min(query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 20, 100);
        LambdaQueryWrapper<Contest> qw = new LambdaQueryWrapper<>();
        qw.eq(Contest::getIsDeleted, false).eq(Contest::getIsVisible, true);
        applyCatalogFilters(qw, query);
        applySort(qw, query);
        Page<Contest> page = contestMapper.selectPage(new Page<>(currentPage, currentPageSize), qw);
        return toListPage(page, currentPage, currentPageSize, userId);
    }

    @Override
    public PageResult<ContestListVO> findAllAdmin(ContestQueryDTO query, String userId) {
        int currentPage = (query.getPage() != null && query.getPage() > 0) ? query.getPage() : 1;
        int currentPageSize = Math.min(query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 20, 100);
        LambdaQueryWrapper<Contest> qw = new LambdaQueryWrapper<>();
        qw.eq(Contest::getIsDeleted, false);
        applyCatalogFilters(qw, query);
        applySort(qw, query);
        Page<Contest> page = contestMapper.selectPage(new Page<>(currentPage, currentPageSize), qw);
        return toListPage(page, currentPage, currentPageSize, userId);
    }

    private void applyCatalogFilters(LambdaQueryWrapper<Contest> qw, ContestQueryDTO query) {
        if (query.getStatus() != null && !query.getStatus().isBlank()) qw.eq(Contest::getStatus, query.getStatus().toUpperCase());
        if (query.getContestType() != null && !query.getContestType().isBlank()) qw.eq(Contest::getContestType, query.getContestType().toUpperCase());
        if (query.getIsRated() != null) qw.eq(Contest::getIsRated, query.getIsRated());
        if (query.getSearch() != null && !query.getSearch().isBlank())
            qw.and(w -> w.like(Contest::getTitle, "%" + query.getSearch() + "%").or().like(Contest::getSlug, "%" + query.getSearch() + "%"));
    }

    private void applySort(LambdaQueryWrapper<Contest> qw, ContestQueryDTO query) {
        String sortField = query.getSort() != null ? query.getSort() : "startTime";
        String direction = query.getDirection() != null ? query.getDirection() : "asc";
        boolean isAsc = "asc".equalsIgnoreCase(direction);
        switch (sortField) {
            case "endTime" -> { if (isAsc) qw.orderByAsc(Contest::getEndTime); else qw.orderByDesc(Contest::getEndTime); }
            case "createdAt" -> { if (isAsc) qw.orderByAsc(Contest::getCreatedAt); else qw.orderByDesc(Contest::getCreatedAt); }
            case "title" -> { if (isAsc) qw.orderByAsc(Contest::getTitle); else qw.orderByDesc(Contest::getTitle); }
            default -> { if (isAsc) qw.orderByAsc(Contest::getStartTime); else qw.orderByDesc(Contest::getStartTime); }
        }
    }

    private PageResult<ContestListVO> toListPage(Page<Contest> page, int currentPage, int currentPageSize, String userId) {
        var enrichment = batchEnrich(page.getRecords(), userId);
        List<ContestListVO> items = page.getRecords().stream()
                .map(c -> toListVOInternal(c, userId, enrichment.problemCounts().getOrDefault(c.getId(), 0L), enrichment.participants().get(c.getId())))
                .collect(Collectors.toList());
        return PageResult.of(items, page.getTotal(), currentPage, currentPageSize);
    }

    @Override
    public PageResult<ContestListVO> findUpcoming(String userId) {
        return findUpcoming(userId, 1, 20);
    }

    @Override
    public PageResult<ContestListVO> findUpcoming(String userId, int page, int pageSize) {
        int p = Math.max(page, 1);
        int ps = Math.min(Math.max(pageSize, 1), 50);
        LambdaQueryWrapper<Contest> qw = new LambdaQueryWrapper<>();
        qw.eq(Contest::getIsDeleted, false)
          .eq(Contest::getIsVisible, true)
          .eq(Contest::getStatus, ContestStatus.UPCOMING.name())
          .orderByAsc(Contest::getStartTime);
        Page<Contest> result = contestMapper.selectPage(new Page<>(p, ps), qw);
        var enrichment = batchEnrich(result.getRecords(), userId);
        List<ContestListVO> items = result.getRecords().stream()
                .map(c -> toListVOInternal(c, userId, enrichment.problemCounts().getOrDefault(c.getId(), 0L), enrichment.participants().get(c.getId())))
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(), p, ps);
    }

    @Override
    public PageResult<ContestListVO> findRunning(String userId) {
        return findRunning(userId, 1, 20);
    }

    @Override
    public PageResult<ContestListVO> findRunning(String userId, int page, int pageSize) {
        int p = Math.max(page, 1);
        int ps = Math.min(Math.max(pageSize, 1), 50);
        LambdaQueryWrapper<Contest> qw = new LambdaQueryWrapper<>();
        qw.eq(Contest::getIsDeleted, false)
          .eq(Contest::getIsVisible, true)
          .eq(Contest::getStatus, ContestStatus.RUNNING.name())
          .orderByAsc(Contest::getStartTime);
        Page<Contest> result = contestMapper.selectPage(new Page<>(p, ps), qw);
        var enrichment = batchEnrich(result.getRecords(), userId);
        List<ContestListVO> items = result.getRecords().stream()
                .map(c -> toListVOInternal(c, userId, enrichment.problemCounts().getOrDefault(c.getId(), 0L), enrichment.participants().get(c.getId())))
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(), p, ps);
    }

    @Override
    public PageResult<ContestListVO> findPast(Integer page, Integer pageSize, String userId) {
        int p = Math.max(page != null ? page : 1, 1);
        int ps = Math.min(pageSize != null && pageSize > 0 ? pageSize : 10, 50);
        LambdaQueryWrapper<Contest> qw = new LambdaQueryWrapper<>();
        qw.eq(Contest::getIsDeleted, false).eq(Contest::getStatus, ContestStatus.FINISHED.name()).orderByDesc(Contest::getEndTime);
        Page<Contest> result = contestMapper.selectPage(new Page<>(p, ps), qw);
        var enrichment = batchEnrich(result.getRecords(), userId);
        List<ContestListVO> items = result.getRecords().stream()
                .map(c -> toListVOInternal(c, userId, enrichment.problemCounts().getOrDefault(c.getId(), 0L), enrichment.participants().get(c.getId())))
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(), p, ps);
    }

    @Override
    public GlobalContestStatsVO getStats() {
        long registered = participantMapper.countByStatus(ContestParticipantStatus.REGISTERED.name());
        long active = participantMapper.countByStatus(ContestParticipantStatus.STARTED.name());
        long completed = participantMapper.countByStatus(ContestParticipantStatus.FINISHED.name());
        long totalSubmissions = contestSubmissionMapper.countTotal();
        return new GlobalContestStatsVO(
                (int) registered,
                (int) active,
                (int) completed,
                totalSubmissions
        );
    }

    @Override
    @Cacheable(value = "contestRanking", key = "'getGlobalRanking:' + #limit")
    public List<ContestRankingVO> getGlobalRanking(Integer limit) {
        int max = (limit != null && limit > 0) ? Math.min(limit, 100) : 10;
        return globalRankingMapper.findTopRankings(max).stream().map(this::toRankingVO).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "contestRanking", key = "'getRanking:' + #contestId + ':' + #limit + ':' + (#cursor ?: '0')")
    public List<ContestRankingVO> getContestRanking(String contestId, Integer limit, String cursor) {
        int max = (limit != null && limit > 0) ? Math.min(limit, 100) : 10;
        if (contestId == null || contestId.isBlank()) {
            return globalRankingMapper.findTopRankings(max).stream()
                    .map(this::toRankingVO).collect(Collectors.toList());
        }
        Integer afterRank = null;
        String afterUserId = null;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split(":", 2);
            try {
                afterRank = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                afterRank = null;
            }
            if (parts.length > 1) afterUserId = parts[1];
        }
        return participantMapper
                .selectParticipantsKeyset(contestId, afterRank, afterUserId, max)
                .stream()
                .map(this::toContestRankingVO)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "contestRanking", key = "'globalPaginated:' + #page + ':' + #limit + ':' + (#country ?: '_all')")
    public PageResult<ContestRankingVO> getGlobalRankingsPaginated(Integer page, Integer limit, String country) {
        int currentPage = (page != null && page > 0) ? page : 1;
        int currentLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 50;
        boolean filtered = country != null && !country.isBlank();
        long total = filtered
                ? globalRankingMapper.findByCountry(country).size()
                : globalRankingMapper.countTotal();
        int offset = (currentPage - 1) * currentLimit;
        List<GlobalRanking> rankings = filtered
                ? globalRankingMapper.findByCountry(country)
                : globalRankingMapper.findRankingsPaginated(currentLimit, offset);
        if (filtered) {
            int from = Math.min(offset, rankings.size());
            int to = Math.min(offset + currentLimit, rankings.size());
            rankings = rankings.subList(from, to);
        }
        List<ContestRankingVO> paginatedList = rankings.stream()
                .map(this::toRankingVO)
                .collect(Collectors.toList());
        return PageResult.of(paginatedList, total, currentPage, currentLimit);
    }

    @Override
    public PageResult<ContestRankingVO> getAdminContestRanking(String contestId, Integer page, Integer limit) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || Boolean.TRUE.equals(contest.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return rankingService != null ? rankingService.getContestRanking(contestId, page, limit) : PageResult.of(List.of(), 0L, 1, 50);
    }

    private ContestRankingVO toRankingVO(GlobalRanking ranking) {
        if (ranking == null) return null;
        ContestRankingVO vo = new ContestRankingVO();
        vo.setRank(ranking.getGlobalRank());
        vo.setUserId(ranking.getUserId());
        vo.setUsername(ranking.getUsername());
        vo.setAvatar(ranking.getAvatar());
        vo.setName(ranking.getName());
        vo.setScore(ranking.getRating().longValue());
        vo.setProblemsSolved(ranking.getContestsAttended());
        vo.setCountry(ranking.getCountry());
        vo.setMaxRating(ranking.getMaxRating());
        vo.setRatingTitle(ranking.getRatingTitle());
        vo.setMaxRatingTitle(ranking.getMaxRatingTitle());
        vo.setContestsAttended(ranking.getContestsAttended());
        vo.setBadge(ranking.getBadge());
        return vo;
    }

    private ContestRankingVO toContestRankingVO(ContestParticipantMapper.ContestParticipantWithUser p) {
        if (p == null) return null;
        ContestRankingVO vo = new ContestRankingVO();
        vo.setRank(p.finalRank());
        vo.setUserId(p.userId());
        vo.setUsername(p.username());
        vo.setName(p.name());
        vo.setAvatar(p.avatar());
        return vo;
    }
}
