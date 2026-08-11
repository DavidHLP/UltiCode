package com.ulticode.modules.contest.service.impl;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.app.error.ContestErrorCode;

import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.contest.clock.ContestClock;
import com.ulticode.modules.contest.dto.AddContestProblemDTO;
import com.ulticode.modules.contest.dto.ContestProblemVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.projection.ContestProjection;
import com.ulticode.modules.contest.service.ContestLifecycleService;
import com.ulticode.modules.contest.service.ContestService;
import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.service.SubmissionWritePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Write-side facade for contest operations. After the admin-contest mutation
 * seam landed, this only owns the contest state-machine pieces that the
 * user-facing path still drives: {@code submitContestProblem} (the
 * participant submit guard matrix) and {@code addProblem} /
 * {@code removeProblem} (contest-problem link management).
 *
 * <p>Admin lifecycle (create / update / soft-delete / start / end /
 * announcement CRUD / admin add-problem) moved to
 * {@link com.ulticode.modules.admin.service.AdminContestMutationService} —
 * the single seam where every admin contest write policy lives. Read paths
 * live in {@link ContestProjection}; participation in
 * {@link com.ulticode.modules.contest.service.ContestParticipationService}.
 *
 * <p>The internal {@link #getContestOrThrow} helper is the soft-delete-aware
 * guard every write path uses; it loads directly from the mapper so callers
 * never see soft-deleted contests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestServiceImpl implements ContestService {

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestParticipantMapper participantMapper;
    private final SubmissionWritePort submissionWritePort;
    private final ContestLifecycleService contestLifecycleService;
    private final ContestProjection contestProjection;
    private final ContestClock contestClock;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;
    private final CurrentUserProvider currentUserProvider;

    // =========================================================================
    // Participant submission (state-machine guards)
    // =========================================================================

    @Override
    @Transactional
    public SubmissionVO submitContestProblem(String contestId, Long problemId, String userId,
                                             CreateSubmissionDTO createDTO) {
        if (createDTO == null) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Submission payload is required");
        }

        Contest contest = getContestOrThrow(contestId);
        getContestProblemOrThrow(contestId, problemId);

        String virtualSessionId = createDTO.getVirtualSessionId();
        Optional<ContestParticipant> participantOpt = virtualSessionId == null || virtualSessionId.isBlank()
                ? participantMapper.findRealForSubmissionAdmission(contestId, userId)
                : participantMapper.findVirtualForSubmissionAdmission(contestId, userId, virtualSessionId);
        ContestParticipant participant = participantOpt
                .orElseThrow(() -> new BusinessException(ContestErrorCode.CONTEST_NOT_REGISTERED));
        boolean virtual = Boolean.TRUE.equals(participant.getIsVirtual());
        if (virtual) {
            // Virtual replays start only after the parent contest is published
            // as FINISHED; their own session clock remains authoritative.
            if (!ContestStatus.FINISHED.name().equals(contest.getStatus())) {
                throw new BusinessException(ContestErrorCode.CONTEST_ENDED,
                        "Virtual contest is not available");
            }
        } else if (!ContestStatus.RUNNING.name().equals(contest.getStatus())) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_STARTED, "Contest is not running");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (!virtual && contest.getEndTime() != null && now.isAfter(contest.getEndTime())) {
            throw new BusinessException(ContestErrorCode.CONTEST_ENDED, "Contest end time has passed");
        }

        if (!ContestParticipantStatus.STARTED.name().equals(participant.getStatus())) {
            throw new BusinessException(virtual
                    ? ContestErrorCode.CONTEST_ENDED
                    : ContestErrorCode.CONTEST_NOT_STARTED,
                    "Contest participation has not started");
        }

        // Virtual sessions use their own started_at + duration window. The
        // adapter repeats this check under row locks before inserting the
        // contest mapping, closing the check/insert race.
        if (virtual) {
            LocalDateTime virtualEnd = contestClock.effectiveEndTime(participant, contest).orElse(null);
            if (virtualEnd == null || now.isAfter(virtualEnd)) {
                throw new BusinessException(ContestErrorCode.CONTEST_ENDED,
                        "Virtual contest duration has passed");
            }
        }

        // The path contest is the sole source of ownership. Do not let a
        // client-supplied context redirect the submission to another contest.
        createDTO.setProblemId(problemId);
        createDTO.setContestId(contestId);
        createDTO.setVirtualSessionId(virtual ? participant.getVirtualSessionId() : null);
        return submissionWritePort.submit(userId, createDTO);
    }

    // =========================================================================
    // Contest-problem link management
    // =========================================================================

    @Override
    @Transactional
    @CacheEvict(value = "contest", allEntries = true)
    @Audited(action = AuditVocabulary.UPDATE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, entityIdFrom = "contestId", captureOldState = false)
    public ContestProblemVO addProblem(String contestId, AddContestProblemDTO dto) {
        if (!currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(BaseErrorCode.FORBIDDEN);
        getContestOrThrow(contestId);
        ContestProblem existing = contestProblemMapper.findByContestIdAndProblemId(contestId, dto.getProblemId());
        if (existing != null) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Problem already exists in this contest");
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
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("addedProblemId", dto.getProblemId());
        newValues.put("problemIndex", cp.getProblemIndex());
        AuditContext.setNewValues(newValues);
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
    @Audited(action = AuditVocabulary.UPDATE_CONTEST, entityType = AuditVocabulary.ENTITY_CONTEST, entityIdFrom = "contestId", captureOldState = false)
    public void removeProblem(String contestId, Long problemId) {
        if (!currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")) throw new BusinessException(BaseErrorCode.FORBIDDEN);
        getContestOrThrow(contestId);
        ContestProblem cp = contestProblemMapper.findByContestIdAndProblemId(contestId, problemId);
        if (cp == null) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Problem not found in this contest");
        }
        if (contestProblemMapper.hasContestOwnedResults(cp.getId())) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Cannot remove a problem after contest submissions or results exist");
        }
        contestProblemMapper.deleteById(cp.getId());
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("removedProblemId", problemId);
        AuditContext.setNewValues(newValues);
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
        Contest contest = contestMapper.selectByIdForUpdate(contestId);
        if (contest == null || Boolean.TRUE.equals(contest.getIsDeleted())) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_FOUND);
        }
        return contest;
    }

    private ContestProblem getContestProblemOrThrow(String contestId, Long problemId) {
        getContestOrThrow(contestId);
        ContestProblem contestProblem = contestProblemMapper.findByContestIdAndProblemId(contestId, problemId);
        if (contestProblem == null) {
            throw new BusinessException(ContestErrorCode.PROBLEM_NOT_FOUND);
        }
        return contestProblem;
    }
}