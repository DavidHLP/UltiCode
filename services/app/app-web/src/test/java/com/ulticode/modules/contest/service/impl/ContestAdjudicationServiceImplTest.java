package com.ulticode.modules.contest.service.impl;

import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.ContestProblemResult;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.contest.entity.FirstSolveRecord;
import com.ulticode.modules.contest.mapper.ContestAdjudicationReceiptMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestProblemResultMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.FirstSolveRecordMapper;
import com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor;
import com.ulticode.submission.api.event.SubmissionJudgedEvent;
import com.ulticode.common.uuid.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContestAdjudicationServiceImpl} — the deep verdict
 * seam. Covers the P0-1 first-solve / idempotency contract and the R4 /
 * ADR-006 §2 scoring-mode + penalty behaviour. Each test exercises one
 * invariant against a mock mapper graph so the contract is locked in
 * independent of MyBatis / DB state.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestAdjudicationServiceImpl (P0-1 verdict seam, R4 scoring modes)")
class ContestAdjudicationServiceImplTest {

    private static final String CONTEST_ID = "contest-1";
    /** L2: explicit constant for the custom-penalty test (mirrors the schema default 300). */
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
    @Mock private ContestRankingCacheEvictor rankingCacheEvictor;
    @Mock private Clock clock;
    @Mock private com.ulticode.modules.contest.scoring.ScoringStrategyResolver scoringStrategyResolver;
    @Mock private ContestAdjudicationReceiptMapper receiptMapper;
    @Mock private UuidGenerator uuidGenerator;

