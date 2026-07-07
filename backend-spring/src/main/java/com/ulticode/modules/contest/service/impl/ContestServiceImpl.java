package com.ulticode.modules.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.contest.dto.AddContestProblemDTO;
import com.ulticode.modules.contest.dto.ContestProblemVO;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.ParticipationStatusDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.projection.ContestProjection;
import com.ulticode.modules.contest.service.ContestSchedulerService;
import com.ulticode.modules.contest.service.ContestScoringService;
import com.ulticode.modules.contest.service.ContestService;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Write-side facade for contest operations. Owns the contest state machine
 * (create / update / delete / start / end / addProblem / removeProblem /
 * submit) and the participation lifecycle methods that delegate to
 * {@link ContestSchedulerService}.
 *
 * <p>All read paths — catalog lists, detail, problems, announcements, stats,
 * rankings — live in {@link ContestProjection}. Write paths shape their return
 * values through {@link ContestProjection#toVO} and resolve entities for guards
 * via the internal {@link #getContestOrThrow(String)} helper (direct mapper
 * access, since every guard needs the soft-delete-aware variant).
 *
 * <p>Delegates scheduling / lifecycle to {@link ContestSchedulerService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestServiceImpl implements ContestService {

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestParticipantMapper participantMapper;
    private final ContestSchedulerService schedulerService;
    private final AchievementTriggerService achievementTriggerService;
    private final SubmissionService submissionService;
    private final ContestScoringService contestScoringService;
    private final ContestProjection contestProjection;
    private final Clock clock;

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
        return contestProjection.toVO(contest, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    @Audited(action = AuditActionUtil.UPDATE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "id")
    public ContestVO updateContest(String id, UpdateContestDTO dto) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = getContestOrThrow(id);
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
        return contestProjection.toVO(contest, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    @Audited(action = AuditActionUtil.DELETE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "id")
    public void deleteContest(String id) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = getContestOrThrow(id);
        // LambdaUpdateWrapper is required because Contest.isDeleted carries @TableLogic;
        // mapper.updateById(entity) silently skips fields annotated with @TableLogic.
        String deletedBy = SecurityUtil.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now(clock);
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

    @Override
    @Transactional
    public SubmissionVO submitContestProblem(String contestId, Long problemId, String userId, CreateSubmissionDTO createDTO) {
        Contest contest = getContestOrThrow(contestId);
        if (!ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_NOT_STARTED, "Contest is not running");
        }
        // P1-2 fix: enforce contest end time. Admin must call endContest at end_time,
        // but if they forget, submissions past end_time would otherwise slip through.
        if (contest.getEndTime() != null && LocalDateTime.now(clock).isAfter(contest.getEndTime())) {
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
            LocalDateTime virtualEnd =
                    participant.getStartedAt().plusMinutes(contest.getDurationMinutes());
            if (LocalDateTime.now(clock).isAfter(virtualEnd)) {
                throw new BusinessException(ErrorCode.CONTEST_ENDED,
                        "Virtual contest duration has passed");
            }
        }

        createDTO.setProblemId(problemId);
        return submissionService.submit(userId, createDTO);
    }

    // =========================================================================
    // Participation (delegated to ContestSchedulerService)
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
    public java.util.List<ContestVO> getUserContests(String userId, String type) {
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
    // Admin lifecycle (state transitions + problem management)
    // =========================================================================

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    @Audited(action = AuditActionUtil.UPDATE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "id")
    public ContestVO startContest(String id, String userId) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = getContestOrThrow(id);
        String status = contest.getStatus();
        if (!ContestStatus.DRAFT.name().equals(status) && !ContestStatus.UPCOMING.name().equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Contest can only be started from DRAFT or UPCOMING status");
        }
        contest.setStatus(ContestStatus.RUNNING.name());
        contest.setActualStartTime(LocalDateTime.now(clock));
        contestMapper.updateById(contest);
        AuditContext.setOldValues(Map.ofEntries(Map.entry("status", status)));
        AuditContext.setNewValues(Map.ofEntries(Map.entry("status", ContestStatus.RUNNING.name())));
        log.info("Contest started: {} by user {}", id, userId);
        return contestProjection.toVO(contest, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"contest", "contestRanking"}, allEntries = true)
    @Audited(action = AuditActionUtil.UPDATE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "id")
    public ContestVO endContest(String id, String userId) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        Contest contest = getContestOrThrow(id);
        if (!ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Contest can only be ended from RUNNING status");
        }
        contest.setStatus(ContestStatus.FINISHED.name());
        contest.setActualEndTime(LocalDateTime.now(clock));
        contestMapper.updateById(contest);
        AuditContext.setOldValues(Map.ofEntries(Map.entry("status", ContestStatus.RUNNING.name())));
        AuditContext.setNewValues(Map.ofEntries(Map.entry("status", ContestStatus.FINISHED.name())));
        log.info("Contest ended: {} by user {}", id, userId);
        return contestProjection.toVO(contest, userId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "contest", allEntries = true)
    @Audited(action = AuditActionUtil.UPDATE_CONTEST, entityType = AuditActionUtil.ENTITY_CONTEST, entityIdFrom = "contestId", captureOldState = false)
    public ContestProblemVO addProblem(String contestId, AddContestProblemDTO dto) {
        if (!SecurityUtil.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(ErrorCode.FORBIDDEN);
        getContestOrThrow(contestId);
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
        getContestOrThrow(contestId);
        ContestProblem cp = contestProblemMapper.findByContestIdAndProblemId(contestId, problemId);
        if (cp == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Problem not found in this contest");
        }
        contestProblemMapper.deleteById(cp.getId());
        AuditContext.setNewValues(Map.ofEntries(Map.entry("removedProblemId", problemId)));
        log.info("Problem {} removed from contest {}", problemId, contestId);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Load a non-deleted contest by id, throwing {@link ErrorCode#CONTEST_NOT_FOUND}
     * when missing or soft-deleted. Used by every write path as the pre-mutation
     * guard (previously inlined as {@code findById(id).filter(!isDeleted).orElseThrow}).
     */
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

    private String generateSlug(String title) {
        if (title == null || title.isBlank()) return "contest-" + UUID.randomUUID().toString().substring(0, 8);
        String slug = title.toLowerCase().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        return slug.length() < 3 ? slug + "-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }
}
