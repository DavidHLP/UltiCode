package com.ulticode.modules.contest.integration;

import com.ulticode.app.error.ContestErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.contest.clock.ContestClock;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.modules.websocket.port.ContestRankingMarkDirtyPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Contest-side adapter of {@link ContestSubmissionPort}.
 *
 * <p>Owns all contest persistence effects of a submission: the four contest
 * mappers and the realtime ranking-dirty flag. Lives in the contest module
 * (it has natural access to contest entities, enums, and mappers) while the
 * port it satisfies lives in the submission module — a deliberate dependency
 * inversion so the submission module never imports
 * {@code com.ulticode.modules.contest.*}.
 *
 * <p>Contest association writes are driven by the durable
 * {@code SubmissionCreated} outbox event from the Submission owner.
 * {@link #recordSubmissionFromEvent} inserts the mapping idempotently,
 * preserving the original occurrence time without re-checking the current
 * contest deadline (bounded admission window accepted by design).
 * Read-side lookups ({@link #findContestId}, {@link #isContestSubmission},
 * {@link #isVirtualParticipation}) serve the judge worker and result dispatch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContestSubmissionAdapter implements ContestSubmissionPort {

    private final ContestProblemMapper contestProblemMapper;
    private final ContestMapper contestMapper;
    private final ContestParticipantMapper contestParticipantMapper;
    private final ContestSubmissionMapper contestSubmissionMapper;
    private final ContestRankingMarkDirtyPort contestRankingMarkDirtyPort;
    private final ContestClock contestClock;

    /**
     * Apply an already-admitted remote contest submission. Admission is the
     * App's non-locking pre-check in {@code ContestServiceImpl} (DEC-018);
     * the Submission owner does not re-run status/deadline checks. The
     * bounded admission→write window is accepted by design, and this method
     * preserves the original occurrence time without re-checking the current
     * contest deadline.
     */
    public void recordSubmissionFromEvent(String submissionId, String userId, Long problemId,
                                          String contestId, String virtualSessionId,
                                          LocalDateTime occurredAt) {
        Optional<ContestSubmission> existing = contestSubmissionMapper
                .findBySubmissionId(submissionId);
        if (existing.isPresent()) {
            ContestSubmission current = existing.get();
            if (!Objects.equals(current.getContestId(), contestId)
                    || !Objects.equals(current.getVirtualSessionId(), virtualSessionId)) {
                throw new IllegalStateException(
                        "Conflicting contest association for submission " + submissionId);
            }
            return;
        }

        Contest contest = contestMapper.selectByIdForUpdate(contestId);
        if (contest == null || Boolean.TRUE.equals(contest.getIsDeleted())) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_FOUND);
        }
        ContestProblem contestProblem = contestProblemMapper
                .findByContestIdAndProblemId(contestId, problemId);
        if (contestProblem == null) {
            throw new BusinessException(ContestErrorCode.PROBLEM_NOT_FOUND);
        }

        boolean virtual = StringUtils.hasText(virtualSessionId);
        Optional<ContestParticipant> participant = virtual
                ? contestParticipantMapper.findVirtualForSubmissionAdmission(
                        contestId, userId, virtualSessionId)
                : contestParticipantMapper.findRealForSubmissionAdmission(contestId, userId);
        if (participant.isEmpty()) {
            throw new BusinessException(ContestErrorCode.CONTEST_NOT_REGISTERED);
        }
        ContestParticipant p = participant.get();
        if (!Objects.equals(userId, p.getUserId())) {
            throw new IllegalStateException("Contest association user mismatch");
        }

        ContestSubmission cs = new ContestSubmission();
        cs.setSubmissionId(submissionId);
        cs.setContestId(contestId);
        cs.setContestProblemId(contestProblem.getId());
        cs.setParticipantId(p.getId());
        cs.setVirtualSessionId(virtual ? p.getVirtualSessionId() : null);
        LocalDateTime start = contestClock.participantClock(p, contest).orElse(null);
        if (start != null) {
            long seconds = Duration.between(start, occurredAt).getSeconds();
            cs.setTimeFromStart((int) Math.min(Integer.MAX_VALUE, Math.max(0, seconds)));
        }
        cs.setIsAccepted(false);
        cs.setSubmittedAt(occurredAt);
        contestSubmissionMapper.insert(cs);
        contestRankingMarkDirtyPort.markDirty(contestId);
    }

    @Override
    public boolean isVirtualParticipation(String submissionId) {
        return contestParticipantMapper
                .findIsVirtualBySubmissionId(submissionId)
                .orElse(false);
    }

    @Override
    public boolean isContestSubmission(String submissionId) {
        return contestSubmissionMapper.findBySubmissionId(submissionId).isPresent();
    }

    @Override
    public String findContestId(String submissionId) {
        try {
            return contestSubmissionMapper.findBySubmissionId(submissionId)
                    .map(ContestSubmission::getContestId)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("No contest mapping for submission {}: {}", submissionId, e.getMessage());
            return null;
        }
    }
}
