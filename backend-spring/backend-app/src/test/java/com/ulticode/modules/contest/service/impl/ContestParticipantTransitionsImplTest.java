package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.service.ContestParticipantTransitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the deep {@link ContestParticipantTransitions} seam —
 * the single owner of {@link ContestParticipant} status transitions,
 * register / start / delete operations, and the read-then-write composite
 * that drives the lifecycle auto-finish.
 *
 * <p>These tests pin the seam contract independently of the lifecycle
 * and participation services so future callers can rely on the
 * interface without each test having to drive the full lifecycle.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestParticipantTransitions — deep seam")
class ContestParticipantTransitionsImplTest {

    private static final String CONTEST_ID = "c-1";
    private static final String USER_ID = "u-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 24, 10, 0);

    @Mock private ContestParticipantMapper mapper;
    private ContestParticipantTransitions seam;

    @BeforeEach
    void setUp() {
        seam = new ContestParticipantTransitionsImpl(mapper);
    }

    @Test
    @DisplayName("batchStartRegistered forwards to the atomic REGISTERED→STARTED SQL")
    void batchStartRegistered_atomicGuard() {
        when(mapper.startRegisteredParticipants(eq(CONTEST_ID), eq(NOW))).thenReturn(5);

        int n = seam.batchStartRegistered(CONTEST_ID, NOW);

        assertThat(n).isEqualTo(5);
        verify(mapper).startRegisteredParticipants(CONTEST_ID, NOW);
    }

    @Test
    @DisplayName("finishStartedReal forwards to the atomic STARTED+is_virtual=0 SQL")
    void finishStartedReal_atomicGuard() {
        when(mapper.finishStartedRealParticipants(eq(CONTEST_ID), eq(NOW))).thenReturn(7);

        int n = seam.finishStartedReal(CONTEST_ID, NOW);

        assertThat(n).isEqualTo(7);
        verify(mapper).finishStartedRealParticipants(CONTEST_ID, NOW);
    }

    @Test
    @DisplayName("bulkFinishVirtualByIds rejects null and empty collections without touching the mapper")
    void bulkFinishVirtualByIds_inputHygiene_noop() {
        assertThat(seam.bulkFinishVirtualByIds(null, NOW)).isZero();
        assertThat(seam.bulkFinishVirtualByIds(Collections.emptyList(), NOW)).isZero();
        verify(mapper, times(0)).finishStartedVirtualParticipantsByIds(any(), any());
    }

    @Test
    @DisplayName("bulkFinishVirtualByIds dedups before the SQL round-trip")
    void bulkFinishVirtualByIds_dedups() {
        when(mapper.finishStartedVirtualParticipantsByIds(any(), eq(NOW))).thenReturn(2);

        List<String> withDups = Arrays.asList("a", "a", "b", "a", "b");
        int n = seam.bulkFinishVirtualByIds(withDups, NOW);

        assertThat(n).isEqualTo(2);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Collection<String>> captor =
                ArgumentCaptor.forClass((Class) Collection.class);
        verify(mapper).finishStartedVirtualParticipantsByIds(captor.capture(), eq(NOW));
        assertThat(new HashSet<>(captor.getValue())).isEqualTo(new HashSet<>(Arrays.asList("a", "b")));
    }

    @Test
    @DisplayName("findAndFinishExpiredVirtuals reads then writes, applying the seam's input hygiene")
    void findAndFinishExpiredVirtuals_readThenWrite() {
        ContestParticipant v1 = makeVirtual("v-1");
        ContestParticipant v2 = makeVirtual("v-2");
        ContestParticipant v3 = makeVirtual("v-3");
        when(mapper.findVirtualParticipantsToFinish(NOW))
                .thenReturn(Arrays.asList(v1, v2, v3));
        when(mapper.finishStartedVirtualParticipantsByIds(any(), eq(NOW))).thenReturn(3);

        int n = seam.findAndFinishExpiredVirtuals(NOW);

        assertThat(n).isEqualTo(3);
        verify(mapper, times(1)).findVirtualParticipantsToFinish(NOW);
        verify(mapper, times(1)).finishStartedVirtualParticipantsByIds(
                argThat((Collection<String> ids) ->
                        ids.size() == 3 && ids.containsAll(Arrays.asList("v-1", "v-2", "v-3"))),
                eq(NOW));
    }

