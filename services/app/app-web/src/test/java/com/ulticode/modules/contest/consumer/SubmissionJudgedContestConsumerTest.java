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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
                "runtimeMs", 42,
                "memoryMb", 8.5,
                "contestId", "contest-1"));

        ArgumentCaptor<SubmissionJudgedEvent> captor =
                ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(adjudicationService).applyJudgeResult(captor.capture());
        SubmissionJudgedEvent event = captor.getValue();
        assertThat(event.getSubmissionId()).isEqualTo("submission-1");
        assertThat(event.getProblemId()).isEqualTo(100L);
        assertThat(event.getGeneration()).isEqualTo(7L);
        assertThat(event.isAccepted()).isTrue();
        assertThat(event.getContestId()).isEqualTo("contest-1");
    }

    @Test
    void contestJudgedBeforeCreatedAssociationIsRetryable() {
        org.mockito.Mockito.when(contestSubmissionMapper.findBySubmissionId("submission-1"))
                .thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.consume(Map.of(
                "submissionId", "submission-1",
                "userId", "user-1",
                "problemId", 100,
                "generation", 7,
                "verdict", "Accepted",
                "contestId", "contest-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not staged");
        verify(adjudicationService, never()).applyJudgeResult(
                org.mockito.ArgumentMatchers.any(SubmissionJudgedEvent.class));
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
                "verdict", "Sandbox Error"));

        verify(adjudicationService, never()).applyJudgeResult(
                org.mockito.ArgumentMatchers.any(SubmissionJudgedEvent.class));
    }
}
