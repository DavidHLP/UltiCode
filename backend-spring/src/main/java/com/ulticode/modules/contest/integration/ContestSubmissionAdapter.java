package com.ulticode.modules.contest.integration;

import com.ulticode.modules.contest.clock.ContestClock;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.submission.port.ContestSubmissionPort;
import com.ulticode.modules.contest.port.ContestRankingMarkDirtyPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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
 * <p>Extracted verbatim from {@code SubmissionServiceImpl.recordContestSubmissionIfNeeded}
 * and the {@code isVirtual} probe previously inlined in
 * {@code updateSubmissionResult} (2026-07-02 deepening, ADR-0001). Behaviour
 * is byte-for-byte identical: same RUNNING/STARTED gating, same real-vs-virtual
 * clock selection (R6.2 / F-06), same first-match-break, same fire-and-forget
 * swallowing (the port contract promises supplementary semantics; the adapter
 * still guards its own internal steps defensively).
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

    @Override
    public void recordSubmissionIfNeeded(String submissionId, String userId, Long problemId) {
        // 1. Find contest_problems containing this problem
        List<ContestProblem> contestProblems = contestProblemMapper.findByProblemId(problemId);

        for (ContestProblem cp : contestProblems) {
            // 2. Check if contest is RUNNING
            Contest contest = contestMapper.selectById(cp.getContestId());
            if (contest == null || !ContestStatus.RUNNING.name().equals(contest.getStatus())) {
                continue;
            }

            // 3. Check if user has STARTED status (D-06 -- matches DB enum 'STARTED')
            Optional<ContestParticipant> participant = contestParticipantMapper
                    .findByContestIdAndUserId(cp.getContestId(), userId);
            if (participant.isEmpty() ||
                    !ContestParticipantStatus.STARTED.name().equals(participant.get().getStatus())) {
                continue;
            }

            // 4. Create ContestSubmission (D-05)
            ContestSubmission cs = new ContestSubmission();
            cs.setSubmissionId(submissionId);
            cs.setContestId(cp.getContestId());
            cs.setContestProblemId(cp.getId());
            cs.setParticipantId(participant.get().getId());
            // R6.2 / F-06: pick the right clock per participant type. Real
            // contests use actualStartTime (fallback to startTime if admin
            // never triggered the scheduler transition); virtual sessions
            // use the participant's own startedAt — contest.startTime is
            // irrelevant for replays and would yield nonsense offsets.
            ContestParticipant p = participant.get();
            LocalDateTime clock = contestClock.participantClock(p, contest).orElse(null);
            if (clock != null) {
                cs.setTimeFromStart((int) Duration.between(clock, LocalDateTime.now()).getSeconds());
            }
            cs.setIsAccepted(false); // Will be updated when judge completes
            cs.setSubmittedAt(LocalDateTime.now());
            contestSubmissionMapper.insert(cs);
            contestRankingMarkDirtyPort.markDirty(contest.getId());

            // Only record for the first matching active contest
            break;
        }
    }

    @Override
    public boolean isVirtualParticipation(String submissionId) {
        return contestParticipantMapper
                .findIsVirtualBySubmissionId(submissionId)
                .orElse(false);
    }
}
