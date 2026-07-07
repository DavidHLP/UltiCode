package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.ContestProblemResult;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.contest.entity.FirstSolveRecord;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestProblemResultMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.FirstSolveRecordMapper;
import com.ulticode.modules.submission.event.SubmissionJudgedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContestScoringServiceImpl}, covering the P0-1, P0-2,
 * P2-2, and P2-5 fixes from
 * {@code docs/contest-design-analysis-2026-06-16.md}.
 *
 * <p>Each test exercises one of the documented fixes against a mock mapper
 * graph so the contract is locked in independent of MyBatis / DB state.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestScoringServiceImpl (P0-1, P0-2, P2-2, P2-5 fixes)")
class ContestScoringServiceImplTest {

    private static final String CONTEST_ID = "contest-1";
    /** L2: explicit constant for the custom-penalty test (mirrors the schema
     *  default 300 in V20260602 baseline). */
    private static final int CUSTOM_PENALTY = 300;
    private static final String PROBLEM_ID = "cp-1";
    private static final String PARTICIPANT_ID = "p-1";
    private static final String USER_ID = "u-1";
    private static final String SUBMISSION_ID = "sub-1";
    private static final Long PROBLEM_LONG_ID = 100L;

    @Mock private ContestMapper contestMapper;
    @Mock private ContestParticipantMapper contestParticipantMapper;
    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestSubmissionMapper contestSubmissionMapper;
    @Mock private ContestProblemResultMapper contestProblemResultMapper;
    @Mock private FirstSolveRecordMapper firstSolveRecordMapper;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;
    @Mock private Clock clock;

