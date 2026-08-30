package com.ulticode.modules.contest.service.impl;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.app.error.ContestErrorCode;

import com.ulticode.modules.contest.clock.ContestClock;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestAdjudicationReceiptMapper;
import com.ulticode.modules.contest.mapper.ContestCascadeMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.app.api.service.ContestRankingMarkDirtyPort;
import com.ulticode.app.api.service.ContestStatusPushPort;
import com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor;
import com.ulticode.modules.contest.service.ContestParticipantTransitions;
import com.ulticode.modules.contest.service.RatingCalculationService;
import com.ulticode.app.api.service.ContestNotificationPort;
import com.ulticode.submission.api.dto.SubmissionAdjudicationFact;
import com.ulticode.submission.api.service.SubmissionAdjudicationReadPort;

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
import static org.mockito.Mockito.doThrow;
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
 *
 * <p>All participant status transitions and cascade deletes are verified
 * through the {@link ContestParticipantTransitions} seam — the lifecycle
 * service has no direct dependency on the participant mapper.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestLifecycleServiceImpl (tick, reminders, P0-2, M2, P2-5)")
class ContestLifecycleServiceImplTest {

    private static final String CONTEST_ID = "contest-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0);

    @Mock private ContestMapper contestMapper;
    @Mock private ContestParticipantTransitions participantTransitions;
    @Mock private ContestCascadeMapper contestCascadeMapper;
    @Mock private ContestRankingCacheEvictor rankingCacheEvictor;
    @Mock private Clock clock;
    @Mock private ContestClock contestClock;
    @Mock private ContestStatusPushPort contestStatusPushPort;
    @Mock private ContestRankingMarkDirtyPort contestRankingMarkDirtyPort;
    @Mock private RatingCalculationService ratingService;
    @Mock private ContestNotificationPort contestNotificationPort;
    @Mock private ContestAdjudicationReceiptMapper adjudicationReceiptMapper;
    @Mock private SubmissionAdjudicationReadPort submissionAdjudicationReadPort;

