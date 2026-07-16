package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.clock.ContestClock;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestProblemResultMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.FirstSolveRecordMapper;
import com.ulticode.modules.contest.port.ContestRankingMarkDirtyPort;
import com.ulticode.modules.contest.port.ContestStatusPushPort;
import com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor;
import com.ulticode.modules.contest.service.RatingCalculationService;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.intent.ContestStartingIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContestLifecycleServiceImpl} — the time-driven contest
 * lifetime seam. Covers the P0-2 batch start, the M2 bulk auto-finish, the
 * P2-5 idempotent cascade delete, the {@link #tick(LocalDateTime)} heartbeat
 * (due selection + idempotent transition + participant closure + push/ranking
 * + rating handoff), and the {@link #sendReminders(LocalDateTime)} fan-out.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestLifecycleServiceImpl (tick, reminders, P0-2, M2, P2-5)")
class ContestLifecycleServiceImplTest {

    private static final String CONTEST_ID = "contest-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0);

    @Mock private ContestMapper contestMapper;
    @Mock private ContestParticipantMapper contestParticipantMapper;
    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestSubmissionMapper contestSubmissionMapper;
    @Mock private ContestProblemResultMapper contestProblemResultMapper;
    @Mock private FirstSolveRecordMapper firstSolveRecordMapper;
    @Mock private ContestRankingCacheEvictor rankingCacheEvictor;
    @Mock private Clock clock;
    @Mock private ContestClock contestClock;
    @Mock private ContestStatusPushPort contestStatusPushPort;
    @Mock private ContestRankingMarkDirtyPort contestRankingMarkDirtyPort;
    @Mock private RatingCalculationService ratingService;
    @Mock private NotificationDispatcher notificationDispatcher;

    private ContestLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW.toInstant(ZoneOffset.UTC));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        service = new ContestLifecycleServiceImpl(
                contestMapper, contestParticipantMapper, contestProblemMapper,
                contestSubmissionMapper, contestProblemResultMapper,
                firstSolveRecordMapper, rankingCacheEvictor, clock,
                contestClock, contestStatusPushPort, contestRankingMarkDirtyPort,
                ratingService, notificationDispatcher);
    }

    /** P0-2: batchStartParticipants delegates to mapper and returns the count. */
    @Test
    @DisplayName("P0-2: batchStartParticipants calls mapper and returns count")
    void batchStartParticipants_delegatesToMapper() {
        when(contestParticipantMapper.batchUpdateStatus(eq(CONTEST_ID), eq("REGISTERED"), eq("STARTED"), any(LocalDateTime.class)))
                .thenReturn(7);

        int updated = service.batchStartParticipants(CONTEST_ID);

        assertThat(updated).isEqualTo(7);
        verify(contestParticipantMapper).batchUpdateStatus(
                eq(CONTEST_ID), eq("REGISTERED"), eq("STARTED"), any(LocalDateTime.class));
    }

    /** M2: autoFinishVirtualParticipants queries, then issues a single bulk UPDATE keyed by ids. */
    @Test
    @DisplayName("M2: auto-finish virtual participants uses a single bulk UPDATE by ids")
    void autoFinishVirtualParticipants_processesQueryResults() {
        ContestParticipant v1 = newParticipant("v-1");
        ContestParticipant v2 = newParticipant("v-2");
        when(contestParticipantMapper.findVirtualParticipantsToFinish(any(LocalDateTime.class)))
                .thenReturn(List.of(v1, v2));
        when(contestParticipantMapper.bulkFinishByIds(any(), any(LocalDateTime.class)))
                .thenReturn(2);

        int total = service.autoFinishVirtualParticipants();

        assertThat(total).isEqualTo(2);
        verify(contestParticipantMapper, times(1))
                .bulkFinishByIds(argThat(ids -> ids.contains("v-1") && ids.contains("v-2")),
                        any(LocalDateTime.class));
        verify(contestParticipantMapper, never())
                .batchUpdateStatus(anyString(), anyString(), anyString(), any(LocalDateTime.class));
    }

    /** tick: a due UPCOMING contest transitions to RUNNING, pushes status, marks ranking dirty. */
    @Test
    @DisplayName("tick: due UPCOMING contest transitions to RUNNING + emits status + marks dirty")
    void tick_dueUpcoming_transitionsToRunning() {
        Contest contest = newContest(ContestStatus.UPCOMING.name());
        contest.setStartTime(NOW.minusMinutes(1)); // already past start
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of(contest));
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of());
        when(contestMapper.tryTransitionToRunning(eq(CONTEST_ID), any(LocalDateTime.class))).thenReturn(1);

        service.tick(NOW);

        verify(contestMapper).tryTransitionToRunning(eq(CONTEST_ID), any(LocalDateTime.class));
        verify(contestStatusPushPort).emitStatus(eq(CONTEST_ID), eq(ContestStatus.RUNNING),
                any(), argThat(java.util.Objects::isNull), argThat(java.util.Objects::isNull));
        verify(contestRankingMarkDirtyPort).markDirty(CONTEST_ID);
    }

    /** tick: a due RUNNING contest transitions to FINISHED, closes real participants, hands off rating. */
    @Test
    @DisplayName("tick: due RUNNING contest transitions to FINISHED + closes participants + rates")
    void tick_dueRunning_transitionsToFinished() {
        Contest contest = newContest(ContestStatus.RUNNING.name());
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of(contest));
        when(contestClock.contestEndTime(contest)).thenReturn(Optional.of(NOW.minusMinutes(1)));
        when(contestMapper.tryTransitionToFinished(eq(CONTEST_ID), any(LocalDateTime.class))).thenReturn(1);

        service.tick(NOW);

        verify(contestMapper).tryTransitionToFinished(eq(CONTEST_ID), any(LocalDateTime.class));
        verify(contestParticipantMapper).finishStartedRealParticipants(eq(CONTEST_ID), any(LocalDateTime.class));
        verify(contestStatusPushPort).emitStatus(eq(CONTEST_ID), eq(ContestStatus.FINISHED),
                argThat(java.util.Objects::isNull), any(), argThat(java.util.Objects::isNull));
        verify(ratingService).calculateAndUpdate(CONTEST_ID);
    }

    /** tick: an UPCOMING contest whose start is still in the future is left alone. */
    @Test
    @DisplayName("tick: UPCOMING contest starting in the future is not transitioned")
    void tick_notDueUpcoming_isSkipped() {
        Contest contest = newContest(ContestStatus.UPCOMING.name());
        contest.setStartTime(NOW.plusHours(1)); // future
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of(contest));
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of());

        service.tick(NOW);

        verify(contestMapper, never()).tryTransitionToRunning(eq(CONTEST_ID), any(LocalDateTime.class));
        verify(contestStatusPushPort, never()).emitStatus(anyString(), any(), any(), any(), any());
    }

    /** tick is idempotent: an already-RUNNING contest is not re-transitioned. */
    @Test
    @DisplayName("tick: already-RUNNING contest is not re-transitioned (idempotent)")
    void tick_alreadyRunning_isSkipped() {
        Contest contest = newContest(ContestStatus.RUNNING.name());
        contest.setStartTime(NOW.minusMinutes(1));
        // Returned by BOTH status queries (defensive): must skip because status is RUNNING.
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of(contest));
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of());
        when(contestClock.contestEndTime(contest)).thenReturn(Optional.empty());

        service.tick(NOW);

        // tryTransitionToRunning is invoked but the conditional UPDATE finds
        // status != UPCOMING (affected=0), so the transition is a no-op.
        verify(contestMapper).tryTransitionToRunning(eq(CONTEST_ID), any(LocalDateTime.class));
        verify(contestMapper, never()).updateById(any(Contest.class));
        verify(contestStatusPushPort, never()).emitStatus(anyString(), any(), any(), any(), any());
    }

    /** sendReminders: a contest in the T-24h window fans out a ContestStartingIntent with reminderType=24h. */
    @Test
    @DisplayName("sendReminders: T-24h contest dispatches a ContestStartingIntent with reminderType=24h")
    void sendReminders_t24hWindow_dispatchesIntent() {
        Contest contest = newContest(ContestStatus.UPCOMING.name());
        contest.setStartTime(NOW.plusHours(24).plusMinutes(30)); // inside 24h..25h window
        ContestParticipant participant = newParticipant("p-1");
        participant.setUserId("user-1");
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of(contest));
        when(contestParticipantMapper.findByContestIds(any())).thenReturn(List.of(participant));

        service.sendReminders(NOW);

        ArgumentCaptor<ContestStartingIntent> captor =
                ArgumentCaptor.forClass(ContestStartingIntent.class);
        verify(notificationDispatcher).dispatch(captor.capture());
        ContestStartingIntent intent = captor.getValue();
        assertThat(intent.reminderType()).isEqualTo("24h");
        assertThat(intent.userId()).isEqualTo("user-1");
        assertThat(intent.contestId()).isEqualTo(CONTEST_ID);
    }

    /** sendReminders: a contest in the T-1h window fans out a distinct intent (reminderType=1h). */
    @Test
    @DisplayName("sendReminders: T-1h contest dispatches a distinct ContestStartingIntent (reminderType=1h)")
    void sendReminders_t1hWindow_dispatchesDistinctIntent() {
        Contest contest = newContest(ContestStatus.UPCOMING.name());
        contest.setStartTime(NOW.plusHours(1).plusMinutes(30)); // inside 1h..2h window
        ContestParticipant participant = newParticipant("p-1");
        participant.setUserId("user-1");
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of(contest));
        when(contestParticipantMapper.findByContestIds(any())).thenReturn(List.of(participant));

        service.sendReminders(NOW);

        ArgumentCaptor<ContestStartingIntent> captor =
                ArgumentCaptor.forClass(ContestStartingIntent.class);
        verify(notificationDispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().reminderType()).isEqualTo("1h");
        // Distinct intent id from the 24h case so the ledger dedups them independently.
        assertThat(captor.getValue().intentId()).endsWith(":1h");
    }

    /** sendReminders: a contest outside both windows dispatches nothing. */
    @Test
    @DisplayName("sendReminders: contest outside T-24h/T-1h windows dispatches nothing")
    void sendReminders_outsideWindow_dispatchesNothing() {
        Contest contest = newContest(ContestStatus.UPCOMING.name());
        contest.setStartTime(NOW.plusHours(12)); // between the two windows
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of(contest));

        service.sendReminders(NOW);

        verify(notificationDispatcher, never()).dispatch(any());
    }

    /** P2-5: deleteContestCascade is idempotent on missing/soft-deleted contests. */
    @Test
    @DisplayName("P2-5: deleteContestCascade is a no-op when contest is already soft-deleted")
    void deleteContestCascade_alreadyDeleted_isNoOp() {
        Contest deleted = new Contest();
        deleted.setId(CONTEST_ID);
        deleted.setIsDeleted(true);
        when(contestMapper.selectById(CONTEST_ID)).thenReturn(deleted);

        service.deleteContestCascade(CONTEST_ID);

        verify(contestSubmissionMapper, never()).deleteByContestId(anyString());
        verify(contestParticipantMapper, never()).deleteByContestId(anyString());
        verify(rankingCacheEvictor, never()).evictRankingCache();
    }

    /** P2-5: deleteContestCascade physically deletes 5 tables and evicts the ranking cache. */
    @Test
    @DisplayName("P2-5: deleteContestCascade deletes submissions/cpr/first-solve/participants/problems")
    void deleteContestCascade_deletesAllRelatedTables() {
        Contest c = new Contest();
        c.setId(CONTEST_ID);
        c.setIsDeleted(false);
        when(contestMapper.selectById(CONTEST_ID)).thenReturn(c);

        service.deleteContestCascade(CONTEST_ID);

        verify(contestSubmissionMapper).deleteByContestId(CONTEST_ID);
        verify(contestProblemResultMapper).deleteByContestId(CONTEST_ID);
        verify(firstSolveRecordMapper).deleteByContestId(CONTEST_ID);
        verify(contestParticipantMapper).deleteByContestId(CONTEST_ID);
        verify(contestProblemMapper).deleteByContestId(CONTEST_ID);
        verify(rankingCacheEvictor).evictRankingCache();
    }

    // ---- helpers --------------------------------------------------------

    private static Contest newContest(String status) {
        Contest c = new Contest();
        c.setId(CONTEST_ID);
        c.setStatus(status);
        return c;
    }

    private static ContestParticipant newParticipant(String id) {
        ContestParticipant p = new ContestParticipant();
        p.setId(id);
        p.setContestId(CONTEST_ID);
        p.setIsVirtual(true);
        return p;
    }
}