    private ContestScoringServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(java.time.ZoneId.of("UTC"));
        service = new ContestScoringServiceImpl(
                contestMapper, contestParticipantMapper, contestProblemMapper,
                contestSubmissionMapper, contestProblemResultMapper,
                firstSolveRecordMapper, cacheManager, clock);
        // R4: the service now loads the parent contest for scoringMode/penalty
        // config. Provide a default ICPC contest so the existing tests (which
        // assume ICPC semantics: 20-min penalty per WA) continue to hold.
        Contest c = new Contest();
        c.setId(CONTEST_ID);
        c.setScoringMode("ICPC");
        c.setPenaltyPerWrong(20);
        when(contestMapper.selectById(CONTEST_ID)).thenReturn(c);
    }

    /** P0-1: apply judge result for a non-contest submission is a no-op. */
    @Test
    @DisplayName("P0-1: non-contest submission is a no-op (no mapper calls)")
    void applyJudgeResult_nonContestSubmission_noOp() {
        when(contestSubmissionMapper.findBySubmissionId(SUBMISSION_ID))
                .thenReturn(Optional.empty());
        SubmissionJudgedEvent event = newEvent(false, 100);

        service.applyJudgeResult(event);

        verify(contestSubmissionMapper, never()).markAcceptedBySubmissionId(anyString(), anyBoolean());
        verify(contestParticipantMapper, never()).updateById(any(ContestParticipant.class));
    }

    /** P0-1: AC writes is_accepted, increments score, creates cpr row, evicts cache. */
    @Test
    @DisplayName("P0-1: AC verdict writes is_accepted and increments totalScore")
    void applyJudgeResult_accepted_writesIsAcceptedAndIncrementsScore() {
        ContestParticipant participant = newParticipant(0, 0, 0);
        ContestProblem cp = newContestProblem(100);
        ContestSubmission cs = newContestSubmission(0);
        when(contestSubmissionMapper.findBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(cs));
        when(contestParticipantMapper.selectById(PARTICIPANT_ID)).thenReturn(participant);
        when(contestProblemMapper.selectById(PROBLEM_ID)).thenReturn(cp);
        when(contestProblemResultMapper.findByParticipantIdAndContestProblemId(PARTICIPANT_ID, PROBLEM_ID))
                .thenReturn(Optional.empty());
        when(firstSolveRecordMapper.insert(any(FirstSolveRecord.class))).thenReturn(1);

        SubmissionJudgedEvent event = newEvent(true, 600); // 10 min from start

        service.applyJudgeResult(event);

        // is_accepted flag written
        verify(contestSubmissionMapper).markAcceptedBySubmissionId(SUBMISSION_ID, true);
        // score = 100 (first AC) + 10 (first-solve bonus) = 110
        ArgumentCaptor<ContestParticipant> captor = ArgumentCaptor.forClass(ContestParticipant.class);
        verify(contestParticipantMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTotalScore()).isEqualTo(110);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        // first-solve record written
        verify(firstSolveRecordMapper, times(1)).insert(any(FirstSolveRecord.class));
        // aggregate counters incremented
        verify(contestMapper).incrementSubmissionCount(CONTEST_ID);
    }

    /** P0-1: WA verdict does not write is_accepted=true, but increments penalty + attempts. */
    @Test
    @DisplayName("P0-1: WA verdict increments penalty and attempts but not score")
    void applyJudgeResult_wrongAnswer_incrementsPenalty() {
        ContestParticipant participant = newParticipant(0, 0, 0);
        ContestProblem cp = newContestProblem(100);
        ContestSubmission cs = newContestSubmission(0);
        when(contestSubmissionMapper.findBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(cs));
        when(contestParticipantMapper.selectById(PARTICIPANT_ID)).thenReturn(participant);
        when(contestProblemMapper.selectById(PROBLEM_ID)).thenReturn(cp);
        when(contestSubmissionMapper.findByContestIdAndParticipantId(CONTEST_ID, PARTICIPANT_ID))
                .thenReturn(List.of(cs));

        SubmissionJudgedEvent event = newEvent(false, 200);

        service.applyJudgeResult(event);

        verify(contestSubmissionMapper).markAcceptedBySubmissionId(SUBMISSION_ID, false);
        ArgumentCaptor<ContestParticipant> captor = ArgumentCaptor.forClass(ContestParticipant.class);
        verify(contestParticipantMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTotalScore()).isEqualTo(0); // not incremented for WA
        assertThat(captor.getValue().getTotalPenalty()).isEqualTo(20); // ICPC default
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        verify(contestProblemResultMapper, never()).insert(any(ContestProblemResult.class)); // no result row on WA
    }

    /** P0-1: DuplicateKeyException on first_solve_records means we are NOT the first solver. */
    @Test
    @DisplayName("P0-1: first-solve race lost — no first_solve bonus applied")
    void applyJudgeResult_firstSolveRaceLost_noBonus() {
        ContestParticipant participant = newParticipant(0, 0, 0);
        ContestProblem cp = newContestProblem(100);
        ContestSubmission cs = newContestSubmission(0);
        when(contestSubmissionMapper.findBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(cs));
        when(contestParticipantMapper.selectById(PARTICIPANT_ID)).thenReturn(participant);
        when(contestProblemMapper.selectById(PROBLEM_ID)).thenReturn(cp);
        when(contestProblemResultMapper.findByParticipantIdAndContestProblemId(PARTICIPANT_ID, PROBLEM_ID))
                .thenReturn(Optional.empty());
        // First-solve insert hits the unique key — race lost.
        when(firstSolveRecordMapper.insert(any(FirstSolveRecord.class)))
                .thenThrow(new DuplicateKeyException("uk"));

        SubmissionJudgedEvent event = newEvent(true, 60);

        service.applyJudgeResult(event);

        // Score is 100 (AC), no first-solve bonus (no 110).
        ArgumentCaptor<ContestParticipant> captor = ArgumentCaptor.forClass(ContestParticipant.class);
        verify(contestParticipantMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTotalScore()).isEqualTo(100);
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

    /** M2: autoFinishVirtualParticipants queries, then issues a single
     *  bulk UPDATE keyed by the id set (was: per-row N+1). */
    @Test
    @DisplayName("M2: auto-finish virtual participants uses a single bulk UPDATE by ids")
    void autoFinishVirtualParticipants_processesQueryResults() {
        ContestParticipant v1 = newParticipant(0, 0, 0);
        v1.setId("v-1");
        v1.setContestId(CONTEST_ID);
        v1.setIsVirtual(true);
        ContestParticipant v2 = newParticipant(0, 0, 0);
        v2.setId("v-2");
        v2.setContestId(CONTEST_ID);
        v2.setIsVirtual(true);
        when(contestParticipantMapper.findVirtualParticipantsToFinish(any(LocalDateTime.class)))
                .thenReturn(List.of(v1, v2));
        when(contestParticipantMapper.bulkFinishByIds(any(), any(LocalDateTime.class)))
                .thenReturn(2);

        int total = service.autoFinishVirtualParticipants();

        // bulkFinishByIds is called once with the full id set; the
        // count returned by the mapper is the total transitioned.
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
    }

    /** P2-5: deleteContestCascade physically deletes 5 tables. */
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
    }

    // ---- R4: ADR-006 §4 validation: scoring mode + penalty config ----

    /** R4: SCORE mode — wrong submissions do NOT add penalty (AC-即满分). */
    @Test
    @DisplayName("R4 / ADR-006 §2.2: SCORE mode does not accumulate penalty on WA")
    void applyJudgeResult_scoreMode_waHasNoPenalty() {
        runWrongSubmissionWithContest(mockContest("SCORE", 20));
        ArgumentCaptor<ContestParticipant> captor = ArgumentCaptor.forClass(ContestParticipant.class);
        verify(contestParticipantMapper).updateById(captor.capture());
        // SCORE mode: totalPenalty stays 0 (was 0, stays 0 — no penalty added).
        assertThat(captor.getValue().getTotalPenalty()).isEqualTo(0);
        // attempts still increment.
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
    }

    /** R4: IOI mode — wrong submissions do NOT add penalty (取最高分，错误不计). */
    @Test
    @DisplayName("R4 / ADR-006 §2.2: IOI mode does not accumulate penalty on WA")
    void applyJudgeResult_ioiMode_waHasNoPenalty() {
        runWrongSubmissionWithContest(mockContest("IOI", 20));
        ArgumentCaptor<ContestParticipant> captor = ArgumentCaptor.forClass(ContestParticipant.class);
        verify(contestParticipantMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTotalPenalty()).isEqualTo(0);
    }

    /** R4 / ADR-006 §2.1: penaltyPerWrong=null falls back to 20, no NPE. */
    @Test
    @DisplayName("R4 / ADR-006 §2.1: penaltyPerWrong=null falls back to 20 in ICPC mode")
    void applyJudgeResult_icpcPenaltyNull_fallsBackTo20() {
        runWrongSubmissionWithContest(mockContest("ICPC", null));
        ArgumentCaptor<ContestParticipant> captor = ArgumentCaptor.forClass(ContestParticipant.class);
        verify(contestParticipantMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTotalPenalty()).isEqualTo(20);
    }

    /** R4 / ADR-006 §2.1: custom penaltyPerWrong is honored. */
    @Test
    @DisplayName("R4: custom penaltyPerWrong=CUSTOM_PENALTY is applied in ICPC mode")
    void applyJudgeResult_icpcCustomPenalty_applied() {
        runWrongSubmissionWithContest(mockContest("ICPC", CUSTOM_PENALTY));
        ArgumentCaptor<ContestParticipant> captor = ArgumentCaptor.forClass(ContestParticipant.class);
        verify(contestParticipantMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTotalPenalty()).isEqualTo(300);
    }

    // ---- M5: scoring-mode test helpers ----

    /** M5: build a Contest for the scoring test cases. */
    private Contest mockContest(String scoringMode, Integer penaltyPerWrong) {
        Contest c = new Contest();
        c.setId(CONTEST_ID);
        c.setScoringMode(scoringMode);
        c.setPenaltyPerWrong(penaltyPerWrong);
        when(contestMapper.selectById(CONTEST_ID)).thenReturn(c);
        return c;
    }

    /** M5: wire the standard WA submission mappers and invoke the service. */
    private void runWrongSubmissionWithContest(Contest contest) {
        ContestParticipant participant = newParticipant(0, 0, 0);
        ContestProblem cp = newContestProblem(100);
        ContestSubmission cs = newContestSubmission(0);
        when(contestSubmissionMapper.findBySubmissionId(SUBMISSION_ID)).thenReturn(Optional.of(cs));
        when(contestParticipantMapper.selectById(PARTICIPANT_ID)).thenReturn(participant);
        when(contestProblemMapper.selectById(PROBLEM_ID)).thenReturn(cp);
        when(contestSubmissionMapper.findByContestIdAndParticipantId(CONTEST_ID, PARTICIPANT_ID))
                .thenReturn(List.of(cs));
        service.applyJudgeResult(newEvent(false, 200));
    }

    // ---- helpers --------------------------------------------------------

    private static SubmissionJudgedEvent newEvent(boolean accepted, Integer runtimeSeconds) {
        return new SubmissionJudgedEvent(
                new Object(),
                SUBMISSION_ID,
                USER_ID,
                PROBLEM_LONG_ID,
                accepted ? "Accepted" : "Wrong Answer",
                accepted,
                runtimeSeconds,
                LocalDateTime.now());
    }

    private static ContestParticipant newParticipant(int score, int penalty, int attempts) {
        ContestParticipant p = new ContestParticipant();
        p.setId(PARTICIPANT_ID);
        p.setContestId(CONTEST_ID);
        p.setUserId(USER_ID);
        p.setStatus("STARTED");
        p.setTotalScore(score);
        p.setTotalPenalty(penalty);
        p.setAttemptCount(attempts);
        p.setTotalAttempts(attempts);
        p.setIsVirtual(false);
        return p;
    }

    private static ContestProblem newContestProblem(int score) {
        ContestProblem cp = new ContestProblem();
        cp.setId(PROBLEM_ID);
        cp.setContestId(CONTEST_ID);
        cp.setProblemId(PROBLEM_LONG_ID);
        cp.setProblemIndex("A");
        cp.setScore(score);
        return cp;
    }

    private static ContestSubmission newContestSubmission(int timeFromStart) {
        ContestSubmission cs = new ContestSubmission();
        cs.setId("cs-1");
        cs.setSubmissionId(SUBMISSION_ID);
        cs.setContestId(CONTEST_ID);
        cs.setContestProblemId(PROBLEM_ID);
        cs.setParticipantId(PARTICIPANT_ID);
        cs.setTimeFromStart(timeFromStart);
        cs.setIsAccepted(false);
        cs.setSubmittedAt(LocalDateTime.now());
        return cs;
    }
}