    @Test
    @DisplayName("findAndFinishExpiredVirtuals returns 0 when the query is empty")
    void findAndFinishExpiredVirtuals_emptyQuery() {
        when(mapper.findVirtualParticipantsToFinish(NOW)).thenReturn(Collections.emptyList());

        int n = seam.findAndFinishExpiredVirtuals(NOW);

        assertThat(n).isZero();
        verify(mapper, times(0)).finishStartedVirtualParticipantsByIds(any(), any());
    }

    @Test
    @DisplayName("registerRealParticipant forces canonical status + isVirtual=false")
    void registerRealParticipant_canonicalises() {
        ContestParticipant p = new ContestParticipant();
        p.setContestId(CONTEST_ID);
        p.setUserId(USER_ID);
        p.setStatus("WRONG_STATUS"); // a bad caller-supplied literal
        p.setIsVirtual(true);        // a bad caller-supplied flag

        seam.registerRealParticipant(p);

        assertThat(p.getStatus()).isEqualTo(ContestParticipantStatus.REGISTERED.wireValue());
        assertThat(p.getIsVirtual()).isFalse();
        verify(mapper).insert(p);
    }

    @Test
    @DisplayName("startVirtualParticipant forces canonical status + isVirtual=true")
    void startVirtualParticipant_canonicalises() {
        ContestParticipant p = new ContestParticipant();
        p.setContestId(CONTEST_ID);
        p.setUserId(USER_ID);
        p.setVirtualSessionId("sess-1");
        p.setStatus("WRONG_STATUS");
        p.setIsVirtual(false);

        seam.startVirtualParticipant(p);

        assertThat(p.getStatus()).isEqualTo(ContestParticipantStatus.STARTED.wireValue());
        assertThat(p.getIsVirtual()).isTrue();
        verify(mapper).insert(p);
    }

    @Test
    @DisplayName("deleteById delegates to the mapper and returns the affected-row count")
    void deleteById() {
        when(mapper.deleteById("p-1")).thenReturn(1);
        assertThat(seam.deleteById("p-1")).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteAllByContestId delegates to the mapper for the cascade path")
    void deleteAllByContestId() {
        when(mapper.deleteByContestId(CONTEST_ID)).thenReturn(11);
        assertThat(seam.deleteAllByContestId(CONTEST_ID)).isEqualTo(11);
    }

    @Test
    @DisplayName("findByContestIdsForReminder delegates to the mapper for the reminder fan-out")
    void findByContestIdsForReminder() {
        List<ContestParticipant> rows = Arrays.asList(makeReal("p-1"), makeReal("p-2"));
        when(mapper.findByContestIds(anyList())).thenReturn(rows);

        List<ContestParticipant> result =
                seam.findByContestIdsForReminder(Arrays.asList(CONTEST_ID, "c-2"));

        assertThat(result).hasSize(2);
    }

    private static ContestParticipant makeVirtual(String id) {
        ContestParticipant p = new ContestParticipant();
        p.setId(id);
        p.setContestId(CONTEST_ID);
        p.setIsVirtual(true);
        p.setStatus(ContestParticipantStatus.STARTED.wireValue());
        return p;
    }

    private static ContestParticipant makeReal(String id) {
        ContestParticipant p = new ContestParticipant();
        p.setId(id);
        p.setContestId(CONTEST_ID);
        p.setIsVirtual(false);
        p.setStatus(ContestParticipantStatus.REGISTERED.wireValue());
        return p;
    }
}
