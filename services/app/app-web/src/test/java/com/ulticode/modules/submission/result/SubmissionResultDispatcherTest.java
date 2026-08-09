package com.ulticode.modules.submission.result;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.event.outbox.IntegrationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Submission result durable dispatcher")
class SubmissionResultDispatcherTest {

    @Mock
    private SubmissionResultOutboxMapper resultMapper;

    @Mock
    private IntegrationEventPublisher integrationEventPublisher;

    @Test
    @DisplayName("publishes the full verdict envelope before marking the result delivered")
    void publishesVerdictBeforeMarkingDelivered() {
        SubmissionResultOutboxRecord record = resultRecord("result-1", "submission-1", 7L);
        when(resultMapper.claimPending(anyString(), eq(50))).thenReturn(1);
        when(resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(record));
        when(integrationEventPublisher.publishWithId(
                eq("result-1"), eq("App"), eq("SubmissionJudged"), eq("submission-1"), eq(7L),
                isNull(String.class), isNull(String.class), anyMap()))
                .thenReturn("result-1");
        when(resultMapper.markDelivered(anyString(), anyString())).thenReturn(1);

        SubmissionResultDispatcher dispatcher =
                new SubmissionResultDispatcher(resultMapper, integrationEventPublisher);

        assertThat(dispatcher.dispatch()).isEqualTo(1);

        ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        InOrder order = inOrder(integrationEventPublisher, resultMapper);
        order.verify(integrationEventPublisher).publishWithId(
                eq("result-1"), eq("App"), eq("SubmissionJudged"), eq("submission-1"), eq(7L),
                isNull(String.class), isNull(String.class), payloadCaptor.capture());
        order.verify(resultMapper).markDelivered(eq("result-1"), anyString());
        verify(resultMapper, never()).markFailed(any(), anyString(), any(), anyInt());

        Map<?, ?> payload = payloadCaptor.getValue();
        assertThat(payload.get("submissionId")).isEqualTo("submission-1");
        assertThat(payload.get("generation")).isEqualTo(7L);
        assertThat(payload.get("userId")).isEqualTo("user-1");
        assertThat(payload.get("problemId")).isEqualTo("problem-1");
        assertThat(payload.get("verdict")).isEqualTo("ACCEPTED");
        assertThat(payload.get("runtimeMs")).isEqualTo(120);
        assertThat(payload.get("memoryMb")).isEqualTo(4.5);
        assertThat(payload.get("contestId")).isEqualTo("contest-1");
    }

    @Test
    @DisplayName("keeps the result retryable when durable publication fails")
    void publicationFailureMarksResultRetryable() {
        SubmissionResultOutboxRecord record = resultRecord("result-2", "submission-2", 3L);
        when(resultMapper.claimPending(anyString(), eq(50))).thenReturn(1);
        when(resultMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(record));
        when(integrationEventPublisher.publishWithId(
                eq("result-2"), eq("App"), eq("SubmissionJudged"), eq("submission-2"), eq(3L),
                isNull(String.class), isNull(String.class), anyMap()))
                .thenThrow(new IllegalStateException("integration outbox unavailable"));

        SubmissionResultDispatcher dispatcher =
                new SubmissionResultDispatcher(resultMapper, integrationEventPublisher);

        assertThat(dispatcher.dispatch()).isZero();

        verify(resultMapper).markFailed(
                eq("result-2"), anyString(), eq("integration outbox unavailable"), eq(5));
        verify(resultMapper, never()).markDelivered(any(), anyString());
    }

    @Test
    @DisplayName("reclaims stale claims before taking a new result batch")
    void reclaimsStaleClaimsBeforeClaiming() {
        when(resultMapper.reclaimStaleClaimed()).thenReturn(2);
        when(resultMapper.claimPending(anyString(), eq(50))).thenReturn(0);

        SubmissionResultDispatcher dispatcher =
                new SubmissionResultDispatcher(resultMapper, integrationEventPublisher);

        assertThat(dispatcher.dispatch()).isZero();

        InOrder order = inOrder(resultMapper);
        order.verify(resultMapper).reclaimStaleClaimed();
        order.verify(resultMapper).claimPending(anyString(), eq(50));
        verify(resultMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }


    private static SubmissionResultOutboxRecord resultRecord(String id, String submissionId,
                                                               long generation) {
        SubmissionResultOutboxRecord record = new SubmissionResultOutboxRecord();
        record.setId(id);
        record.setSubmissionId(submissionId);
        record.setGeneration(generation);
        record.setUserId("user-1");
        record.setProblemId("problem-1");
        record.setVerdict("ACCEPTED");
        record.setRuntimeMs(120);
        record.setMemoryMb(4.5);
        record.setContestId("contest-1");
        record.setState("CLAIMED");
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }
}
