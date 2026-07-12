package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.contest.clock.ContestClock;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.ParticipationStatusDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.achievement.constants.AchievementType;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.ContestParticipationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ContestParticipationService}. Owns contest
 * registration, virtual replay, and the participation lifecycle invariants,
 * plus the registration achievement side effect.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestParticipationServiceImpl implements ContestParticipationService {

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper participantMapper;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;
    private final ContestClock contestClock;
    private final AchievementTriggerService achievementTriggerService;

    @Override
    @Transactional
    public void registerForContest(String contestId, String userId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);

        if (!ContestStatus.UPCOMING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (contest.getRegistrationEnd() != null && now.isAfter(contest.getRegistrationEnd())) {
            throw new BusinessException(ErrorCode.CONTEST_REGISTRATION_CLOSED);
        }

        // P1-3 (TOCTOU fix): drop the read-then-insert existsBy check. The DB unique
        // key (contest_id, user_id, virtual_session_id) is the source of truth:
        // two concurrent inserts race; one will lose on the DB constraint, which
        // we catch below. The tryIncrementRegisteredCount happens BEFORE the
        // insert so a failing insert rolls back the increment (same transaction).
        int updated = contestMapper.tryIncrementRegisteredCount(contestId);
        if (updated == 0) throw new BusinessException(ErrorCode.CONTEST_FULL);

        ContestParticipant participant = new ContestParticipant();
        participant.setContestId(contestId);
        participant.setUserId(userId);
        participant.setStatus(ContestParticipantStatus.REGISTERED.name());
        participant.setRegisteredAt(now);
        participant.setIsVirtual(false);
        try {
            participantMapper.insert(participant);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Race lost: another transaction inserted the same (contest, user)
            // row. The transaction will roll back the registeredCount increment.
            throw new BusinessException(ErrorCode.CONTEST_ALREADY_REGISTERED);
        }
        log.info("User {} registered for contest {}", userId, contestId);

        // Fire the contest participation achievement. Internal side effect of
        // registration; failures must not roll back the registration itself.
        try {
            long participationCount = participantMapper.countByUserId(userId);
            achievementTriggerService.trigger(userId, AchievementType.CONTEST_PARTICIPATION, (int) participationCount);
        } catch (Exception e) {
            log.warn("Failed to trigger contest achievement for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void unregisterFromContest(String contestId, String userId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);

        ContestParticipant participant = participantMapper.findByContestIdAndUserId(contestId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_NOT_REGISTERED));

        if (!ContestStatus.UPCOMING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);
        }
        // P2-7 fix: also reject unregister after registration_end. Without this
        // check a user could unregister after the registration window closed,
        // letting them dodge a no-show penalty in some scoring rules.
        LocalDateTime now = LocalDateTime.now(clock);
        if (contest.getRegistrationEnd() != null && now.isAfter(contest.getRegistrationEnd())) {
            throw new BusinessException(ErrorCode.CONTEST_REGISTRATION_CLOSED);
        }
        participantMapper.deleteById(participant.getId());
        contestMapper.decrementRegisteredCount(contestId);
        log.info("User {} unregistered from contest {}", userId, contestId);
    }

    @Override
    public ParticipationStatusDTO getParticipationStatus(String contestId, String userId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);

        ParticipationStatusDTO status = new ParticipationStatusDTO();
        status.setContestId(contestId);
        status.setTitle(contest.getTitle());
        status.setStartTime(contest.getStartTime());
        status.setEndTime(contest.getEndTime());

        Optional<ContestParticipant> participantOpt = participantMapper.findByContestIdAndUserId(contestId, userId);
        if (participantOpt.isEmpty()) {
            status.setStatus("not_participated");
            status.setHasStarted(false);
            status.setIsActive(false);
            status.setIsCompleted(false);
            status.setCanParticipate(ContestStatus.UPCOMING.name().equals(contest.getStatus()) ||
                    ContestStatus.RUNNING.name().equals(contest.getStatus()));
            return status;
        }

        ContestParticipant p = participantOpt.get();
        status.setStatus(p.getStatus().toLowerCase());
        status.setRegisteredAt(p.getRegisteredAt());
        status.setStartedAt(p.getStartedAt());
        status.setCompletedAt(p.getFinishedAt());
        status.setRanking(p.getFinalRank());
        status.setScore(p.getTotalScore() != null ? p.getTotalScore().longValue() : null);
        status.setPenalty(p.getTotalPenalty());
        status.setHasStarted(p.getStartedAt() != null);
        status.setIsCompleted(ContestParticipantStatus.FINISHED.name().equals(p.getStatus()));
        status.setIsActive(ContestParticipantStatus.STARTED.name().equals(p.getStatus()));
        return status;
    }

    @Override
    public List<ContestVO> getUserContests(String userId, String type) {
        List<ContestParticipant> participants = participantMapper.findByUserId(userId);

        List<ContestParticipant> filtered = participants.stream().filter(p -> {
            String s = p.getStatus();
            return switch (type) {
                case "registered" -> ContestParticipantStatus.REGISTERED.name().equals(s);
                case "virtual" -> Boolean.TRUE.equals(p.getIsVirtual());
                default -> ContestParticipantStatus.FINISHED.name().equals(s) ||
                        ContestParticipantStatus.STARTED.name().equals(s);
            };
        }).collect(Collectors.toList());

        return filtered.stream().map(p -> {
            Contest contest = contestMapper.selectById(p.getContestId());
            ContestVO vo = toContestVO(contest, userId);
            vo.setUserRanking(p.getFinalRank());
            vo.setUserScore(p.getTotalScore() != null ? p.getTotalScore().longValue() : null);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationStatusDTO startVirtualContest(String contestId, String userId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);

        if (!ContestStatus.FINISHED.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ENDED);
        }

        // R3.3: idempotent start. Use FOR UPDATE to serialize concurrent calls
        // from the same user (e.g. double-clicks, two tabs). The lock holds
        // until COMMIT, so the second caller will see the first caller's row
        // and short-circuit. Without the lock, both would pass the existence
        // check, both would INSERT (different virtual_session_id UUIDs), and
        // the user would end up with two active sessions.
        Optional<ContestParticipant> active = participantMapper
                .findActiveVirtualSessionForUpdate(contestId, userId);
        if (active.isPresent()) {
            log.info("R3.3: user {} already has active virtual session for contest {}, returning it",
                    userId, contestId);
            return getVirtualSession(contestId, userId);
        }

        // No active session. Optionally also check for a non-active (FINISHED)
        // virtual row so we can still report the prior session if the user
        // is replaying a previously finished virtual run; not blocking.
        LocalDateTime now = LocalDateTime.now(clock);
        ContestParticipant participant = new ContestParticipant();
        participant.setContestId(contestId);
        participant.setUserId(userId);
        participant.setStatus(ContestParticipantStatus.STARTED.name());
        participant.setRegisteredAt(now);
        participant.setStartedAt(now);
        participant.setIsVirtual(true);
        participant.setVirtualSessionId(uuidGenerator.newId());
        participantMapper.insert(participant);

        log.info("User {} started virtual contest {}", userId, contestId);
        return getVirtualSession(contestId, userId);
    }

    @Override
    public ParticipationStatusDTO getVirtualSession(String contestId, String userId) {
        Optional<ContestParticipant> participantOpt = participantMapper.findByContestIdAndUserId(contestId, userId);
        if (participantOpt.isEmpty() || !Boolean.TRUE.equals(participantOpt.get().getIsVirtual())) {
            return null;
        }
        ContestParticipant participant = participantOpt.get();
        Contest contest = contestMapper.selectById(contestId);

        ParticipationStatusDTO status = new ParticipationStatusDTO();
        status.setContestId(contestId);
        status.setId(participant.getVirtualSessionId());
        status.setTitle(contest.getTitle());
        status.setStatus(participant.getStatus().toLowerCase());
        status.setRegisteredAt(participant.getRegisteredAt());
        status.setStartedAt(participant.getStartedAt());
        status.setStartTime(participant.getStartedAt());
        LocalDateTime virtualEnd = contestClock.effectiveEndTime(participant, contest).orElse(null);
        status.setEndTime(virtualEnd);
        status.setEndsAt(virtualEnd);
        status.setScore(participant.getTotalScore() != null ? participant.getTotalScore().longValue() : null);
        status.setPenalty(participant.getTotalPenalty());
        status.setHasStarted(true);
        status.setIsActive(ContestParticipantStatus.STARTED.name().equals(participant.getStatus()));
        status.setIsCompleted(ContestParticipantStatus.FINISHED.name().equals(participant.getStatus()));
        return status;
    }

    @Override
    @Transactional
    public void finishVirtualContest(String contestId, String sessionId, String userId) {
        Optional<ContestParticipant> participantOpt = participantMapper.findByContestIdAndUserId(contestId, userId);
        if (participantOpt.isEmpty()) throw new BusinessException(ErrorCode.CONTEST_NOT_REGISTERED);

        ContestParticipant participant = participantOpt.get();
        if (!Boolean.TRUE.equals(participant.getIsVirtual())) throw new BusinessException(ErrorCode.CONTEST_NOT_REGISTERED);

        // sessionId is optional. If absent, fall back to the participant's stored
        // virtualSessionId (single active session per user per contest, so unambiguous).
        // If present, validate it matches to defend against tampering.
        if (sessionId != null && !sessionId.isBlank()
                && !sessionId.equals(participant.getVirtualSessionId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid virtual session id");
        }
        String effectiveSessionId = (sessionId == null || sessionId.isBlank())
                ? participant.getVirtualSessionId()
                : sessionId;
        if (effectiveSessionId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "No active virtual session");
        }

        // Idempotency: re-running finish on a session that is already FINISHED
        // must not re-stamp finished_at/updated_at. Without this guard, repeated
        // POSTs (network retry, double-click, polling tab) would overwrite the
        // original finish time and lose the audit trail of when the user
        // actually finished.
        if (ContestParticipantStatus.FINISHED.name().equals(participant.getStatus())) {
            log.info("User {} virtual contest {} session {} already finished, skip re-stamp",
                    userId, contestId, effectiveSessionId);
            return;
        }

        // R6.2 / F-01: route through the bulk-finish mapper so the audit's
        // "auto-finish central dispatch" invariant holds — same path the
        // scheduler uses, no direct updateById bypass.
        participantMapper.bulkFinishByIds(
                java.util.List.of(participant.getId()), LocalDateTime.now(clock));

        log.info("User {} finished virtual contest {} session {}", userId, contestId, effectiveSessionId);
    }

    private ContestVO toContestVO(Contest contest, String userId) {
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
        if (userId != null && !userId.isBlank()) {
            Optional<?> participantOpt = participantMapper.findByContestIdAndUserId(contest.getId(), userId);
            if (participantOpt.isPresent()) {
                var p = (ContestParticipant) participantOpt.get();
                vo.setIsParticipating(true);
                vo.setUserRanking(p.getFinalRank());
                vo.setUserScore(p.getTotalScore() != null ? p.getTotalScore().longValue() : null);
            } else {
                vo.setIsParticipating(false);
            }
        }
        return vo;
    }
}
