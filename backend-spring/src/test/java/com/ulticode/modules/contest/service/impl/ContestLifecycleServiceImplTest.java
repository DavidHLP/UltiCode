package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestProblemResultMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.FirstSolveRecordMapper;
import com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

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
 * Unit tests for {@link ContestLifecycleServiceImpl} — the contest-level
 * participant transition + cascade-cleanup seam split out of the old
 * ContestScoringService. Verifies the P0-2 batch start, the M2 bulk
 * auto-finish, and the P2-5 idempotent cascade delete.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestLifecycleServiceImpl (P0-2, M2, P2-5)")
class ContestLifecycleServiceImplTest {

    private static final String CONTEST_ID = "contest-1";

    @Mock private ContestMapper contestMapper;
    @Mock private ContestParticipantMapper contestParticipantMapper;
    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestSubmissionMapper contestSubmissionMapper;
    @Mock private ContestProblemResultMapper contestProblemResultMapper;
    @Mock private FirstSolveRecordMapper firstSolveRecordMapper;
    @Mock private ContestRankingCacheEvictor rankingCacheEvictor;
    @Mock private Clock clock;

    private ContestLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(java.time.ZoneId.of("UTC"));
        service = new ContestLifecycleServiceImpl(
                contestMapper, contestParticipantMapper, contestProblemMapper,
                contestSubmissionMapper, contestProblemResultMapper,
                firstSolveRecordMapper, rankingCacheEvictor, clock);
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

        // bulkFinishByIds is called once with the full id set; the count
        // returned by the mapper is the total transitioned.
        assertThat(total).isEqualTo(2);
        verify(contestParticipantMapper, times(1))
                .bulkFinishByIds(argThat(ids -> ids.contains("v-1") && ids.contains("v-2")),
                        any(LocalDateTime.class));
        // M2: no per-row batchUpdateStatus calls anymore.
        verify(contestParticipantMapper, never())
                .batchUpdateStatus(anyString(), anyString(), anyString(), any(LocalDateTime.class));
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

    private static ContestParticipant newParticipant(String id) {
        ContestParticipant p = new ContestParticipant();
        p.setId(id);
        p.setContestId(CONTEST_ID);
        p.setIsVirtual(true);
        return p;
    }
}
