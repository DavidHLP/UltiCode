package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.ContestErrorCode;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.contest.dto.ParticipationStatusDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.ContestParticipantTransitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for the new behaviour added in 2026-06-11
 * (see PR `fix/contest-api-contracts`):
 *  - {@link ContestParticipationServiceImpl#getVirtualSession} populates
 *    {@code id} and {@code endsAt} fields.
 *  - {@link ContestParticipationServiceImpl#finishVirtualContest} accepts a null
 *    or blank sessionId and falls back to the stored virtualSessionId.
 *
 * <p>These tests use Mockito only (no @WebMvcTest) to keep the test surface
 * minimal and avoid coupling with other pre-existing test files.
 */
class ContestParticipationServiceImplVirtualSessionTest {

    private ContestMapper contestMapper;
    private ContestParticipantMapper participantMapper;
    private ContestParticipantTransitions participantTransitions;
    private Clock clock;
    private com.ulticode.modules.contest.clock.ContestClock contestClock;
    private com.ulticode.modules.achievement.port.ContestAchievementPort achievementTriggerPort;
    private com.ulticode.modules.contest.projection.ContestProjection contestProjection;
    private ContestParticipationServiceImpl service;

    private static final String CONTEST_ID = "contest-finished-001";
    private static final String USER_ID = "user-001";
    private static final int DURATION_MIN = 150;

    @BeforeEach
    void setUp() {
        contestMapper = mock(ContestMapper.class);
        participantMapper = mock(ContestParticipantMapper.class);
        participantTransitions = mock(ContestParticipantTransitions.class);
        clock = mock(Clock.class);
        contestClock = mock(com.ulticode.modules.contest.clock.ContestClock.class);
        achievementTriggerPort =
                mock(com.ulticode.modules.achievement.port.ContestAchievementPort.class);
        contestProjection =
                mock(com.ulticode.modules.contest.projection.ContestProjection.class);
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        service = new ContestParticipationServiceImpl(
                contestMapper, participantMapper, clock, new FixedUuidGenerator(),
                contestClock, achievementTriggerPort, participantTransitions,
                contestProjection);
    }

    private ContestParticipant buildVirtualParticipant(String sessionId) {
        ContestParticipant p = new ContestParticipant();
        p.setId("participant-test-id");
        p.setContestId(CONTEST_ID);
        p.setUserId(USER_ID);
        p.setIsVirtual(true);
        p.setVirtualSessionId(sessionId);
        p.setStatus(ContestParticipantStatus.STARTED.wireValue());
        p.setRegisteredAt(LocalDateTime.now().minusHours(1));
        p.setStartedAt(LocalDateTime.now().minusHours(1));
        return p;
    }

    private Contest buildContest() {
        Contest c = new Contest();
        c.setId(CONTEST_ID);
        c.setTitle("Test Contest");
        c.setIsVisible(true);
        c.setDurationMinutes(DURATION_MIN);
        return c;
    }

    // ============================================================
    // getVirtualSession — id + endsAt
    // ============================================================

    @Test
    @DisplayName("getVirtualSession populates id and endsAt with stored virtualSessionId")
    void getVirtualSession_populatesIdAndEndsAt() {
        String sessionUuid = UUID.randomUUID().toString();
        ContestParticipant p = buildVirtualParticipant(sessionUuid);
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));
        Contest contest = buildContest();
        when(contestMapper.selectById(CONTEST_ID)).thenReturn(contest);
        LocalDateTime virtualEnd = p.getStartedAt().plusMinutes(contest.getDurationMinutes());
        when(contestClock.effectiveEndTime(p, contest)).thenReturn(java.util.Optional.of(virtualEnd));

        ParticipationStatusDTO result = service.getVirtualSession(CONTEST_ID, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(sessionUuid);
        assertThat(result.getEndsAt()).isNotNull();
        assertThat(result.getEndsAt()).isEqualTo(result.getEndTime());
        assertThat(result.getEndsAt()).isEqualTo(virtualEnd);
    }

    @Test
    @DisplayName("getVirtualSession returns null for non-virtual participant")
    void getVirtualSession_returnsNullForNonVirtual() {
        ContestParticipant p = buildVirtualParticipant(UUID.randomUUID().toString());
        p.setIsVirtual(false);
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        ParticipationStatusDTO result = service.getVirtualSession(CONTEST_ID, USER_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getVirtualSession returns null when participant not found")
    void getVirtualSession_returnsNullWhenNotFound() {
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.empty());

        ParticipationStatusDTO result = service.getVirtualSession(CONTEST_ID, USER_ID);

        assertThat(result).isNull();
    }

    // ============================================================
    // finishVirtualContest — sessionId fallback + validation
    // ============================================================

    @Test
    @DisplayName("finishVirtualContest accepts null sessionId and falls back to stored")
    void finishVirtualContest_acceptsNullSessionId() {
        String storedUuid = UUID.randomUUID().toString();
        ContestParticipant p = buildVirtualParticipant(storedUuid);
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        // Should not throw — null sessionId is allowed
        service.finishVirtualContest(CONTEST_ID, null, USER_ID);

        verify(participantTransitions)
                .bulkFinishVirtualByIds(any(), any());
    }

    @Test
    @DisplayName("finishVirtualContest accepts blank sessionId and falls back to stored")
    void finishVirtualContest_acceptsBlankSessionId() {
        String storedUuid = UUID.randomUUID().toString();
        ContestParticipant p = buildVirtualParticipant(storedUuid);
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        service.finishVirtualContest(CONTEST_ID, "  ", USER_ID);

        verify(participantTransitions)
                .bulkFinishVirtualByIds(any(), any());
    }

    @Test
    @DisplayName("finishVirtualContest accepts matching sessionId")
    void finishVirtualContest_acceptsMatchingSessionId() {
        String storedUuid = UUID.randomUUID().toString();
        ContestParticipant p = buildVirtualParticipant(storedUuid);
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        service.finishVirtualContest(CONTEST_ID, storedUuid, USER_ID);

        verify(participantTransitions)
                .bulkFinishVirtualByIds(any(), any());
    }

    @Test
    @DisplayName("finishVirtualContest rejects mismatched sessionId with 400")
    void finishVirtualContest_rejectsMismatchedSessionId() {
        ContestParticipant p = buildVirtualParticipant(UUID.randomUUID().toString());
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.finishVirtualContest(CONTEST_ID, "wrong-uuid", USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", BaseErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("finishVirtualContest rejects non-virtual participant with CONTEST_NOT_REGISTERED")
    void finishVirtualContest_rejectsNonVirtual() {
        ContestParticipant p = buildVirtualParticipant(UUID.randomUUID().toString());
        p.setIsVirtual(false);
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.finishVirtualContest(CONTEST_ID, null, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_NOT_REGISTERED);
    }

    @Test
    @DisplayName("finishVirtualContest is idempotent — re-running on FINISHED does not re-stamp")
    void finishVirtualContest_idempotentOnAlreadyFinished() {
        String storedUuid = UUID.randomUUID().toString();
        LocalDateTime originalFinish = LocalDateTime.of(2026, 6, 18, 9, 42, 5);
        ContestParticipant p = buildVirtualParticipant(storedUuid);
        p.setId("participant-finished-id");
        p.setStatus(ContestParticipantStatus.FINISHED.wireValue());
        p.setFinishedAt(originalFinish);
        p.setUpdatedAt(originalFinish);
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        // Re-finish an already-FINISHED session — should be a no-op
        service.finishVirtualContest(CONTEST_ID, storedUuid, USER_ID);

        // The original finish time must be preserved (not re-stamped to now())
        assertThat(p.getFinishedAt()).isEqualTo(originalFinish);
        assertThat(p.getUpdatedAt()).isEqualTo(originalFinish);
        // The SQL UPDATE must NOT be re-issued
        verify(participantTransitions, never())
                .bulkFinishVirtualByIds(any(), any());
    }

    // ============================================================
    // registerForContest — capacity, dedup, achievement side effect
    // ============================================================

    private Contest buildUpcomingContest() {
        Contest c = buildContest();
        c.setStatus(com.ulticode.modules.contest.entity.enums.ContestStatus.UPCOMING.name());
        c.setRegistrationEnd(null);
        return c;
    }

    @Test
    @DisplayName("registerForContest inserts participant and fires participation achievement")
    void registerForContest_successTriggersAchievement() {
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(buildUpcomingContest());
        when(contestMapper.tryIncrementRegisteredCount(CONTEST_ID)).thenReturn(1);
        when(participantMapper.countByUserId(USER_ID)).thenReturn(3L);

        service.registerForContest(CONTEST_ID, USER_ID);

        verify(participantTransitions).registerRealParticipant(any(ContestParticipant.class));
        verify(achievementTriggerPort).triggerContestParticipation(
                USER_ID,
                3);
    }

    @Test
    @DisplayName("registerForContest rejects hidden contests before touching capacity")
    void registerForContest_hiddenContest() {
        Contest hidden = buildUpcomingContest();
        hidden.setIsVisible(false);
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(hidden);

        assertThatThrownBy(() -> service.registerForContest(CONTEST_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_NOT_FOUND);

        verify(contestMapper, never()).tryIncrementRegisteredCount(any());
        verify(participantTransitions, never()).registerRealParticipant(any());
    }

    @Test
    @DisplayName("registerForContest throws CONTEST_FULL after capacity claim fails")
    void registerForContest_full() {
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(buildUpcomingContest());
        when(contestMapper.tryIncrementRegisteredCount(CONTEST_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.registerForContest(CONTEST_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_FULL);

        // The enclosing transaction rolls this insert back when the conditional
        // capacity claim returns zero.
        verify(participantTransitions).registerRealParticipant(any(ContestParticipant.class));
        verify(achievementTriggerPort, never()).triggerContestParticipation(any(), anyInt());
    }

    @Test
    @DisplayName("registerForContest maps duplicate insert to CONTEST_ALREADY_REGISTERED, no achievement")
    void registerForContest_duplicate() {
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(buildUpcomingContest());
        when(contestMapper.tryIncrementRegisteredCount(CONTEST_ID)).thenReturn(1);
        doThrow(new org.springframework.dao.DuplicateKeyException("dup"))
                .when(participantTransitions).registerRealParticipant(any(ContestParticipant.class));

        assertThatThrownBy(() -> service.registerForContest(CONTEST_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_ALREADY_REGISTERED);

        verify(achievementTriggerPort, never()).triggerContestParticipation(any(), anyInt());
        verify(contestMapper, never()).tryIncrementRegisteredCount(any());
    }

    @Test
    @DisplayName("registerForContest rejects non-upcoming contest before touching capacity")
    void registerForContest_notUpcoming() {
        Contest c = buildContest();
        c.setStatus(com.ulticode.modules.contest.entity.enums.ContestStatus.RUNNING.name());
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(c);

        assertThatThrownBy(() -> service.registerForContest(CONTEST_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);

        verify(contestMapper, never()).tryIncrementRegisteredCount(any());
    }

    @Test
    @DisplayName("registerForContest swallows achievement failure — registration still succeeds")
    void registerForContest_achievementFailureDoesNotRollBack() {
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(buildUpcomingContest());
        when(contestMapper.tryIncrementRegisteredCount(CONTEST_ID)).thenReturn(1);
        when(participantMapper.countByUserId(USER_ID)).thenReturn(1L);
        doThrow(new RuntimeException("achievement service down"))
                .when(achievementTriggerPort).triggerContestParticipation(any(), anyInt());

        // Must not propagate — the side effect is best-effort by design.
        service.registerForContest(CONTEST_ID, USER_ID);

        verify(participantTransitions).registerRealParticipant(any(ContestParticipant.class));
    }

    @Test
    @DisplayName("unregisterFromContest locks contest and participant before deleting")
    void unregisterForContest_locksLifecycleRows() {
        ContestParticipant participant = buildVirtualParticipant("not-a-session");
        participant.setIsVirtual(false);
        participant.setStatus(ContestParticipantStatus.REGISTERED.wireValue());
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(buildUpcomingContest());
        when(participantMapper.findByContestIdAndUserIdForUpdate(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(participant));
        when(participantTransitions.deleteById(participant.getId())).thenReturn(1);

        service.unregisterFromContest(CONTEST_ID, USER_ID);

        verify(contestMapper).selectByIdForUpdate(CONTEST_ID);
        verify(participantMapper).findByContestIdAndUserIdForUpdate(CONTEST_ID, USER_ID);
        verify(participantTransitions).deleteById(participant.getId());
        verify(contestMapper).decrementRegisteredCount(CONTEST_ID);
    }

    @Test
    @DisplayName("unregisterFromContest rejects a lifecycle transition observed after locking")
    void unregisterForContest_rejectsRunningContestWithoutDelete() {
        Contest running = buildUpcomingContest();
        running.setStatus(ContestStatus.RUNNING.name());
        ContestParticipant participant = buildVirtualParticipant("not-a-session");
        participant.setIsVirtual(false);
        participant.setStatus(ContestParticipantStatus.REGISTERED.wireValue());
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(running);
        when(participantMapper.findByContestIdAndUserIdForUpdate(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> service.unregisterFromContest(CONTEST_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_ONLY_REGISTER_UPCOMING);

        verify(participantTransitions, never()).deleteById(anyString());
        verify(contestMapper, never()).decrementRegisteredCount(anyString());
    }

    // ============================================================
    // startVirtualContest — regression: virtualSessionId must be set
    // ============================================================

    @Test
    @DisplayName("startVirtualContest sets virtualSessionId and returns it as DTO id")
    void startVirtualContest_setsVirtualSessionIdAndReturnsIt() {
        Contest c = buildContest();
        c.setStatus("FINISHED");
        when(contestMapper.selectById(CONTEST_ID)).thenReturn(c);
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(c);
        when(participantMapper.findActiveVirtualSessionForUpdate(CONTEST_ID, USER_ID))
                .thenReturn(Optional.empty());

        // Holder to capture the generated session ID after insert
        java.util.concurrent.atomic.AtomicReference<String> generatedId =
                new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            ContestParticipant p = invocation.getArgument(0);
            generatedId.set(p.getVirtualSessionId());
            return p;
        }).when(participantTransitions).startVirtualParticipant(any(ContestParticipant.class));

        // getVirtualSession reads the DB — return a valid participant
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenAnswer(invocation -> {
                    ContestParticipant p = buildVirtualParticipant(generatedId.get());
                    return Optional.of(p);
                });

        ParticipationStatusDTO result = service.startVirtualContest(CONTEST_ID, USER_ID);

        assertThat(generatedId.get()).isNotNull();
        assertThat(generatedId.get()).isNotBlank();
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(generatedId.get());
    }

    // ============================================================
    // startVirtualContest — regression: DuplicateKeyException handler
    // must return existing session, not crash or lose the winner's id
    // ============================================================

    @Test
    @DisplayName("startVirtualContest rejects hidden contests before creating a session")
    void startVirtualContest_hiddenContest() {
        Contest hidden = buildContest();
        hidden.setStatus("FINISHED");
        hidden.setIsVisible(false);
        when(contestMapper.selectById(CONTEST_ID)).thenReturn(hidden);
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(hidden);

        assertThatThrownBy(() -> service.startVirtualContest(CONTEST_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_NOT_FOUND);

        verify(participantMapper, never()).findActiveVirtualSessionForUpdate(any(), any());
        verify(participantTransitions, never()).startVirtualParticipant(any());
    }

    @Test
    @DisplayName("startVirtualContest DuplicateKeyException returns existing session without error")
    void startVirtualContest_dupKey_returnsExistingSession() {
        Contest c = buildContest();
        c.setStatus("FINISHED");
        when(contestMapper.selectById(CONTEST_ID)).thenReturn(c);
        when(contestMapper.selectByIdForUpdate(CONTEST_ID)).thenReturn(c);
        when(participantMapper.findActiveVirtualSessionForUpdate(CONTEST_ID, USER_ID))
                .thenReturn(Optional.empty());

        // Simulate the race: another transaction won and inserted first
        String winnerSessionId = UUID.randomUUID().toString();
        doThrow(new org.springframework.dao.DuplicateKeyException(
                        "Duplicate entry for active_virtual_key"))
                .when(participantTransitions).startVirtualParticipant(any(ContestParticipant.class));

        // getVirtualSession reads the winner's row from the DB
        ContestParticipant existing = buildVirtualParticipant(winnerSessionId);
        when(participantMapper.findVirtualByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(existing));

        // Must not throw — catch block handles DuplicateKeyException gracefully
        ParticipationStatusDTO result = service.startVirtualContest(CONTEST_ID, USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(winnerSessionId);
        // The existing session is returned, not null or a newly-generated id
        verify(participantMapper).findActiveVirtualSessionForUpdate(CONTEST_ID, USER_ID);
        verify(participantTransitions).startVirtualParticipant(any(ContestParticipant.class));
    }

}
