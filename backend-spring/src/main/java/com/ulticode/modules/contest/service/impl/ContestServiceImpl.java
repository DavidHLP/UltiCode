package com.ulticode.modules.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditContext;
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
import com.ulticode.modules.contest.service.ContestScoringService;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ProblemMapper problemMapper;
    private final SubmissionService submissionService;
    private final SubmissionProjection submissionProjection;
    // P2-5 fix: cascade-delete helper invoked from deleteContest.
    private final ContestScoringService contestScoringService;

    // =========================================================================
    // CRUD Operations (Admin)
    // =========================================================================

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    @Audited(action = AuditActionUtil.CREATE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, captureOldState = false)
    public ContestVO createContest(CreateContestDTO dto, String userId) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = new Contest();
        contest.setTitle(dto.getTitle());
        contest.setDescription(dto.getDescription());
        contest.setStartTime(dto.getStartTime());
        contest.setDurationMinutes(dto.getDuration());
        contest.setEndTime(dto.getStartTime().plusMinutes(dto.getDuration()));
        contest.setMaxParticipants(dto.getMaxParticipants());
        contest.setIsVisible(dto.getIsPublished() != null ? dto.getIsPublished() : false);
        contest.setCreatedBy(userId);
        // P0-3 (simplified): honor the caller's isPublished flag instead of always
        // DRAFT. Admin path (AdminContestServiceImpl.createContest:127) already
        // does this; this makes the user-facing path consistent. DRAFT remains
        // the default for backward compatibility when isPublished is null/false.
        contest.setStatus(Boolean.TRUE.equals(dto.getIsPublished())
                ? ContestStatus.UPCOMING.name()
                : ContestStatus.DRAFT.name());
        contest.setRegisteredCount(0);
        contest.setParticipantCount(0);
        contest.setSubmissionCount(0);
        contest.setIsDeleted(false);
        contest.setSlug(generateSlug(dto.getTitle()));
        try {
            contestMapper.insert(contest);
        } catch (DataIntegrityViolationException e) {
            // P0-5 / H2: uk_contest_slug rejected. Catch the parent class so we
            // surface 409 regardless of which Spring exception subtype the
            // underlying driver throws.
            throw new BusinessException(ErrorCode.CONTEST_SLUG_EXISTS,
                    "Contest slug already exists: " + contest.getSlug());
        }
        AuditContext.setNewValues(Map.ofEntries(Map.entry("title", contest.getTitle()), Map.entry("slug", contest.getSlug()), Map.entry("status", contest.getStatus())));
        AuditContext.setUserId(userId);
        log.info("Contest created: {} by user {}", contest.getId(), userId);
        return toVO(contest, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    @Audited(action = AuditActionUtil.UPDATE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "id")
    public ContestVO updateContest(String id, UpdateContestDTO dto) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = findById(id)
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        if (!ContestStatus.UPCOMING.name().equalsIgnoreCase(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ONLY_UPDATE_UPCOMING,
                    "Contest can only be updated when in UPCOMING status, current: " + contest.getStatus());
        }
        Map<String, Object> oldValues = new java.util.HashMap<>();
        oldValues.put("title", contest.getTitle());
        oldValues.put("status", contest.getStatus());
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
        try {
            contestMapper.updateById(contest);
        } catch (DataIntegrityViolationException e) {
            // P0-5 / H2: title→slug change collided with existing row's slug.
            // Parent-class catch for cross-driver compatibility.
            throw new BusinessException(ErrorCode.CONTEST_SLUG_EXISTS,
                    "Contest slug already exists: " + contest.getSlug());
        }
        AuditContext.setOldValues(oldValues);
        AuditContext.setNewValues(Map.ofEntries(Map.entry("title", contest.getTitle()), Map.entry("status", contest.getStatus())));
        log.info("Contest updated: {}", id);
        return toVO(contest, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    @Audited(action = AuditActionUtil.DELETE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "id")
    public void deleteContest(String id) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = findById(id)
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        // LambdaUpdateWrapper is required because Contest.isDeleted carries @TableLogic;
        // mapper.updateById(entity) silently skips fields annotated with @TableLogic.
        String deletedBy = SecurityUtil.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        contestMapper.update(null, new LambdaUpdateWrapper<Contest>()
                .eq(Contest::getId, id)
                .set(Contest::getIsDeleted, true)
                .set(Contest::getDeletedAt, now)
                .set(Contest::getDeletedBy, deletedBy));
        // P2-5 fix: cascade physical delete of relational rows (participants,
        // submissions, problem results, first-solve records, contest_problems).
        // The soft-delete of the parent is already done above; this is a clean-up
        // step that prevents stale rows from polluting stats / rankings.
        try {
            contestScoringService.deleteContestCascade(id);
        } catch (Exception e) {
            log.warn("P2-5 cascade cleanup failed for contest {}: {}", id, e.getMessage());
        }
        AuditContext.setOldValues(Map.ofEntries(Map.entry("title", contest.getTitle()), Map.entry("status", contest.getStatus())));
        AuditContext.setNewValues(null);
        log.info("Contest deleted: {} by {}", id, deletedBy);
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
        Contest contest = findById(id).orElse(null);
        if (contest == null) {
            contest = findBySlug(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        }
        return toVO(contest, userId);
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
                    Problem problem = problemMapper.selectById(cp.getProblemId());
                    if (problem != null) {
                        vo.setTitle(problem.getTitle());
                        vo.setSlug(problem.getSlug());
                        vo.setDifficulty(problem.getDifficulty());
                        vo.setAcceptanceRate(problem.getAcceptanceRate());
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Long resolveContestProblemId(String contestId, String problemPath) {
        if (problemPath == null || problemPath.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Problem id is required");
        }
        // 1) Try parsing as numeric (legacy & most common case).
        try {
            return Long.parseLong(problemPath);
        } catch (NumberFormatException ignored) {
            // fall through to contest_problem.id lookup
        }
        // 2) Look up the composite id in contest_problems.
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

    public List<SubmissionVO> getContestProblemSubmissions(String contestId, Long problemId, String userId) {
        ContestProblem contestProblem = getContestProblemOrThrow(contestId, problemId);

        return contestSubmissionMapper
                .findSubmissionsByContestProblemAndUser(contestId, contestProblem.getId(), userId)
                .stream()
                .map(submissionProjection::toVO)
                .toList();
    }

    @Override
    @Transactional
    public SubmissionVO submitContestProblem(String contestId, Long problemId, String userId, CreateSubmissionDTO createDTO) {
        Contest contest = getContestOrThrow(contestId);
        if (!ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_STARTED, "Contest is not running");
        }
        // P1-2 fix: enforce contest end time. Admin must call endContest at end_time,
        // but if they forget, submissions past end_time would otherwise slip through.
        if (contest.getEndTime() != null && java.time.LocalDateTime.now().isAfter(contest.getEndTime())) {
            throw new BusinessException(ErrorCode.CONTEST_ENDED, "Contest end time has passed");
        }

        getContestProblemOrThrow(contestId, problemId);

        ContestParticipant participant = participantMapper.findByContestIdAndUserId(contestId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_REGISTERED));
        if (!ContestParticipantStatus.STARTED.name().equals(participant.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_STARTED, "Contest participation has not started");
        }

        // R6.2 / F-07: virtual sessions get a hard deadline based on
        // started_at + duration_minutes. Without this gate, a user could
        // submit well after their virtual replay should have ended; the
        // scheduler's auto-finish only kicks in on the next 10s tick, leaving
        // a window where late submissions sneak through.
        if (Boolean.TRUE.equals(participant.getIsVirtual())
                && participant.getStartedAt() != null
                && contest.getDurationMinutes() != null) {
            java.time.LocalDateTime virtualEnd =
                    participant.getStartedAt().plusMinutes(contest.getDurationMinutes());
            if (java.time.LocalDateTime.now().isAfter(virtualEnd)) {
                throw new BusinessException(ErrorCode.CONTEST_ENDED,
                        "Virtual contest duration has passed");
            }
        }

        createDTO.setProblemId(problemId);
        return submissionService.submit(userId, createDTO);
    }

    @Override
    public List<ContestAnnouncement> getContestAnnouncements(String contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || contest.getIsDeleted()) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return contestAnnouncementMapper.findByContestIdOrderByCreatedAtDesc(contestId);
    }

    private Contest getContestOrThrow(String contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || Boolean.TRUE.equals(contest.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
        return contest;
    }

    private ContestProblem getContestProblemOrThrow(String contestId, Long problemId) {
        getContestOrThrow(contestId);
        ContestProblem contestProblem = contestProblemMapper.findByContestIdAndProblemId(contestId, problemId);
        if (contestProblem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }
        return contestProblem;
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
    @Cacheable(value = "contestRanking", key = "'getRanking:' + #contestId + ':' + #limit + ':' + (#cursor ?: '0')")
    public List<ContestRankingVO> getContestRanking(String contestId, Integer limit, String cursor) {
        int max = (limit != null && limit > 0) ? Math.min(limit, 100) : 10;
        if (contestId == null || contestId.isBlank()) {
            return globalRankingMapper.findTopRankings(max).stream()
                    .map(this::toRankingVO).collect(Collectors.toList());
        }
        // R9.1: keyset cursor — null/blank means first page.
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
        // Apply in-memory pagination for the filtered branch since findByCountry
        // returns the full per-country list; for the global branch the SQL already
        // paginated.
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
    @Audited(action = AuditActionUtil.UPDATE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "id")
    public ContestVO startContest(String id, String userId) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = findById(id)
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        String status = contest.getStatus();
        if (!ContestStatus.DRAFT.name().equals(status) && !ContestStatus.UPCOMING.name().equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Contest can only be started from DRAFT or UPCOMING status");
        }
        contest.setStatus(ContestStatus.RUNNING.name());
        contest.setActualStartTime(LocalDateTime.now());
        contestMapper.updateById(contest);
        AuditContext.setOldValues(Map.ofEntries(Map.entry("status", status)));
        AuditContext.setNewValues(Map.ofEntries(Map.entry("status", ContestStatus.RUNNING.name())));
        log.info("Contest started: {} by user {}", id, userId);
        return toVO(contest, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    @Audited(action = AuditActionUtil.UPDATE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "id")
    public ContestVO endContest(String id, String userId) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = findById(id)
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        if (!ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Contest can only be ended from RUNNING status");
        }
        contest.setStatus(ContestStatus.FINISHED.name());
        contest.setActualEndTime(LocalDateTime.now());
        contestMapper.updateById(contest);
        AuditContext.setOldValues(Map.ofEntries(Map.entry("status", ContestStatus.RUNNING.name())));
        AuditContext.setNewValues(Map.ofEntries(Map.entry("status", ContestStatus.FINISHED.name())));
        log.info("Contest ended: {} by user {}", id, userId);
        return toVO(contest, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "contest", allEntries = true)
    @Audited(action = AuditActionUtil.UPDATE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "contestId", captureOldState = false)
    public ContestProblemVO addProblem(String contestId, AddContestProblemDTO dto) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        findById(contestId)
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
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
        AuditContext.setNewValues(Map.ofEntries(Map.entry("addedProblemId", dto.getProblemId()), Map.entry("problemIndex", cp.getProblemIndex())));
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
    @Audited(action = AuditActionUtil.UPDATE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "contestId", captureOldState = false)
    public void removeProblem(String contestId, Long problemId) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        findById(contestId)
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_FOUND));
        ContestProblem cp = contestProblemMapper.findByContestIdAndProblemId(contestId, problemId);
        if (cp == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Problem not found in this contest");
        }
        contestProblemMapper.deleteById(cp.getId());
        AuditContext.setNewValues(Map.ofEntries(Map.entry("removedProblemId", problemId)));
        log.info("Problem {} removed from contest {}", problemId, contestId);
    }

    @Override
    public PageResult<ContestRankingVO> getAdminContestRanking(String contestId, Integer page, Integer limit) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || Boolean.TRUE.equals(contest.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);
        }
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

    /**
     * R9.1 / F-24: per-contest ranking VO converter for the keyset
     * path. {@link ContestParticipantWithUser} is the mapper result
     * record; we project it into the same {@link ContestRankingVO}
     * shape as the global path so callers can use the same response
     * type regardless of whether the cache key is per-contest.
     */
    private ContestRankingVO toContestRankingVO(
            ContestParticipantMapper.ContestParticipantWithUser p) {
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
