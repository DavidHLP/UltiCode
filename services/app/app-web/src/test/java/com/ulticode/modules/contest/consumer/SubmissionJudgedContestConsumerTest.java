package com.ulticode.modules.contest.consumer;

import com.ulticode.submission.api.event.SubmissionJudgedEvent;
import com.ulticode.modules.contest.service.ContestAdjudicationService;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.entity.ContestSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubmissionJudgedContestConsumerTest {

    @Mock
    private ContestAdjudicationService adjudicationService;
    @Mock
    private ContestSubmissionMapper contestSubmissionMapper;

    private SubmissionJudgedContestConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SubmissionJudgedContestConsumer(adjudicationService, contestSubmissionMapper);
    }

    @Test
    void acceptedPayloadIsMappedToAdjudicationEvent() {
        org.mockito.Mockito.when(contestSubmissionMapper.findBySubmissionId("submission-1"))
                .thenReturn(Optional.of(new ContestSubmission()));
        consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", "100",
                "generation", 7,
                "verdict", "Accepted",
                "runtimeMs", "42",
                "memoryMb", "8.5",
                "contestId", "contest-1"));

        ArgumentCaptor<SubmissionJudgedEvent> captor =
                ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(adjudicationService).applyJudgeResult(captor.capture());
        SubmissionJudgedEvent event = captor.getValue();
        assertThat(event.getSubmissionId()).isEqualTo("submission-1");
        assertThat(event.getProblemId()).isEqualTo(100L);
        assertThat(event.getGeneration()).isEqualTo(7L);
        assertThat(event.getRuntimeMs()).isEqualTo(42);
        assertThat(event.getMemoryMb()).isEqualTo(8.5);
        assertThat(event.isAccepted()).isTrue();
        assertThat(event.getContestId()).isEqualTo("contest-1");
    }

    @Test
    void contestJudgedBeforeCreatedAssociationIsRetryable() {
        org.mockito.Mockito.when(contestSubmissionMapper.findBySubmissionId("submission-1"))
                .thenReturn(Optional.empty(), Optional.of(new ContestSubmission()));
        Map<String, Object> payload = Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", 100,
                "generation", 7,
                "verdict", "Accepted",
                "contestId", "contest-1");

        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not staged");
        verifyNoInteractions(adjudicationService);

        consumer.consume(payload);
        ArgumentCaptor<SubmissionJudgedEvent> captor = ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(adjudicationService).applyJudgeResult(captor.capture());
        assertThat(captor.getValue().getContestId()).isEqualTo("contest-1");
    }

    @Test
    void nonContestPayloadAcceptsNullProblemIdSentinel() {
        consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", "null",
                "generation", 7,
                "verdict", "Wrong Answer"));

        ArgumentCaptor<SubmissionJudgedEvent> captor =
                ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(adjudicationService).applyJudgeResult(captor.capture());
        assertThat(captor.getValue().getProblemId()).isNull();
    }

    @Test
    void infrastructureVerdictIsAcknowledgedWithoutContestScoring() {
        consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", 100,
                "generation", 7,
                "verdict", "Sandbox Error",
                "contestId", "contest-1",
                "runtimeMs", "invalid",
                "memoryMb", "invalid"));

        verifyNoInteractions(contestSubmissionMapper, adjudicationService);
    }

    @Test
    void malformedGenerationPropagatesBeforeContestReadsOrScoring() {
        Object[][] cases = {
                {null, "Missing"},
                {-1, "Negative"},
                {1.5d, "Invalid"},
                {BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), "Invalid"}
        };
        for (Object[] testCase : cases) {
            Map<String, Object> payload = new HashMap<>(Map.of(
                    "submissionId", "submission-1",
                    "userId", "user-1",
                    "verdict", "Accepted",
                    "contestId", "contest-1"));
            if (testCase[0] != null) {
                payload.put("generation", testCase[0]);
            }

            assertThatThrownBy(() -> consumer.consume(payload))
                    .as("generation %s", testCase[0])
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(testCase[1] + " SubmissionJudged field: generation");
        }
        verifyNoInteractions(contestSubmissionMapper, adjudicationService);
    }

    @Test
    void missingGenerationIsRejectedBeforeNonScoringVerdictsAreAcknowledged() {
        for (String verdict : new String[]{"Judging", "Sandbox Error"}) {
            assertThatThrownBy(() -> consumer.consume(Map.of(
                    "submissionId", "submission-1",
                    "userId", "user-1",
                    "verdict", verdict)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Missing SubmissionJudged field: generation")
                    .hasNoCause();
        }
        verifyNoInteractions(contestSubmissionMapper, adjudicationService);
    }

    @Test
    void zeroGenerationStillMapsToFirstAdjudicationGeneration() {
        consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "generation", 0,
                "verdict", "Accepted"));

        ArgumentCaptor<SubmissionJudgedEvent> captor = ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(adjudicationService).applyJudgeResult(captor.capture());
        assertThat(captor.getValue().getGeneration()).isEqualTo(1L);
        assertThat(captor.getValue().getProblemId()).isNull();
        assertThat(captor.getValue().getRuntimeMs()).isZero();
        assertThat(captor.getValue().getMemoryMb()).isZero();
        verifyNoInteractions(contestSubmissionMapper);
    }

    @Test
    void optionalProblemIdIsDecodedBeforeGenerationAndVerdict() {
        Map<String, Object> payload = Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", new BigDecimal("42.5"),
                "verdict", "Judging");

        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid SubmissionJudged problemId")
                .hasCauseInstanceOf(ArithmeticException.class);
        verifyNoInteractions(contestSubmissionMapper, adjudicationService);
    }

    @Test
    void scoringRejectsFractionalOrOutOfRangeRuntimeAndNonFiniteOrNegativeMemory() {
        Map<String, Object> payload = new HashMap<>(Map.of(
                "submissionId", "submission-1", "userId", "user-1",
                "generation", 7, "verdict", "Wrong Answer"));

        for (Object runtime : new Object[]{1.5, "1.0", -1, 2147483648L}) {
            payload.put("runtimeMs", runtime);
            assertThatThrownBy(() -> consumer.consume(payload))
                    .as("runtimeMs = %s", runtime)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        payload.put("runtimeMs", new BigDecimal("2147483647.00"));
        for (Object memory : new Object[]{-0.5, "Infinity", Double.NaN}) {
            payload.put("memoryMb", memory);
            assertThatThrownBy(() -> consumer.consume(payload))
                    .as("memoryMb = %s", memory)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid SubmissionJudged memoryMb");
        }
        verifyNoInteractions(contestSubmissionMapper, adjudicationService);
    }
}