    private ContestLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW.toInstant(ZoneOffset.UTC));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        service = new ContestLifecycleServiceImpl(
                contestMapper, participantTransitions, contestCascadeMapper,
                rankingCacheEvictor, clock, contestClock, contestStatusPushPort,
                contestRankingMarkDirtyPort, contestNotificationPort, ratingService,
                adjudicationReceiptMapper, submissionAdjudicationReadPort);
    }

    /** P0-2: batchStartParticipants crosses the seam and returns its count. */
    @Test
    @DisplayName("P0-2: batchStartParticipants crosses the transitions seam")
    void batchStartParticipants_crossesTransitionsSeam() {
        when(participantTransitions.batchStartRegistered(eq(CONTEST_ID), any(LocalDateTime.class)))
                .thenReturn(7);
        int updated = service.batchStartParticipants(CONTEST_ID);

        assertThat(updated).isEqualTo(7);
        verify(participantTransitions)
                .batchStartRegistered(eq(CONTEST_ID), any(LocalDateTime.class));
    }

    /** M2: autoFinishVirtualParticipants calls the seam's read-then-write composite. */
    @Test
    @DisplayName("M2: auto-finish virtual participants uses the read-then-write composite")
    void autoFinishVirtualParticipants_usesFindAndFinishComposite() {
        when(participantTransitions.findAndFinishExpiredVirtuals(any(LocalDateTime.class)))
                .thenReturn(2);

        int total = service.autoFinishVirtualParticipants();

        assertThat(total).isEqualTo(2);
        verify(participantTransitions, times(1))
                .findAndFinishExpiredVirtuals(any(LocalDateTime.class));
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
        when(participantTransitions.batchStartRegistered(eq(CONTEST_ID), any(LocalDateTime.class)))
                .thenReturn(0);

        service.tick(NOW);

        verify(contestMapper).tryTransitionToRunning(eq(CONTEST_ID), any(LocalDateTime.class));
        verify(contestStatusPushPort).emitStatus(eq(CONTEST_ID), eq(ContestStatus.RUNNING.name()),
                any(), argThat(java.util.Objects::isNull), argThat(java.util.Objects::isNull));
        verify(contestRankingMarkDirtyPort).markDirty(CONTEST_ID);
    }

    @Test
    @DisplayName("tick: failed RUNNING side effect compensates the claim for retry")
    void tick_runningSideEffectFailure_revertsClaim() {
        Contest contest = newContest(ContestStatus.UPCOMING.name());
        contest.setStartTime(NOW.minusMinutes(1));
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of(contest));
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.FINISHING.name())).thenReturn(List.of());
        when(contestMapper.tryTransitionToRunning(eq(CONTEST_ID), any(LocalDateTime.class))).thenReturn(1);
        doThrow(new IllegalStateException("push unavailable"))
                .when(contestStatusPushPort)
                .emitStatus(eq(CONTEST_ID), eq(ContestStatus.RUNNING.name()), any(), any(), any());

        service.tick(NOW);

        verify(contestMapper).revertRunningToUpcoming(eq(CONTEST_ID), eq(NOW));
        verify(contestRankingMarkDirtyPort, never()).markDirty(anyString());
    }

    /** tick: a due RUNNING contest is claimed as FINISHING, then finalized and rated. */
    @Test
    @DisplayName("tick: due RUNNING contest claims FINISHING, closes participants, and rates")
    void tick_dueRunning_transitionsToFinished() {
        Contest contest = newContest(ContestStatus.RUNNING.name());
        Contest finishing = newContest(ContestStatus.FINISHING.name());
        finishing.setActualEndTime(NOW.minusMinutes(1));
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of(contest));
        when(contestMapper.findByStatus(ContestStatus.FINISHING.name())).thenReturn(List.of(finishing));
        when(contestClock.contestEndTime(contest)).thenReturn(Optional.of(NOW.minusMinutes(1)));
        when(contestMapper.tryTransitionToFinishing(eq(CONTEST_ID), any(LocalDateTime.class))).thenReturn(1);
        when(participantTransitions.finishStartedReal(eq(CONTEST_ID), any(LocalDateTime.class)))
                .thenReturn(5);
        when(contestMapper.tryFinalizeFinished(eq(CONTEST_ID), any(LocalDateTime.class))).thenReturn(1);

        service.tick(NOW);

        verify(contestMapper).tryTransitionToFinishing(eq(CONTEST_ID), any(LocalDateTime.class));
        verify(participantTransitions)
                .finishStartedReal(eq(CONTEST_ID), any(LocalDateTime.class));
        verify(contestStatusPushPort).emitStatus(eq(CONTEST_ID), eq(ContestStatus.FINISHED.name()),
                argThat(java.util.Objects::isNull), any(), argThat(java.util.Objects::isNull));
        verify(contestRankingMarkDirtyPort).markDirty(CONTEST_ID);
        verify(contestMapper).tryFinalizeFinished(eq(CONTEST_ID), any(LocalDateTime.class));
        verify(ratingService).calculateAndUpdate(CONTEST_ID);
    }

    @Test
    @DisplayName("tick: FINISHING waits for real contest adjudication to drain")
    void tick_finishingWaitsForAdjudicationDrain() {
        Contest finishing = newContest(ContestStatus.FINISHING.name());
        finishing.setActualEndTime(NOW.minusMinutes(1));
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.FINISHING.name())).thenReturn(List.of(finishing));
        when(adjudicationReceiptMapper.findRealSubmissionIdsByContestId(CONTEST_ID))
                .thenReturn(List.of("submission-1"));
        when(submissionAdjudicationReadPort.findByIds(any()))
                .thenReturn(List.of(new SubmissionAdjudicationFact("submission-1", 1L, "Pending")));
        when(adjudicationReceiptMapper.findReceiptGenerationsBySubmissionIds(any()))
                .thenReturn(List.of());

        service.tick(NOW);

        verify(participantTransitions, never())
                .finishStartedReal(anyString(), any(LocalDateTime.class));
        verify(ratingService, never()).calculateAndUpdate(anyString());
        verify(contestMapper, never()).tryFinalizeFinished(anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("tick: terminal owner fact with a matching receipt allows finalization")
    void tick_finishingTerminalFactWithReceipt_finalizes() {
        Contest finishing = newContest(ContestStatus.FINISHING.name());
        finishing.setActualEndTime(NOW.minusMinutes(1));
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.FINISHING.name())).thenReturn(List.of(finishing));
        when(adjudicationReceiptMapper.findRealSubmissionIdsByContestId(CONTEST_ID))
                .thenReturn(List.of("submission-1"));
        when(submissionAdjudicationReadPort.findByIds(any()))
                .thenReturn(List.of(new SubmissionAdjudicationFact("submission-1", 1L, "Accepted")));
        when(adjudicationReceiptMapper.findReceiptGenerationsBySubmissionIds(any()))
                .thenReturn(List.of(new ContestAdjudicationReceiptMapper.ReceiptGeneration(
                        "submission-1", 1L)));
        when(participantTransitions.finishStartedReal(eq(CONTEST_ID), any(LocalDateTime.class)))
                .thenReturn(0);
        when(contestMapper.tryFinalizeFinished(eq(CONTEST_ID), any(LocalDateTime.class))).thenReturn(1);

        service.tick(NOW);

        verify(ratingService).calculateAndUpdate(CONTEST_ID);
        verify(contestMapper).tryFinalizeFinished(eq(CONTEST_ID), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("tick: terminal user verdict without a receipt remains retryable")
    void tick_finishingTerminalFactWithoutReceipt_waits() {
        Contest finishing = newContest(ContestStatus.FINISHING.name());
        finishing.setActualEndTime(NOW.minusMinutes(1));
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.FINISHING.name())).thenReturn(List.of(finishing));
        when(adjudicationReceiptMapper.findRealSubmissionIdsByContestId(CONTEST_ID))
                .thenReturn(List.of("submission-1"));
        when(submissionAdjudicationReadPort.findByIds(any()))
                .thenReturn(List.of(new SubmissionAdjudicationFact("submission-1", 1L, "Wrong Answer")));
        when(adjudicationReceiptMapper.findReceiptGenerationsBySubmissionIds(any()))
                .thenReturn(List.of());

        service.tick(NOW);

        verify(ratingService, never()).calculateAndUpdate(anyString());
        verify(contestMapper, never()).tryFinalizeFinished(anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("tick: infrastructure owner verdict does not block finalization")
    void tick_finishingInfrastructureFact_doesNotWaitForReceipt() {
        Contest finishing = newContest(ContestStatus.FINISHING.name());
        finishing.setActualEndTime(NOW.minusMinutes(1));
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.FINISHING.name())).thenReturn(List.of(finishing));
        when(adjudicationReceiptMapper.findRealSubmissionIdsByContestId(CONTEST_ID))
                .thenReturn(List.of("submission-1"));
        when(submissionAdjudicationReadPort.findByIds(any()))
                .thenReturn(List.of(new SubmissionAdjudicationFact("submission-1", 1L, "Sandbox Error")));
        when(adjudicationReceiptMapper.findReceiptGenerationsBySubmissionIds(any()))
                .thenReturn(List.of());
        when(participantTransitions.finishStartedReal(eq(CONTEST_ID), any(LocalDateTime.class)))
                .thenReturn(0);
        when(contestMapper.tryFinalizeFinished(eq(CONTEST_ID), any(LocalDateTime.class))).thenReturn(1);

        service.tick(NOW);

        verify(ratingService).calculateAndUpdate(CONTEST_ID);
        verify(contestMapper).tryFinalizeFinished(eq(CONTEST_ID), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("tick: FINISHING rating failure remains retryable and later publishes FINISHED")
    void tick_finishingFailure_isRetried() {
        Contest finishing = newContest(ContestStatus.FINISHING.name());
        finishing.setActualEndTime(NOW.minusMinutes(1));
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.RUNNING.name())).thenReturn(List.of());
        when(contestMapper.findByStatus(ContestStatus.FINISHING.name())).thenReturn(List.of(finishing));
        when(participantTransitions.finishStartedReal(eq(CONTEST_ID), any(LocalDateTime.class)))
                .thenReturn(0);
        org.mockito.Mockito.doThrow(new IllegalStateException("rating unavailable"))
                .doNothing().when(ratingService).calculateAndUpdate(CONTEST_ID);
        when(contestMapper.tryFinalizeFinished(eq(CONTEST_ID), any(LocalDateTime.class))).thenReturn(1);

        service.tick(NOW);
        verify(contestMapper, never()).tryFinalizeFinished(eq(CONTEST_ID), any(LocalDateTime.class));

        service.tick(NOW);
        verify(contestMapper).tryFinalizeFinished(eq(CONTEST_ID), any(LocalDateTime.class));
        verify(contestStatusPushPort).emitStatus(eq(CONTEST_ID), eq(ContestStatus.FINISHED.name()),
                argThat(java.util.Objects::isNull), any(), argThat(java.util.Objects::isNull));
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
        verify(contestMapper, never()).updateById((Contest) any());
        verify(contestStatusPushPort, never()).emitStatus(anyString(), any(), any(), any(), any());
    }

    /** sendReminders: a contest in the T-24h window fans out a notification with reminderType=24h. */
    @Test
    @DisplayName("sendReminders: T-24h contest dispatches notification with reminderType=24h")
    void sendReminders_t24hWindow_dispatchesIntent() {
        Contest contest = newContest(ContestStatus.UPCOMING.name());
        contest.setStartTime(NOW.plusHours(24).plusMinutes(30)); // inside 24h..25h window
        ContestParticipant participant = newParticipant("p-1");
        participant.setUserId("user-1");
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of(contest));
        when(participantTransitions.findByContestIdsForReminder(any())).thenReturn(List.of(participant));

        service.sendReminders(NOW);

        verify(contestNotificationPort).notifyContestStarting(
                eq("user-1"), eq(CONTEST_ID), any(), any(), eq("24h"));
    }

    /** sendReminders: a contest in the T-1h window fans out a distinct notification (reminderType=1h). */
    @Test
    @DisplayName("sendReminders: T-1h contest dispatches notification with reminderType=1h")
    void sendReminders_t1hWindow_dispatchesDistinctIntent() {
        Contest contest = newContest(ContestStatus.UPCOMING.name());
        contest.setStartTime(NOW.plusHours(1).plusMinutes(30)); // inside 1h..2h window
        ContestParticipant participant = newParticipant("p-1");
        participant.setUserId("user-1");
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of(contest));
        when(participantTransitions.findByContestIdsForReminder(any())).thenReturn(List.of(participant));

        service.sendReminders(NOW);

        verify(contestNotificationPort).notifyContestStarting(
                eq("user-1"), eq(CONTEST_ID), any(), any(), eq("1h"));
    }

    /** sendReminders: a contest outside both windows dispatches nothing. */
    @Test
    @DisplayName("sendReminders: contest outside T-24h/T-1h windows dispatches nothing")
    void sendReminders_outsideWindow_dispatchesNothing() {
        Contest contest = newContest(ContestStatus.UPCOMING.name());
        contest.setStartTime(NOW.plusHours(12)); // between the two windows
        when(contestMapper.findByStatus(ContestStatus.UPCOMING.name())).thenReturn(List.of(contest));

        service.sendReminders(NOW);

        verify(contestNotificationPort, never()).notifyContestStarting(any(), any(), any(), any(), any());
    }

    /** P2-5: a retry cleans any leftover children after a committed soft-delete. */
    @Test
    @DisplayName("P2-5: deleteContestCascade retries cleanup for an already soft-deleted contest")
    void deleteContestCascade_alreadyDeleted_retriesCleanup() {
        Contest deleted = new Contest();
        deleted.setId(CONTEST_ID);
        deleted.setIsDeleted(true);
        when(contestMapper.selectByIdIncludingDeletedForUpdate(CONTEST_ID)).thenReturn(deleted);

        service.deleteContestCascade(CONTEST_ID, "admin-1");

        verify(contestCascadeMapper).deleteAdjudicationReceiptsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteProblemResultsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteSubmissionsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteFirstSolveRecordsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteRankingsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteAnalyticsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteVirtualSessionsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteAnnouncementsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteRatingCalculationsByContestId(CONTEST_ID);
        verify(participantTransitions).deleteAllByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteProblemsByContestId(CONTEST_ID);
        verify(contestMapper, never()).updateById((Contest) any());
        verify(rankingCacheEvictor).evictRankingCache();
    }

    /** P2-5: one owner transaction covers every contest-owned relation. */
    @Test
    @DisplayName("P2-5: deleteContestCascade deletes all contest-owned relations")
    void deleteContestCascade_deletesAllRelatedTables() {
        Contest c = new Contest();
        c.setId(CONTEST_ID);
        c.setStatus(ContestStatus.UPCOMING.name());
        c.setIsDeleted(false);
        when(contestMapper.selectByIdIncludingDeletedForUpdate(CONTEST_ID)).thenReturn(c);

        service.deleteContestCascade(CONTEST_ID, "admin-1");

        verify(contestCascadeMapper).deleteAdjudicationReceiptsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteProblemResultsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteSubmissionsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteFirstSolveRecordsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteRankingsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteAnalyticsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteVirtualSessionsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteAnnouncementsByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteRatingCalculationsByContestId(CONTEST_ID);
        verify(participantTransitions).deleteAllByContestId(CONTEST_ID);
        verify(contestCascadeMapper).deleteProblemsByContestId(CONTEST_ID);
        verify(contestMapper).updateById((Contest) argThat((Contest row) ->
                Boolean.TRUE.equals(row.getIsDeleted()) && "admin-1".equals(row.getDeletedBy())));
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
