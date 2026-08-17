package com.ulticode.modules.submission.created;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.submission.result.ResultEventPublisher;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionCreatedDispatcherTest {

    @Mock
    private SubmissionCreatedOutboxMapper outboxMapper;
    @Mock
    private ResultEventPublisher eventPublisher;

    @Test
    void publishesCreatedEventBeforeMarkingDelivered() {
        SubmissionCreatedOutboxRecord record = record();
        when(outboxMapper.claimPending(anyString(), eq(50))).thenReturn(1);
        when(outboxMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(record));
        when(eventPublisher.publish(eq("created-1"), eq("Submission"),
                eq("SubmissionCreated"), eq("submission-1"), eq(2L), anyMap()))
                .thenReturn("1-0");
        when(outboxMapper.markDelivered(anyString(), anyString())).thenReturn(1);

        SubmissionCreatedDispatcher dispatcher =
                new SubmissionCreatedDispatcher(outboxMapper, eventPublisher);

        assertThat(dispatcher.dispatch()).isEqualTo(1);

        ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
        InOrder order = inOrder(eventPublisher, outboxMapper);
        order.verify(eventPublisher).publish(eq("created-1"), eq("Submission"),
                eq("SubmissionCreated"), eq("submission-1"), eq(2L), payload.capture());
        order.verify(outboxMapper).markDelivered(eq("created-1"), anyString());
        verify(outboxMapper, never()).markFailed(any(), anyString(), any(), anyInt());
        assertThat(payload.getValue())
                .containsEntry("contestId", "contest-1")
                .containsEntry("virtualSessionId", "session-1")
                .doesNotContainKey("code");
    }

    @Test
    void publicationFailureLeavesCreatedEventRetryable() {
        when(outboxMapper.claimPending(anyString(), eq(50))).thenReturn(1);
        when(outboxMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(record()));
        when(eventPublisher.publish(anyString(), anyString(), anyString(), anyString(), any(Long.class), anyMap()))
                .thenThrow(new IllegalStateException("stream unavailable"));

        SubmissionCreatedDispatcher dispatcher =
                new SubmissionCreatedDispatcher(outboxMapper, eventPublisher);

        assertThat(dispatcher.dispatch()).isZero();
        verify(outboxMapper).markFailed(eq("created-1"), anyString(),
                eq("stream unavailable"), eq(5));
        verify(outboxMapper, never()).markDelivered(any(), anyString());
    }

    private static SubmissionCreatedOutboxRecord record() {
        SubmissionCreatedOutboxRecord record = new SubmissionCreatedOutboxRecord();
        record.setId("created-1");
        record.setSubmissionId("submission-1");
        record.setGeneration(2L);
        record.setUserId("user-1");
        record.setProblemId("42");
        record.setContestId("contest-1");
        record.setVirtualSessionId("session-1");
        record.setLanguage("java");
        record.setOccurredAt(LocalDateTime.of(2026, 8, 17, 1, 2));
        record.setState("CLAIMED");
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }
}
