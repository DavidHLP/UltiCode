package com.ulticode.modules.queue.processor;

import com.ulticode.app.api.service.JudgeFeatureFlagsPort;
import com.ulticode.app.api.service.SubmissionFencePort;
import com.ulticode.app.api.service.SubmissionWritePort;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.domain.submission.enums.CaseScope;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.pipeline.JudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.JudgeExecutionResult;
import com.ulticode.modules.queue.port.JudgeJobEnvelope;
import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.submission.entity.Submission;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultJudgeAttemptExecutorTest {

    @Mock
    private SubmissionWritePort submissionWritePort;


    @Mock
    private JudgeExecutionPipeline executionPipeline;

    @Mock
    private SubmissionFencePort submissionFencePort;

    @Mock
    private JudgeFeatureFlagsPort featureFlags;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter leaseMissCounter;

    @Mock
    private UuidGenerator uuidGenerator;

    @Mock
    private ScheduledExecutorService heartbeatExecutor;

    @Mock
    private ScheduledFuture<?> heartbeatTask;

    @Mock
    private JudgeQueue judgeQueue;

    private final AtomicReference<Runnable> heartbeat = new AtomicReference<>();
    private DefaultJudgeAttemptExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DefaultJudgeAttemptExecutor(
                submissionWritePort,
                executionPipeline,
                submissionFencePort,
                featureFlags,
                meterRegistry,
                uuidGenerator);
        setHeartbeatExecutor();
        lenient().when(uuidGenerator.newId()).thenReturn("attempt-1");
        when(featureFlags.isUseGenerationFence()).thenReturn(true);
        lenient().when(submissionFencePort.currentGeneration("submission-1")).thenReturn(Optional.of(3L));
        lenient().when(submissionFencePort.acquireLease("submission-1", "attempt-1", 3L, 60L))
                .thenReturn(true);
    }

    @Test
    @DisplayName("fenced verdict serializes details and writes through the fenced port")
    void fencedVerdictWritesDetailsBeforeNotification() throws Exception {
        Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
        detail.setCaseId("case-1");
        detail.setStatus("Accepted");
        detail.setCaseScope(CaseScope.SAMPLE);
        JudgeExecutionResult result = new JudgeExecutionResult(SubmissionStatus.ACCEPTED, 37, 12.5,
                List.of(detail));
        when(executionPipeline.execute("java", "class Main {}", 100L, "user-1", "submission-1"))
                .thenReturn(result);
        when(submissionWritePort.updateSubmissionResultFenced(
                eq("submission-1"), eq(SubmissionStatus.ACCEPTED), eq(37), eq(12.5), any(String.class),
                eq(3L), eq("attempt-1"))).thenReturn(true);

        executor.runAttempt(job(), null, null);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        InOrder sideEffects = inOrder(submissionWritePort);
        sideEffects.verify(submissionWritePort).updateSubmissionResultFenced(
                eq("submission-1"), eq(SubmissionStatus.ACCEPTED), eq(37), eq(12.5), details.capture(),
                eq(3L), eq("attempt-1"));
        assertThat(details.getValue()).contains("\"caseId\":\"case-1\"");
        verify(heartbeatTask).cancel(false);
    }

    @Test
    @DisplayName("stale fenced verdict is dropped without notification")
    void staleVerdictDoesNotNotify() throws Exception {
        JudgeExecutionResult result = new JudgeExecutionResult(SubmissionStatus.WRONG_ANSWER, 12, 1.0, List.of());
        when(executionPipeline.execute("java", "class Main {}", 100L, "user-1", "submission-1"))
                .thenReturn(result);
        when(submissionWritePort.updateSubmissionResultFenced(
                eq("submission-1"), eq(SubmissionStatus.WRONG_ANSWER), eq(12), eq(1.0), any(),
                eq(3L), eq("attempt-1"))).thenReturn(false);

        executor.runAttempt(job(), null, null);

    }

    @Test
    @DisplayName("v2 envelope metadata is used for the lease and fenced verdict")
    void v2EnvelopeMetadataIsPreserved() throws Exception {
        JudgeExecutionResult result = new JudgeExecutionResult(SubmissionStatus.ACCEPTED, 2, 1.0, List.of());
        when(executionPipeline.execute("java", "class Main {}", 100L, "user-1", "submission-1"))
                .thenReturn(result);
        when(submissionFencePort.currentGeneration("submission-1")).thenReturn(Optional.of(7L));
        when(submissionFencePort.acquireLease("submission-1", "dispatch-attempt", 7L, 60L))
                .thenReturn(true);
        when(submissionWritePort.updateSubmissionResultFenced(
                eq("submission-1"), eq(SubmissionStatus.ACCEPTED), eq(2), eq(1.0), any(),
                eq(7L), eq("dispatch-attempt"))).thenReturn(true);

        JudgeJobEnvelope envelope = new JudgeJobEnvelope(
                JudgeJobEnvelope.VERSION_2, "outbox-1", "submission-1", "100", "user-1",
                "java", "class Main {}", 2000, 262144, 7L, "dispatch-attempt");
        executor.runAttempt(job(), judgeQueue, new JudgeJobHandle(envelope, "ack-token"));

        verify(uuidGenerator, never()).newId();
        verify(submissionFencePort).acquireLease("submission-1", "dispatch-attempt", 7L, 60L);
        verify(submissionWritePort).updateSubmissionResultFenced(
                eq("submission-1"), eq(SubmissionStatus.ACCEPTED), eq(2), eq(1.0), any(),
                eq(7L), eq("dispatch-attempt"));
        verify(judgeQueue).ack(any(JudgeJobHandle.class));
    }

    @Test
    @DisplayName("flag-off execution uses the unfenced write path and still serializes details")
    void legacyFlagUsesUnfencedWrite() throws Exception {
        when(featureFlags.isUseGenerationFence()).thenReturn(false);
        Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
        detail.setCaseId("legacy-case");
        detail.setStatus("Accepted");
        JudgeExecutionResult result = new JudgeExecutionResult(SubmissionStatus.ACCEPTED, 5, 2.0,
                List.of(detail));
        when(executionPipeline.execute("java", "class Main {}", 100L, "user-1", "submission-1"))
                .thenReturn(result);

        executor.runAttempt(job(), null, null);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        InOrder legacyWrites = inOrder(submissionWritePort);
        legacyWrites.verify(submissionWritePort).updateSubmissionResult(
                "submission-1", SubmissionStatus.JUDGING, 0, 0.0, null);
        legacyWrites.verify(submissionWritePort).updateSubmissionResult(
                eq("submission-1"), eq(SubmissionStatus.ACCEPTED), eq(5), eq(2.0), details.capture());
        assertThat(details.getValue()).contains("\"caseId\":\"legacy-case\"");
        verify(submissionWritePort, never()).updateSubmissionResultFenced(
                any(), any(), anyInt(), any(), any(), anyLong(), any());
        verify(submissionFencePort, never()).currentGeneration(any());
    }

    @Test
    @DisplayName("rejected lease skips execution and durable writes")
    void rejectedLeaseSkipsExecution() throws Exception {
        when(submissionFencePort.acquireLease("submission-1", "attempt-1", 3L, 60L)).thenReturn(false);

        executor.runAttempt(job(), null, null);

        verify(executionPipeline, never()).execute(any(), any(), anyLong(), any(), any());
        verify(submissionWritePort, never()).updateSubmissionResultFenced(
                any(), any(), anyInt(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("heartbeat renews the lease and cancels itself after a lease loss")
    void heartbeatStopsAfterLeaseLoss() throws Exception {
        JudgeExecutionResult result = new JudgeExecutionResult(SubmissionStatus.ACCEPTED, 1, 1.0, List.of());
        when(executionPipeline.execute("java", "class Main {}", 100L, "user-1", "submission-1"))
                .thenReturn(result);
        when(submissionWritePort.updateSubmissionResultFenced(
                eq("submission-1"), eq(SubmissionStatus.ACCEPTED), eq(1), eq(1.0), any(),
                eq(3L), eq("attempt-1"))).thenReturn(true);
        when(meterRegistry.counter("judge.lease.miss_renew")).thenReturn(leaseMissCounter);
        when(submissionFencePort.renewLease("submission-1", "attempt-1", 60L))
                .thenReturn(true, false);

        executor.runAttempt(job(), null, null);
        assertThat(heartbeat.get()).isNotNull();

        heartbeat.get().run();
        heartbeat.get().run();
        verify(submissionFencePort, times(2)).renewLease("submission-1", "attempt-1", 60L);

        verify(heartbeatTask, times(2)).cancel(false);
        verify(leaseMissCounter).increment();
    }

    private void setHeartbeatExecutor() {
        try {
            var field = DefaultJudgeAttemptExecutor.class.getDeclaredField("heartbeatExecutor");
            field.setAccessible(true);
            field.set(executor, heartbeatExecutor);
            lenient().when(heartbeatExecutor.scheduleAtFixedRate(
                    any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenAnswer(invocation -> {
                        heartbeat.set(invocation.getArgument(0));
                        return heartbeatTask;
                    });
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inject heartbeat test executor", e);
        }
    }

    private JudgeJob job() {
        return JudgeJob.builder()
                .submissionId("submission-1")
                .problemId("100")
                .userId("user-1")
                .language("java")
                .code("class Main {}")
                .build();
    }
}