    private ContestAdjudicationServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(java.time.ZoneId.of("UTC"));
        service = new ContestAdjudicationServiceImpl(
                contestMapper, contestParticipantMapper, contestProblemMapper,
                contestSubmissionMapper, contestProblemResultMapper,
                firstSolveRecordMapper, rankingCacheEvictor, clock,
                scoringStrategyResolver, receiptMapper, uuidGenerator);
        // R4: the service loads the parent contest for scoringMode/penalty
        // config. Provide a default ICPC contest so the existing tests (which
        // assume ICPC semantics: 20-min penalty per WA) continue to hold.
        Contest c = new Contest();
        c.setId(CONTEST_ID);
        c.setScoringMode("ICPC");
        c.setPenaltyPerWrong(20);
        when(contestMapper.selectById(CONTEST_ID)).thenReturn(c);
        lenient().when(scoringStrategyResolver.resolveFromString(
                org.mockito.ArgumentMatchers.argThat("ICPC"::equals)))
                .thenReturn(new com.ulticode.modules.contest.scoring.IcpcStrategy());
        lenient().when(scoringStrategyResolver.resolveFromString(
                org.mockito.ArgumentMatchers.argThat(s -> s == null || !"ICPC".equals(s))))
                .thenReturn(new com.ulticode.modules.contest.scoring.ScoreStrategy());
        lenient().when(contestMapper.selectByIdForUpdate(anyString()))
                .thenAnswer(invocation -> contestMapper.selectById(invocation.getArgument(0)));
        lenient().when(contestSubmissionMapper.findBySubmissionId(anyString()))
                .thenAnswer(invocation -> contestSubmissionMapper
                        .findBySubmissionIdForUpdate(invocation.getArgument(0)));
        lenient().when(receiptMapper.findMaxGenerationForSubmissionForUpdate(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(receiptMapper.insertIfAbsent(
                any(), anyString(), anyLong(), anyString(), anyBoolean())).thenReturn(1);
    }

    /** P0-1: apply judge result for a non-contest submission is a no-op. */
    @Test
    @DisplayName("P0-1: non-contest submission is a no-op (no mapper calls)")
    void applyJudgeResult_nonContestSubmission_noOp() {
        when(contestSubmissionMapper.findBySubmissionIdForUpdate(SUBMISSION_ID))
                .thenReturn(Optional.empty());
        SubmissionJudgedEvent event = newEvent(false, 100);

        service.applyJudgeResult(event);

        verify(contestSubmissionMapper, never()).markAcceptedBySubmissionId(anyString(), anyBoolean());
        verify(contestParticipantMapper, never()).updateById(any(ContestParticipant.class));
    }

    @Test
    @DisplayName("CONTEST-002: infrastructure verdict is not scored")
    void applyJudgeResult_infrastructureVerdict_isNoOp() {
        SubmissionJudgedEvent event = new SubmissionJudgedEvent(
                new Object(), SUBMISSION_ID, USER_ID, PROBLEM_LONG_ID,
                "Sandbox Error", false, null, LocalDateTime.now(),
                1L, 0, 0, CONTEST_ID);

        service.applyJudgeResult(event);

        verify(contestSubmissionMapper, never()).findBySubmissionIdForUpdate(anyString());
        verify(receiptMapper, never()).insertIfAbsent(
                any(), anyString(), anyLong(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("CONTEST-002: duplicate receipt does not replay scoring")
    void applyJudgeResult_duplicateReceipt_isNoOp() {
        ContestSubmission cs = newContestSubmission(0);
        when(contestSubmissionMapper.findBySubmissionIdForUpdate(SUBMISSION_ID))
                .thenReturn(Optional.of(cs));
        when(receiptMapper.findMaxGenerationForSubmissionForUpdate(SUBMISSION_ID))
                .thenReturn(Optional.of(1L));

        service.applyJudgeResult(newEvent(true, 100, 1L));

        verify(receiptMapper, never()).insertIfAbsent(
                any(), anyString(), anyLong(), anyString(), anyBoolean());
        verify(contestSubmissionMapper, never()).markAcceptedBySubmissionId(anyString(), anyBoolean());
        verify(contestParticipantMapper, never()).selectByIdForUpdate(anyString());
    }

    @Test
    @DisplayName("CONTEST-002: stale generation does not overwrite newer receipt")
    void applyJudgeResult_staleGeneration_isNoOp() {
        ContestSubmission cs = newContestSubmission(0);
        when(contestSubmissionMapper.findBySubmissionIdForUpdate(SUBMISSION_ID))
                .thenReturn(Optional.of(cs));
        when(receiptMapper.findMaxGenerationForSubmissionForUpdate(SUBMISSION_ID))
                .thenReturn(Optional.of(2L));

        service.applyJudgeResult(newEvent(true, 100, 1L));

        verify(receiptMapper, never()).insertIfAbsent(
                any(), anyString(), anyLong(), anyString(), anyBoolean());
        verify(contestSubmissionMapper, never()).markAcceptedBySubmissionId(anyString(), anyBoolean());
    }

    /** P0-1: AC writes is_accepted, increments score, creates cpr row, evicts cache. */
    @Test
    @DisplayName("P0-1: AC verdict writes is_accepted and increments totalScore")
    void applyJudgeResult_accepted_writesIsAcceptedAndIncrementsScore() {
        ContestParticipant participant = newParticipant(0, 0, 0);
        ContestProblem cp = newContestProblem(100);
        ContestSubmission cs = newContestSubmission(600);
        when(contestSubmissionMapper.findBySubmissionIdForUpdate(SUBMISSION_ID)).thenReturn(Optional.of(cs));
        when(contestParticipantMapper.selectByIdForUpdate(PARTICIPANT_ID)).thenReturn(participant);
        when(contestProblemMapper.selectById(PROBLEM_ID)).thenReturn(cp);
        when(contestProblemResultMapper.findByParticipantIdAndContestProblemIdForUpdate(PARTICIPANT_ID, PROBLEM_ID))
                .thenReturn(Optional.empty());
        when(firstSolveRecordMapper.insert(any(FirstSolveRecord.class))).thenReturn(1);

        SubmissionJudgedEvent event = newEvent(true, 600, 1L, "spoofed-user"); // 10 min from start

        service.applyJudgeResult(event);

        // is_accepted flag written
        verify(contestSubmissionMapper).markAcceptedBySubmissionId(SUBMISSION_ID, true);
        // score = 100 (first AC) + 10 (first-solve bonus) = 110
        ArgumentCaptor<ContestParticipant> captor = ArgumentCaptor.forClass(ContestParticipant.class);
        verify(contestParticipantMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTotalScore()).isEqualTo(110);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(captor.getValue().getTotalTime()).isEqualTo(600);
        assertThat(captor.getValue().getLastSolveTime()).isEqualTo(600);
        // first-solve record uses the locked participant's owner, not event payload data
        ArgumentCaptor<FirstSolveRecord> firstSolveCaptor = ArgumentCaptor.forClass(FirstSolveRecord.class);
        verify(firstSolveRecordMapper, times(1)).insert(firstSolveCaptor.capture());
        assertThat(firstSolveCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        // aggregate counters incremented
        verify(contestMapper).incrementSubmissionCount(CONTEST_ID);
        // ranking cache evicted so the fresh aggregate is visible
        verify(rankingCacheEvictor).evictRankingCache();
    }

    @Test
    @DisplayName("CONTEST-002: delayed submissions still increment participant count on first adjudication")
    void applyJudgeResult_firstAdjudicationWithMultipleRows_incrementsParticipantCount() {
        ContestParticipant participant = newParticipant(0, 0, 0);
        ContestProblem cp = newContestProblem(100);
        ContestSubmission cs = newContestSubmission(0);
        when(contestSubmissionMapper.findBySubmissionIdForUpdate(SUBMISSION_ID)).thenReturn(Optional.of(cs));
        when(contestParticipantMapper.selectByIdForUpdate(PARTICIPANT_ID)).thenReturn(participant);
        when(contestProblemMapper.selectById(PROBLEM_ID)).thenReturn(cp);
        when(contestProblemResultMapper.findByParticipantIdAndContestProblemIdForUpdate(PARTICIPANT_ID, PROBLEM_ID))
                .thenReturn(Optional.empty());
        when(firstSolveRecordMapper.insert(any(FirstSolveRecord.class))).thenReturn(1);
        // Two already-submitted rows must not hide that this is the first
        // adjudicated attempt for the locked participant.
        when(contestSubmissionMapper.findByContestIdAndParticipantId(CONTEST_ID, PARTICIPANT_ID))
                .thenReturn(List.of(newContestSubmission(0), newContestSubmission(1)));

        service.applyJudgeResult(newEvent(true, 60));

        verify(contestMapper).incrementParticipantCount(CONTEST_ID);
    }

    /** P0-1: WA verdict does not write is_accepted=true, but increments penalty + attempts. */
    @Test
    @DisplayName("P0-1: WA verdict increments penalty and attempts but not score")
    void applyJudgeResult_wrongAnswer_increasesPenalty() {
        ContestParticipant participant = newParticipant(0, 0, 0);
        ContestProblem cp = newContestProblem(100);
        ContestSubmission cs = newContestSubmission(0);
        when(contestSubmissionMapper.findBySubmissionIdForUpdate(SUBMISSION_ID)).thenReturn(Optional.of(cs));
        when(contestParticipantMapper.selectByIdForUpdate(PARTICIPANT_ID)).thenReturn(participant);
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
        when(contestSubmissionMapper.findBySubmissionIdForUpdate(SUBMISSION_ID)).thenReturn(Optional.of(cs));
        when(contestParticipantMapper.selectByIdForUpdate(PARTICIPANT_ID)).thenReturn(participant);
        when(contestProblemMapper.selectById(PROBLEM_ID)).thenReturn(cp);
        when(contestProblemResultMapper.findByParticipantIdAndContestProblemIdForUpdate(PARTICIPANT_ID, PROBLEM_ID))
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

    // ---- helpers --------------------------------------------------------

    private Contest mockContest(String scoringMode, Integer penaltyPerWrong) {
        Contest c = new Contest();
        c.setId(CONTEST_ID);
        c.setScoringMode(scoringMode);
        c.setPenaltyPerWrong(penaltyPerWrong);
        when(contestMapper.selectById(CONTEST_ID)).thenReturn(c);
        return c;
    }

    private void runWrongSubmissionWithContest(Contest contest) {
        ContestParticipant participant = newParticipant(0, 0, 0);
        ContestProblem cp = newContestProblem(100);
        ContestSubmission cs = newContestSubmission(0);
        when(contestSubmissionMapper.findBySubmissionIdForUpdate(SUBMISSION_ID)).thenReturn(Optional.of(cs));
        when(contestParticipantMapper.selectByIdForUpdate(PARTICIPANT_ID)).thenReturn(participant);
        when(contestProblemMapper.selectById(PROBLEM_ID)).thenReturn(cp);
        when(contestSubmissionMapper.findByContestIdAndParticipantId(CONTEST_ID, PARTICIPANT_ID))
                .thenReturn(List.of(cs));
        service.applyJudgeResult(newEvent(false, 200));
    }

    private static SubmissionJudgedEvent newEvent(boolean accepted, Integer runtimeSeconds) {
        return newEvent(accepted, runtimeSeconds, 1L);
    }

    private static SubmissionJudgedEvent newEvent(
            boolean accepted, Integer runtimeSeconds, long generation) {
        return newEvent(accepted, runtimeSeconds, generation, USER_ID);
    }

    private static SubmissionJudgedEvent newEvent(
            boolean accepted, Integer runtimeSeconds, long generation, String userId) {
        return new SubmissionJudgedEvent(
                new Object(),
                SUBMISSION_ID,
                userId,
                PROBLEM_LONG_ID,
                accepted ? "Accepted" : "Wrong Answer",
                accepted,
                runtimeSeconds,
                LocalDateTime.now(),
                generation,
                0,
                0,
                CONTEST_ID);
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
