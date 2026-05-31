package com.ulticode.modules.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.entity.GlobalRanking;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.service.ContestSchedulerService;
import com.ulticode.modules.contest.service.ContestService;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Facade for contest operations.
 * Delegates scheduling/lifecycle to ContestSchedulerService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestServiceImpl implements ContestService {

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestParticipantMapper participantMapper;
    private final GlobalRankingMapper globalRankingMapper;
    private final ContestSchedulerService schedulerService;
    private final RankingService rankingService;
    private final AchievementTriggerService achievementTriggerService;
    private final ContestAnnouncementMapper contestAnnouncementMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;

    // =========================================================================
    // CRUD Operations (Admin)
    // =========================================================================

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    public ContestVO createContest(CreateContestDTO dto, String userId) {
        if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = new Contest();
        contest.setTitle(dto.getTitle());
        contest.setDescription(dto.getDescription());
        contest.setStartTime(dto.getStartTime());
        contest.setDurationMinutes(dto.getDuration());
        contest.setEndTime(dto.getStartTime().plusMinutes(dto.getDuration()));
        contest.setMaxParticipants(dto.getMaxParticipants());
        contest.setIsVisible(dto.getIsPublished() != null ? dto.getIsPublished() : false);
        contest.setCreatedBy(userId);
        contest.setStatus(ContestStatus.DRAFT.name());
        contest.setRegisteredCount(0);
        contest.setParticipantCount(0);
        contest.setSubmissionCount(0);
        contest.setIsDeleted(false);
        contest.setSlug(generateSlug(dto.getTitle()));
        contestMapper.insert(contest);
        log.info("Contest created: {} by user {}", contest.getId(), userId);
        return toVO(contest, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    public ContestVO updateContest(String id, UpdateContestDTO dto) {
        if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        if (dto.getTitle() != null) { contest.setTitle(dto.getTitle()); contest.setSlug(generateSlug(dto.getTitle())); }
        if (dto.getDescription() != null) contest.setDescription(dto.getDescription());
        if (dto.getStartTime() != null) {
            contest.setStartTime(dto.getStartTime());
            contest.setEndTime(dto.getDuration() != null ? dto.getStartTime().plusMinutes(dto.getDuration())
                    : contest.getStartTime().plusMinutes(contest.getDurationMinutes()));
        }
        if (dto.getDuration() != null) {
            contest.setDurationMinutes(dto.getDuration());
            if (contest.getStartTime() != null) contest.setEndTime(contest.getStartTime().plusMinutes(dto.getDuration()));
        }
        if (dto.getMaxParticipants() != null) contest.setMaxParticipants(dto.getMaxParticipants());
        if (dto.getIsPublished() != null) contest.setIsVisible(dto.getIsPublished());
        contestMapper.updateById(contest);
        log.info("Contest updated: {}", id);
        return toVO(contest, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    public void deleteContest(String id) {
        if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        contest.setIsDeleted(true);
        contest.setDeletedAt(LocalDateTime.now());
        contest.setDeletedBy(SecurityUtil.getCurrentUserId());
        contestMapper.updateById(contest);
        log.info("Contest deleted: {}", id);
    }

    // =========================================================================
    // Query Operations
    // =========================================================================

    @Override
    public Optional<Contest> findById(String id) {
        return Optional.ofNullable(contestMapper.selectById(id));
    }

    @Override
    public Optional<Contest> findBySlug(String slug) {
        return Optional.ofNullable(contestMapper.findBySlug(slug));
    }

    @Override
    public ContestVO getContestById(String id, String userId) {
        return toVO(findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND)), userId);
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
                    return vo;
                })
                .collect(Collectors.toList());
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
    public PageResult<ContestListVO> findUpcoming(String userId) {
        return findUpcoming(userId, 1, 20);
    }

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
    @Cacheable(value = "contestRanking", key = "'globalPaginated:' + #page + ':' + #limit")
    public PageResult<ContestRankingVO> getGlobalRankingsPaginated(Integer page, Integer limit) {
        int currentPage = (page != null && page > 0) ? page : 1;
        int currentLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 50;

        long total = globalRankingMapper.countTotal();
        int offset = (currentPage - 1) * currentLimit;
        List<GlobalRanking> rankings = globalRankingMapper.findRankingsPaginated(currentLimit, offset);
        List<ContestRankingVO> paginatedList = rankings.stream()
                .map(this::toRankingVO)
                .collect(Collectors.toList());

        return PageResult.of(paginatedList, total, currentPage, currentLimit);
    }

    // =========================================================================
    // Scheduling (delegated to ContestSchedulerService)
    // =========================================================================

    @Override
    public void registerForContest(String contestId, String userId) {
        schedulerService.registerForContest(contestId, userId);

        // Trigger contest participation achievement
        try {
            long participationCount = participantMapper.countByUserId(userId);
            achievementTriggerService.onContestJoined(userId, (int) participationCount);
        } catch (Exception e) {
            log.warn("Failed to trigger contest achievement for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public void unregisterFromContest(String contestId, String userId) {
        schedulerService.unregisterFromContest(contestId, userId);
    }

    @Override
    public ParticipationStatusDTO getParticipationStatus(String contestId, String userId) {
        return schedulerService.getParticipationStatus(contestId, userId);
    }

    @Override
    public List<ContestVO> getUserContests(String userId, String type) {
        return schedulerService.getUserContests(userId, type);
    }

    @Override
    public ParticipationStatusDTO startVirtualContest(String contestId, String userId) {
        return schedulerService.startVirtualContest(contestId, userId);
    }

    @Override
    public ParticipationStatusDTO getVirtualSession(String contestId, String userId) {
        return schedulerService.getVirtualSession(contestId, userId);
    }

    @Override
    public void finishVirtualContest(String contestId, String sessionId, String userId) {
        schedulerService.finishVirtualContest(contestId, sessionId, userId);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    @Override
    public ContestListVO toListVO(Contest contest, String userId) {
        if (contest == null) return null;
        long problemCount = contestProblemMapper.countByContestId(contest.getId());
        ContestParticipant participant = null;
        if (userId != null && !userId.isBlank()) {
            participant = participantMapper.findByContestIdAndUserId(contest.getId(), userId).orElse(null);
        }
        return toListVOInternal(contest, userId, problemCount, participant);
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
        if (query.getStatus() != null && !query.getStatus().isBlank()) qw.eq(Contest::getStatus, query.getStatus().toUpperCase());
        if (query.getContestType() != null && !query.getContestType().isBlank()) qw.eq(Contest::getContestType, query.getContestType().toUpperCase());
        if (query.getIsRated() != null) qw.eq(Contest::getIsRated, query.getIsRated());
        if (query.getSearch() != null && !query.getSearch().isBlank())
            qw.and(w -> w.like(Contest::getTitle, "%" + query.getSearch() + "%").or().like(Contest::getSlug, "%" + query.getSearch() + "%"));
        String sortField = query.getSort() != null ? query.getSort() : "startTime";
        String direction = query.getDirection() != null ? query.getDirection() : "asc";
        boolean isAsc = "asc".equalsIgnoreCase(direction);
        switch (sortField) {
            case "endTime" -> { if (isAsc) qw.orderByAsc(Contest::getEndTime); else qw.orderByDesc(Contest::getEndTime); }
            case "createdAt" -> { if (isAsc) qw.orderByAsc(Contest::getCreatedAt); else qw.orderByDesc(Contest::getCreatedAt); }
            case "title" -> { if (isAsc) qw.orderByAsc(Contest::getTitle); else qw.orderByDesc(Contest::getTitle); }
            default -> { if (isAsc) qw.orderByAsc(Contest::getStartTime); else qw.orderByDesc(Contest::getStartTime); }
        }
        Page<Contest> page = contestMapper.selectPage(new Page<>(currentPage, currentPageSize), qw);
        var enrichment = batchEnrich(page.getRecords(), userId);
        List<ContestListVO> items = page.getRecords().stream()
                .map(c -> toListVOInternal(c, userId, enrichment.problemCounts().getOrDefault(c.getId(), 0L), enrichment.participants().get(c.getId())))
                .collect(Collectors.toList());
        return PageResult.of(items, page.getTotal(), currentPage, currentPageSize);
    }

    // =========================================================================
    // Admin Operations
    // =========================================================================

    @Override
    public PageResult<ContestListVO> findAllAdmin(ContestQueryDTO query, String userId) {
        int currentPage = (query.getPage() != null && query.getPage() > 0) ? query.getPage() : 1;
        int currentPageSize = Math.min(query.getPageSize() != null && query.getPageSize() > 0 ? query.getPageSize() : 20, 100);
        LambdaQueryWrapper<Contest> qw = new LambdaQueryWrapper<>();
        qw.eq(Contest::getIsDeleted, false);
        // Admin sees all contests including drafts and invisible ones
        if (query.getStatus() != null && !query.getStatus().isBlank()) qw.eq(Contest::getStatus, query.getStatus().toUpperCase());
        if (query.getContestType() != null && !query.getContestType().isBlank()) qw.eq(Contest::getContestType, query.getContestType().toUpperCase());
        if (query.getIsRated() != null) qw.eq(Contest::getIsRated, query.getIsRated());
        if (query.getSearch() != null && !query.getSearch().isBlank())
            qw.and(w -> w.like(Contest::getTitle, "%" + query.getSearch() + "%").or().like(Contest::getSlug, "%" + query.getSearch() + "%"));
        String sortField = query.getSort() != null ? query.getSort() : "startTime";
        String direction = query.getDirection() != null ? query.getDirection() : "asc";
        boolean isAsc = "asc".equalsIgnoreCase(direction);
        switch (sortField) {
            case "endTime" -> { if (isAsc) qw.orderByAsc(Contest::getEndTime); else qw.orderByDesc(Contest::getEndTime); }
            case "createdAt" -> { if (isAsc) qw.orderByAsc(Contest::getCreatedAt); else qw.orderByDesc(Contest::getCreatedAt); }
            case "title" -> { if (isAsc) qw.orderByAsc(Contest::getTitle); else qw.orderByDesc(Contest::getTitle); }
            default -> { if (isAsc) qw.orderByAsc(Contest::getStartTime); else qw.orderByDesc(Contest::getStartTime); }
        }
        Page<Contest> page = contestMapper.selectPage(new Page<>(currentPage, currentPageSize), qw);
        var enrichment = batchEnrich(page.getRecords(), userId);
        List<ContestListVO> items = page.getRecords().stream()
                .map(c -> toListVOInternal(c, userId, enrichment.problemCounts().getOrDefault(c.getId(), 0L), enrichment.participants().get(c.getId())))
                .collect(Collectors.toList());
        return PageResult.of(items, page.getTotal(), currentPage, currentPageSize);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    public ContestVO startContest(String id, String userId) {
        if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        String status = contest.getStatus();
        if (!ContestStatus.DRAFT.name().equals(status) && !ContestStatus.UPCOMING.name().equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Contest can only be started from DRAFT or UPCOMING status");
        }
        contest.setStatus(ContestStatus.RUNNING.name());
        contest.setActualStartTime(LocalDateTime.now());
        contestMapper.updateById(contest);
        log.info("Contest started: {} by user {}", id, userId);
        return toVO(contest, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    public ContestVO endContest(String id, String userId) {
        if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        if (!ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Contest can only be ended from RUNNING status");
        }
        contest.setStatus(ContestStatus.FINISHED.name());
        contest.setActualEndTime(LocalDateTime.now());
        contestMapper.updateById(contest);
        log.info("Contest ended: {} by user {}", id, userId);
        return toVO(contest, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "contest", allEntries = true)
    public ContestProblemVO addProblem(String contestId, AddContestProblemDTO dto) {
        if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        findById(contestId).orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        ContestProblem existing = contestProblemMapper.findByContestIdAndProblemId(contestId, dto.getProblemId());
        if (existing != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Problem already exists in this contest");
        }
        long count = contestProblemMapper.countByContestId(contestId);
        ContestProblem cp = new ContestProblem();
        cp.setContestId(contestId);
        cp.setProblemId(dto.getProblemId());
        cp.setProblemIndex(String.valueOf((char) ('A' + count)));
        cp.setScore(dto.getScore() != null ? dto.getScore() : 100);
        cp.setSolvedCount(0);
        cp.setSubmissionCount(0);
        contestProblemMapper.insert(cp);
        log.info("Problem {} added to contest {}", dto.getProblemId(), contestId);
        ContestProblemVO vo = new ContestProblemVO();
        vo.setId(cp.getId());
        vo.setContestId(cp.getContestId());
        vo.setProblemId(cp.getProblemId());
        vo.setProblemIndex(cp.getProblemIndex());
        vo.setScore(cp.getScore());
        return vo;
    }

    @Override
    @Transactional
    @CacheEvict(value = "contest", allEntries = true)
    public void removeProblem(String contestId, Long problemId) {
        if (!SecurityUtil.hasRole("ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        ContestProblem cp = contestProblemMapper.findByContestIdAndProblemId(contestId, problemId);
        if (cp == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Problem not found in this contest");
        }
        contestProblemMapper.deleteById(cp.getId());
        log.info("Problem {} removed from contest {}", problemId, contestId);
    }

    @Override
    public PageResult<ContestRankingVO> getAdminContestRanking(String contestId, Integer page, Integer limit) {
        return rankingService != null ? rankingService.getContestRanking(contestId, page, limit) : PageResult.of(List.of(), 0L, 1, 50);
    }

    private String generateSlug(String title) {
        if (title == null || title.isBlank()) return "contest-" + UUID.randomUUID().toString().substring(0, 8);
        String slug = title.toLowerCase().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        return slug.length() < 3 ? slug + "-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }

    private ContestRankingVO toRankingVO(GlobalRanking ranking) {
        if (ranking == null) return null;
        ContestRankingVO vo = new ContestRankingVO();
        vo.setRank(ranking.getGlobalRank());
        vo.setUserId(ranking.getUserId());
        vo.setUsername(ranking.getUsername());
        vo.setAvatar(ranking.getAvatar());
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
}
