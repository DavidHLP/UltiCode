package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.ParticipationStatusDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.ContestSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of ContestSchedulerService.
 * Handles contest registration, virtual contests, and participation lifecycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestSchedulerServiceImpl implements ContestSchedulerService {

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper participantMapper;

    @Override
    @Transactional
    public void registerForContest(String contestId, String userId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) throw new BusinessException(ErrorCode.CONTEST_NOT_FOUND);

        if (!ContestStatus.UPCOMING.name().equals(contest.getStatus())) {
            throw new BusinessException(ErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);
        }
        LocalDateTime now = LocalDateTime.now();
        if (contest.getRegistrationEnd() != null && now.isAfter(contest.getRegistrationEnd())) {
            throw new BusinessException(ErrorCode.CONTEST_REGISTRATION_CLOSED);
        }
        if (participantMapper.existsByContestIdAndUserId(contestId, userId)) {
            throw new BusinessException(ErrorCode.CONTEST_ALREADY_REGISTERED);
        }
        int updated = contestMapper.tryIncrementRegisteredCount(contestId);
        if (updated == 0) throw new BusinessException(ErrorCode.CONTEST_FULL);

        ContestParticipant participant = new ContestParticipant();
        participant.setContestId(contestId);
        participant.setUserId(userId);
        participant.setStatus(ContestParticipantStatus.REGISTERED.name());
        participant.setRegisteredAt(now);
        participant.setIsVirtual(false);
        participantMapper.insert(participant);
        log.info("User {} registered for contest {}", userId, contestId);
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
        Optional<ContestParticipant> existing = participantMapper.findByContestIdAndUserId(contestId, userId);
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getIsVirtual())) {
            return getVirtualSession(contestId, userId);
        }

        LocalDateTime now = LocalDateTime.now();
        ContestParticipant participant = new ContestParticipant();
        participant.setContestId(contestId);
        participant.setUserId(userId);
        participant.setStatus(ContestParticipantStatus.STARTED.name());
        participant.setRegisteredAt(now);
        participant.setStartedAt(now);
        participant.setIsVirtual(true);
        participant.setVirtualSessionId(UUID.randomUUID().toString());
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
        status.setEndTime(participant.getStartedAt().plusMinutes(contest.getDurationMinutes()));
        status.setEndsAt(participant.getStartedAt().plusMinutes(contest.getDurationMinutes()));
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

        participant.setStatus(ContestParticipantStatus.FINISHED.name());
        participant.setFinishedAt(LocalDateTime.now());
        participantMapper.updateById(participant);

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
