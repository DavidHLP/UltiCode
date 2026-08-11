package com.ulticode.modules.contest.integration;

import com.ulticode.app.api.service.ContestRankingMarkDirtyPort;
import com.ulticode.app.error.ContestErrorCode;
import com.ulticode.common.exception.BusinessException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContestSubmissionAdapter explicit admission")
class ContestSubmissionAdapterTest {

    private static final String CONTEST_ID = "contest-1";
    private static final String USER_ID = "user-1";
    private static final Long PROBLEM_ID = 42L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 12, 0);

    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestMapper contestMapper;
    @Mock private ContestParticipantMapper contestParticipantMapper;
    @Mock private ContestSubmissionMapper contestSubmissionMapper;
    @Mock private ContestRankingMarkDirtyPort rankingMarkDirtyPort;
    @Mock private ContestClock contestClock;

    private ContestSubmissionAdapter adapter;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);
        adapter = new ContestSubmissionAdapter(
                contestProblemMapper, contestMapper, contestParticipantMapper,
                contestSubmissionMapper, rankingMarkDirtyPort, contestClock, clock);
    }

    @Test
    @DisplayName("ordinary submission does not scan or infer a contest")
    void ordinarySubmission_doesNotInferContest() {
        adapter.recordSubmissionIfNeeded("submission-1", USER_ID, PROBLEM_ID, null, null);

        verifyNoInteractions(contestMapper, contestProblemMapper, contestParticipantMapper,
                contestSubmissionMapper, rankingMarkDirtyPort, contestClock);
    }

    @Test
    @DisplayName("real contest context locks exact participant and records mapping")
    void realContext_recordsOnlyExplicitContest() {
        Contest contest = contest(ContestStatus.RUNNING.name());
        ContestProblem problem = contestProblem();
        ContestParticipant participant = participant(false, null);
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(contest);
        when(contestProblemMapper.findByContestIdAndProblemId(CONTEST_ID, PROBLEM_ID))
                .thenReturn(problem);
        when(contestParticipantMapper.findRealForSubmissionAdmission(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(participant));
        when(contestClock.contestEndTime(contest)).thenReturn(Optional.of(NOW));
        when(contestClock.participantClock(participant, contest)).thenReturn(Optional.of(NOW.minusMinutes(3)));

        adapter.recordSubmissionIfNeeded("submission-1", USER_ID, PROBLEM_ID, CONTEST_ID, null);

        ArgumentCaptor<ContestSubmission> captor = ArgumentCaptor.forClass(ContestSubmission.class);
        verify(contestSubmissionMapper).insert(captor.capture());
        ContestSubmission recorded = captor.getValue();
        assertThat(recorded.getContestId()).isEqualTo(CONTEST_ID);
        assertThat(recorded.getContestProblemId()).isEqualTo(problem.getId());
        assertThat(recorded.getParticipantId()).isEqualTo(participant.getId());
        assertThat(recorded.getVirtualSessionId()).isNull();
        assertThat(recorded.getTimeFromStart()).isEqualTo(180);
        verify(rankingMarkDirtyPort).markDirty(CONTEST_ID);
        verify(contestProblemMapper, never()).findByProblemId(anyLong());
    }

    @Test
    @DisplayName("virtual context accepts a FINISHED parent inside its own session window")
    void virtualContext_usesSessionWindow() {
        Contest contest = contest(ContestStatus.FINISHED.name());
        ContestProblem problem = contestProblem();
        ContestParticipant participant = participant(true, "session-1");
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(contest);
        when(contestProblemMapper.findByContestIdAndProblemId(CONTEST_ID, PROBLEM_ID))
                .thenReturn(problem);
        when(contestParticipantMapper.findVirtualForSubmissionAdmission(
                CONTEST_ID, USER_ID, "session-1")).thenReturn(Optional.of(participant));
        when(contestClock.effectiveEndTime(participant, contest)).thenReturn(Optional.of(NOW));
        when(contestClock.participantClock(participant, contest)).thenReturn(Optional.of(NOW.minusMinutes(5)));

        adapter.recordSubmissionIfNeeded("submission-virtual", USER_ID, PROBLEM_ID,
                CONTEST_ID, "session-1");

        ArgumentCaptor<ContestSubmission> captor = ArgumentCaptor.forClass(ContestSubmission.class);
        verify(contestSubmissionMapper).insert(captor.capture());
        assertThat(captor.getValue().getVirtualSessionId()).isEqualTo("session-1");
        assertThat(captor.getValue().getTimeFromStart()).isEqualTo(300);
    }

    @Test
    @DisplayName("expired virtual session is rejected before contest mapping insert")
    void virtualContext_afterDeadline_isRejected() {
        Contest contest = contest(ContestStatus.FINISHED.name());
        ContestParticipant participant = participant(true, "session-1");
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(contest);
        when(contestProblemMapper.findByContestIdAndProblemId(CONTEST_ID, PROBLEM_ID))
                .thenReturn(contestProblem());
        when(contestParticipantMapper.findVirtualForSubmissionAdmission(
                CONTEST_ID, USER_ID, "session-1")).thenReturn(Optional.of(participant));
        when(contestClock.effectiveEndTime(participant, contest))
                .thenReturn(Optional.of(NOW.minusNanos(1)));

        assertThatThrownBy(() -> adapter.recordSubmissionIfNeeded(
                "submission-late", USER_ID, PROBLEM_ID, CONTEST_ID, "session-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_ENDED);
        verify(contestSubmissionMapper, never()).insert(any(ContestSubmission.class));
        verify(rankingMarkDirtyPort, never()).markDirty(any());
    }

    private static Contest contest(String status) {
        Contest contest = new Contest();
        contest.setId(CONTEST_ID);
        contest.setStatus(status);
        contest.setIsDeleted(false);
        return contest;
    }

    private static ContestProblem contestProblem() {
        ContestProblem problem = new ContestProblem();
        problem.setId("contest-problem-1");
        problem.setContestId(CONTEST_ID);
        problem.setProblemId(PROBLEM_ID);
        return problem;
    }

    private static ContestParticipant participant(boolean virtual, String sessionId) {
        ContestParticipant participant = new ContestParticipant();
        participant.setId(virtual ? "participant-virtual" : "participant-real");
        participant.setContestId(CONTEST_ID);
        participant.setUserId(USER_ID);
        participant.setStatus(ContestParticipantStatus.STARTED.wireValue());
        participant.setIsVirtual(virtual);
        participant.setVirtualSessionId(sessionId);
        participant.setStartedAt(NOW.minusMinutes(5));
        return participant;
    }
}
