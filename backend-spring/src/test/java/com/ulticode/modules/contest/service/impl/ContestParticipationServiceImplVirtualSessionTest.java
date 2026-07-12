package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.contest.dto.ParticipationStatusDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * Focused unit tests for the new behaviour added in 2026-06-11
 * (see PR `fix/contest-api-contracts`):
 *  - {@link ContestParticipationServiceImpl#getVirtualSession} populates
 *    {@code id} and {@code endsAt} fields.
 *  - {@link ContestParticipationServiceImpl#finishVirtualContest} accepts a null
 *    or blank sessionId and falls back to the stored virtualSessionId.
 *
 * These tests use Mockito only (no @WebMvcTest) to keep the test surface
 * minimal and avoid coupling with other pre-existing test files.
 */
class ContestParticipationServiceImplVirtualSessionTest {

    private ContestMapper contestMapper;
    private ContestParticipantMapper participantMapper;
    private Clock clock;
    private com.ulticode.modules.contest.clock.ContestClock contestClock;
    private ContestParticipationServiceImpl service;

    private static final String CONTEST_ID = "contest-finished-001";
    private static final String USER_ID = "user-001";
    private static final int DURATION_MIN = 150;

    @BeforeEach
    void setUp() {
        contestMapper = mock(ContestMapper.class);
        participantMapper = mock(ContestParticipantMapper.class);
        clock = mock(Clock.class);
        contestClock = mock(com.ulticode.modules.contest.clock.ContestClock.class);
        com.ulticode.modules.achievement.service.AchievementTriggerService achievementTriggerService =
                mock(com.ulticode.modules.achievement.service.AchievementTriggerService.class);
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        service = new ContestParticipationServiceImpl(contestMapper, participantMapper, clock, new FixedUuidGenerator(), contestClock, achievementTriggerService);
    }

    private ContestParticipant buildVirtualParticipant(String sessionId) {
        ContestParticipant p = new ContestParticipant();
        p.setId("participant-test-id");
        p.setContestId(CONTEST_ID);
        p.setUserId(USER_ID);
        p.setIsVirtual(true);
        p.setVirtualSessionId(sessionId);
        p.setStatus(ContestParticipantStatus.STARTED.name());
        p.setRegisteredAt(LocalDateTime.now().minusHours(1));
        p.setStartedAt(LocalDateTime.now().minusHours(1));
        return p;
    }

    private Contest buildContest() {
        Contest c = new Contest();
        c.setId(CONTEST_ID);
        c.setTitle("Test Contest");
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
        when(participantMapper.findByContestIdAndUserId(CONTEST_ID, USER_ID))
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
        when(participantMapper.findByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        ParticipationStatusDTO result = service.getVirtualSession(CONTEST_ID, USER_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getVirtualSession returns null when participant not found")
    void getVirtualSession_returnsNullWhenNotFound() {
        when(participantMapper.findByContestIdAndUserId(CONTEST_ID, USER_ID))
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
        when(participantMapper.findByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        // Should not throw — null sessionId is allowed
        service.finishVirtualContest(CONTEST_ID, null, USER_ID);

        // Verify the SQL update was issued (the actual contract: mapper.bulkFinishByIds
        // is called with the participant's id). The in-memory p is not mutated by
        // the mocked mapper, so checking p.getStatus() is incorrect here.
        verify(participantMapper).bulkFinishByIds(any(), any());
    }

    @Test
    @DisplayName("finishVirtualContest accepts blank sessionId and falls back to stored")
    void finishVirtualContest_acceptsBlankSessionId() {
        String storedUuid = UUID.randomUUID().toString();
        ContestParticipant p = buildVirtualParticipant(storedUuid);
        when(participantMapper.findByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        service.finishVirtualContest(CONTEST_ID, "  ", USER_ID);

        verify(participantMapper).bulkFinishByIds(any(), any());
    }

    @Test
    @DisplayName("finishVirtualContest accepts matching sessionId")
    void finishVirtualContest_acceptsMatchingSessionId() {
        String storedUuid = UUID.randomUUID().toString();
        ContestParticipant p = buildVirtualParticipant(storedUuid);
        when(participantMapper.findByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        service.finishVirtualContest(CONTEST_ID, storedUuid, USER_ID);

        verify(participantMapper).bulkFinishByIds(any(), any());
    }

    @Test
    @DisplayName("finishVirtualContest rejects mismatched sessionId with 400")
    void finishVirtualContest_rejectsMismatchedSessionId() {
        ContestParticipant p = buildVirtualParticipant(UUID.randomUUID().toString());
        when(participantMapper.findByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.finishVirtualContest(CONTEST_ID, "wrong-uuid", USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("finishVirtualContest rejects non-virtual participant with CONTEST_NOT_REGISTERED")
    void finishVirtualContest_rejectsNonVirtual() {
        ContestParticipant p = buildVirtualParticipant(UUID.randomUUID().toString());
        p.setIsVirtual(false);
        when(participantMapper.findByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.finishVirtualContest(CONTEST_ID, null, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_NOT_REGISTERED);
    }

    @Test
    @DisplayName("finishVirtualContest is idempotent — re-running on FINISHED does not re-stamp")
    void finishVirtualContest_idempotentOnAlreadyFinished() {
        String storedUuid = UUID.randomUUID().toString();
        LocalDateTime originalFinish = LocalDateTime.of(2026, 6, 18, 9, 42, 5);
        ContestParticipant p = buildVirtualParticipant(storedUuid);
        p.setId("participant-finished-id");
        p.setStatus(ContestParticipantStatus.FINISHED.name());
        p.setFinishedAt(originalFinish);
        p.setUpdatedAt(originalFinish);
        when(participantMapper.findByContestIdAndUserId(CONTEST_ID, USER_ID))
                .thenReturn(Optional.of(p));

        // Re-finish an already-FINISHED session — should be a no-op
        service.finishVirtualContest(CONTEST_ID, storedUuid, USER_ID);

        // The original finish time must be preserved (not re-stamped to now())
        assertThat(p.getFinishedAt()).isEqualTo(originalFinish);
        assertThat(p.getUpdatedAt()).isEqualTo(originalFinish);
        // The SQL UPDATE must NOT be re-issued
        verify(participantMapper, never()).bulkFinishByIds(any(), any());
    }
}
