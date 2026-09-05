package com.ulticode.modules.contest.integration;

import com.ulticode.modules.websocket.port.ContestRankingMarkDirtyPort;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContestSubmissionAdapter event-driven contest association")
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
        adapter = new ContestSubmissionAdapter(
                contestProblemMapper, contestMapper, contestParticipantMapper,
                contestSubmissionMapper, rankingMarkDirtyPort, contestClock);
    }

    @Test
    @DisplayName("created event preserves admission time without rechecking current deadline")
    void createdEvent_recordsLateDeliveryIdempotently() {
        Contest contest = contest(ContestStatus.FINISHED.name());
        ContestProblem problem = contestProblem();
        ContestParticipant participant = participant(true, "session-1");
        participant.setStatus(ContestParticipantStatus.REGISTERED.name());
        when(contestSubmissionMapper.findBySubmissionId("submission-event"))
                .thenReturn(Optional.empty());
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(contest);
        when(contestProblemMapper.findByContestIdAndProblemId(CONTEST_ID, PROBLEM_ID))
                .thenReturn(problem);
        when(contestParticipantMapper.findVirtualForSubmissionAdmission(
                CONTEST_ID, USER_ID, "session-1")).thenReturn(Optional.of(participant));
        when(contestClock.participantClock(participant, contest))
                .thenReturn(Optional.of(NOW.minusMinutes(5)));
        LocalDateTime occurredAt = NOW.minusMinutes(2);

        adapter.recordSubmissionFromEvent("submission-event", USER_ID, PROBLEM_ID,
                CONTEST_ID, "session-1", occurredAt);

        ArgumentCaptor<ContestSubmission> captor = ArgumentCaptor.forClass(ContestSubmission.class);
        verify(contestSubmissionMapper).insert(captor.capture());
        assertThat(captor.getValue().getSubmittedAt()).isEqualTo(occurredAt);
        assertThat(captor.getValue().getTimeFromStart()).isEqualTo(180);
        verify(rankingMarkDirtyPort).markDirty(CONTEST_ID);
    }

    @Test
    @DisplayName("created event replay is a no-op when the association already exists")
    void createdEvent_replay_isNoOp() {
        ContestSubmission existing = new ContestSubmission();
        existing.setSubmissionId("submission-event");
        existing.setContestId(CONTEST_ID);
        existing.setVirtualSessionId("session-1");
        when(contestSubmissionMapper.findBySubmissionId("submission-event"))
                .thenReturn(Optional.of(existing));

        adapter.recordSubmissionFromEvent("submission-event", USER_ID, PROBLEM_ID,
                CONTEST_ID, "session-1", NOW);

        verifyNoInteractions(contestMapper, contestProblemMapper, contestParticipantMapper,
                contestClock);
        verify(contestSubmissionMapper, never()).insert(any(ContestSubmission.class));
        verify(rankingMarkDirtyPort, never()).markDirty(any());
    }

    @Test
    @DisplayName("created event with conflicting association is rejected without insert")
    void createdEvent_conflictingAssociation_isRejected() {
        ContestSubmission existing = new ContestSubmission();
        existing.setSubmissionId("submission-event");
        existing.setContestId("contest-other");
        existing.setVirtualSessionId(null);
        when(contestSubmissionMapper.findBySubmissionId("submission-event"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> adapter.recordSubmissionFromEvent(
                "submission-event", USER_ID, PROBLEM_ID, CONTEST_ID, null, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflicting contest association");
        verify(contestSubmissionMapper, never()).insert(any(ContestSubmission.class));
        verify(rankingMarkDirtyPort, never()).markDirty(any());
    }

    @Test
    @DisplayName("created event with mismatched participant user is rejected")
    void createdEvent_userMismatch_isRejected() {
        Contest contest = contest(ContestStatus.RUNNING.name());
        ContestProblem problem = contestProblem();
        ContestParticipant participant = participant(true, "session-1");
        participant.setUserId("user-other");
        when(contestSubmissionMapper.findBySubmissionId("submission-event"))
                .thenReturn(Optional.empty());
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(contest);
        when(contestProblemMapper.findByContestIdAndProblemId(CONTEST_ID, PROBLEM_ID))
                .thenReturn(problem);
        when(contestParticipantMapper.findVirtualForSubmissionAdmission(
                CONTEST_ID, USER_ID, "session-1")).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> adapter.recordSubmissionFromEvent(
                "submission-event", USER_ID, PROBLEM_ID, CONTEST_ID, "session-1", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user mismatch");
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
