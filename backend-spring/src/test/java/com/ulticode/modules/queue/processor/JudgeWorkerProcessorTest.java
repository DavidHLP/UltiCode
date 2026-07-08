package com.ulticode.modules.queue.processor;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.pipeline.JudgeExecutionPipeline;
import com.ulticode.modules.queue.pipeline.JudgeExecutionResult;
import com.ulticode.modules.queue.port.JudgeQueue;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.queue.port.SubmissionResultPushPort;
import com.ulticode.modules.websocket.contest.dto.SubmissionResultPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Worker-level tests after the arch-review deepening.
 *
 * <p>The execution path (load test cases → sandbox dispatch → verdict resolution
 * → DTO building) now lives in {@link JudgeExecutionPipeline}. This test
 * therefore mocks the pipeline rather than the individual execution
 * collaborators; pipeline-internal coverage lives in
 * {@code DefaultJudgeExecutionPipelineTest}.
 *
 * <p>Tests cover the worker contract only:
 * <ul>
 *   <li>Polling + dispatching the pipeline</li>
 *   <li>Persisting results via {@link SubmissionService}</li>
 *   <li>Pushing {@link SubmissionResultPayload} via the push port</li>
 *   <li>The fenced path (lease acquire / renew / heartbeat)</li>
 *   <li>Exception handling (pipeline exception → System Error)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JudgeWorkerProcessor")
class JudgeWorkerProcessorTest {

    @Mock
    private QueueService queueService;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private SubmissionResultPushPort submissionResultPushPort;

    @Mock
    private ContestSubmissionMapper contestSubmissionMapper;

    /**
     * Arch-review deepening seam: the worker delegates all execution
     * (test-case loading, sandbox dispatch, verdict resolution, DTO
     * building) here. Pipeline-internal behaviour is covered by
     * {@code DefaultJudgeExecutionPipelineTest}; this test stubs the
     * pipeline's return value and asserts the worker correctly
     * persists + pushes the result.
     */
    @Mock
    private JudgeExecutionPipeline executionPipeline;

    @Mock
    private QueueConfig queueConfig;

    /**
     * ADR-003 M3b: mapper for the lease CAS. Mocked so the legacy path
     * (flag-off) is exercised — processJob guards on the flag and never reaches
     * acquireLease when {@link #featureFlags} is flag-off.
     */
    @Mock
    private com.ulticode.modules.submission.mapper.SubmissionMapper submissionMapper;

    /**
     * ADR-003 M3b: flag-off properties so {@code processJob} takes the legacy
     * branch. Declared as a {@link Spy} of a real instance so Mockito injects
     * it via constructor ({@link InjectMocks} only picks up {@link Mock}/{@link Spy}
     * fields, per the mockito5-lombok-constructor-injection rule).
     */
    @Spy
    private com.ulticode.modules.submission.config.FeatureFlagsProperties featureFlags =
            new com.ulticode.modules.submission.config.FeatureFlagsProperties();

    /**
     * ADR-003 M3b: metrics registry mock; never invoked on the flag-off path.
     */
    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    /**
     * ADR-003 M3c-3a: provider for the {@link JudgeQueue} port. M3a/M3b
     * resolves to null; tests in this class don't exercise the port path,
     * so we don't stub the provider to return anything.
     */
    @Mock
    private ObjectProvider<JudgeQueue> judgeQueueProvider;

    @Mock
    private Clock clock;

    @Mock
    private com.ulticode.common.uuid.UuidGenerator uuidGenerator;

    @InjectMocks
    private JudgeWorkerProcessor processor;

    @Captor
    private ArgumentCaptor<SubmissionResultPayload> payloadCaptor;

    @Captor
    private ArgumentCaptor<List<Submission.TestCaseDetail>> detailsCaptor;

    private JudgeJob sampleJob;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(Instant.now());
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        // Inject a deterministic UuidGenerator for the @InjectMocks field
        // (the field is @Mock which defaults to returning null).
        org.springframework.test.util.ReflectionTestUtils.setField(processor, "uuidGenerator",
                new com.ulticode.common.uuid.FixedUuidGenerator());
        sampleJob = JudgeJob.create("sub-1", "100", "user-1", "javascript", "console.log('hello');", clock,
                new com.ulticode.common.uuid.FixedUuidGenerator());
        lenient().when(queueConfig.getMaxConcurrentJobs()).thenReturn(10);
        lenient().when(contestSubmissionMapper.selectOne(any())).thenReturn(null);
    }

    // === getJobType ===

    @Test
    @DisplayName("getJobType returns JUDGE_QUEUE")
    void getJobType_returnsJudgeQueue() {
        assertThat(processor.getJobType()).isEqualTo("judge_queue");
    }

    // === pollAndProcess ===

    @Nested
    @DisplayName("pollAndProcess")
    class PollAndProcess {

        @Test
        @DisplayName("does nothing when queue is empty")
        void emptyQueue_doesNothing() throws Exception {
            when(queueService.pollJob("judge_queue")).thenReturn(null);

            processor.pollAndProcess();

            verify(submissionService, never()).updateSubmissionResult(anyString(), anyString(),
                    anyInt(), any(), any());
            verify(executionPipeline, never()).execute(anyString(), anyString(), anyLong(),
                    anyString(), anyString());
        }

        @Test
        @DisplayName("processes JudgeJob: delegates to pipeline, persists, pushes")
        void withJudgeJob_processesAndDecrementsActiveJobs() throws Exception {
            when(queueService.pollJob("judge_queue")).thenReturn(sampleJob);
            when(executionPipeline.execute(eq("javascript"), eq("console.log('hello');"),
                    eq(100L), eq("user-1"), eq("sub-1")))
                    .thenReturn(buildAcceptedResult(2));

            processor.pollAndProcess();

            verify(executionPipeline).execute(eq("javascript"), eq("console.log('hello');"),
                    eq(100L), eq("user-1"), eq("sub-1"));
            verify(submissionService).updateSubmissionResult(eq("sub-1"), eq("Accepted"),
                    anyInt(), any(), any());
            verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().status()).isEqualTo("Accepted");
        }

        @Test
        @DisplayName("returns early when max concurrent jobs reached")
        void maxConcurrentJobs_returnsEarly() throws Exception {
            when(queueConfig.getMaxConcurrentJobs()).thenReturn(0);

            processor.pollAndProcess();

            verify(queueService, never()).pollJob(anyString());
            verify(executionPipeline, never()).execute(anyString(), anyString(), anyLong(),
                    anyString(), anyString());
        }

        @Test
        @DisplayName("catches exception to prevent scheduler death")
        void exception_caughtAndLogged() {
            when(queueService.pollJob("judge_queue")).thenThrow(new RuntimeException("Redis down"));

            // Should not throw
            processor.pollAndProcess();
        }
    }

    // === processJob ===

    @Nested
    @DisplayName("processJob")
    class ProcessJob {

        @Test
        @DisplayName("sets status to Judging, calls pipeline, writes verdict")
        void setsJudgingThenWritesVerdict() throws Exception {
            when(executionPipeline.execute(anyString(), anyString(), eq(100L), eq("user-1"),
                    eq("sub-1")))
                    .thenReturn(buildAcceptedResult(1));

            processor.processJob(sampleJob);

            var statusOrder = inOrder(executionPipeline, submissionService);
            statusOrder.verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("Judging"), eq(0), isNull(), isNull());
            statusOrder.verify(executionPipeline).execute(eq("javascript"),
                    eq("console.log('hello');"), eq(100L), eq("user-1"), eq("sub-1"));
            statusOrder.verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("Accepted"), anyInt(), any(), any());
        }

        @Test
        @DisplayName("pipeline returns null → marks as System Error (no test cases)")
        void pipelineReturnsNull_marksSystemError() throws Exception {
            when(executionPipeline.execute(anyString(), anyString(), anyLong(), anyString(),
                    anyString()))
                    .thenReturn(null);

            processor.processJob(sampleJob);

            verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("System Error"), eq(0), eq(0.0), isNull());
            verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().status()).isEqualTo("System Error");
        }

        @Test
        @DisplayName("pushes WebSocket result after writing verdict")
        void pushesWebSocketAfterVerdict() throws Exception {
            when(executionPipeline.execute(anyString(), anyString(), anyLong(), anyString(),
                    anyString()))
                    .thenReturn(buildWrongAnswerResult(1));

            processor.processJob(sampleJob);

            var inOrder = inOrder(submissionService, submissionResultPushPort);
            inOrder.verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("Wrong Answer"), anyInt(), any(), any());
            inOrder.verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), any());
        }

        @Test
        @DisplayName("contest lookup failure does not overwrite successful verdict")
        void contestLookupFailure_keepsVerdict() throws Exception {
            when(executionPipeline.execute(anyString(), anyString(), anyLong(), anyString(),
                    anyString()))
                    .thenReturn(buildAcceptedResult(1));
            when(contestSubmissionMapper.selectOne(any()))
                    .thenThrow(new RuntimeException("contest schema mismatch"));

            processor.processJob(sampleJob);

            verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("Accepted"), anyInt(), any(), any());
            verify(submissionService, never()).updateSubmissionResult(
                    eq("sub-1"), eq("System Error"), anyInt(), any(), any());
            verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().status()).isEqualTo("Accepted");
        }

        @Test
        @DisplayName("pipeline exception marks submission as System Error instead of leaving Judging")
        void pipelineException_marksSystemError() throws Exception {
            when(executionPipeline.execute(anyString(), anyString(), anyLong(), anyString(),
                    anyString()))
                    .thenThrow(new RuntimeException("sandbox down"));

            processor.processJob(sampleJob);

            var statusOrder = inOrder(submissionService);
            statusOrder.verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("Judging"), eq(0), isNull(), isNull());
            statusOrder.verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("System Error"), eq(0), eq(0.0), isNull());
            verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().status()).isEqualTo("System Error");
        }
    }

    // === processJobFenced ===

    @Nested
    @DisplayName("processJobFenced (fenced path: lease acquire / renew / heartbeat)")
    class ProcessJobFenced {

        @Test
        @DisplayName("happy path: acquires lease, executes pipeline, writes verdict via fenced CAS")
        void fencedHappyPath_acquireExecuteWrite() throws Exception {
            when(featureFlags.isUseGenerationFence()).thenReturn(true);
            Submission current = new Submission();
            current.setId("sub-1");
            current.setGeneration(1L);
            when(submissionMapper.selectById("sub-1")).thenReturn(current);
            when(submissionMapper.acquireLease(eq("sub-1"), anyString(), eq(1L), anyLong()))
                    .thenReturn(1);
            when(executionPipeline.execute(anyString(), anyString(), eq(100L), eq("user-1"),
                    eq("sub-1")))
                    .thenReturn(buildAcceptedResult(1));
            when(submissionService.updateSubmissionResultFenced(eq("sub-1"), eq(1L), anyString(),
                    eq("Accepted"), anyInt(), anyDouble(), any()))
                    .thenReturn(true);

            processor.processJob(sampleJob);

            verify(submissionMapper).acquireLease(eq("sub-1"), anyString(), eq(1L), anyLong());
            verify(executionPipeline).execute(eq("javascript"), eq("console.log('hello');"),
                    eq(100L), eq("user-1"), eq("sub-1"));
            verify(submissionService).updateSubmissionResultFenced(eq("sub-1"), eq(1L), anyString(),
                    eq("Accepted"), anyInt(), anyDouble(), any());
            verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().status()).isEqualTo("Accepted");
        }

        @Test
        @DisplayName("submission not found → silently abandons (no pipeline call, no write)")
        void fencedSubmissionNotFound_abandons() throws Exception {
            when(featureFlags.isUseGenerationFence()).thenReturn(true);
            when(submissionMapper.selectById("sub-1")).thenReturn(null);

            processor.processJob(sampleJob);

            verify(submissionMapper, never()).acquireLease(anyString(), anyString(), anyLong(),
                    anyLong());
            verify(executionPipeline, never()).execute(anyString(), anyString(), anyLong(),
                    anyString(), anyString());
            verify(submissionService, never()).updateSubmissionResult(anyString(), anyString(),
                    anyInt(), any(), any());
        }

        @Test
        @DisplayName("lease not acquired (affected=0) → silently abandons (no pipeline call)")
        void fencedLeaseLost_abandons() throws Exception {
            when(featureFlags.isUseGenerationFence()).thenReturn(true);
            Submission current = new Submission();
            current.setId("sub-1");
            current.setGeneration(1L);
            when(submissionMapper.selectById("sub-1")).thenReturn(current);
            when(submissionMapper.acquireLease(eq("sub-1"), anyString(), eq(1L), anyLong()))
                    .thenReturn(0);

            processor.processJob(sampleJob);

            verify(executionPipeline, never()).execute(anyString(), anyString(), anyLong(),
                    anyString(), anyString());
            verify(submissionService, never()).updateSubmissionResult(anyString(), anyString(),
                    anyInt(), any(), any());
            verify(submissionResultPushPort, never()).emitSubmissionResult(anyString(), any());
        }

        @Test
        @DisplayName("pipeline returns null on fenced path → writes System Error via fenced CAS")
        void fencedPipelineReturnsNull_writesSystemError() throws Exception {
            when(featureFlags.isUseGenerationFence()).thenReturn(true);
            Submission current = new Submission();
            current.setId("sub-1");
            current.setGeneration(1L);
            when(submissionMapper.selectById("sub-1")).thenReturn(current);
            when(submissionMapper.acquireLease(eq("sub-1"), anyString(), eq(1L), anyLong()))
                    .thenReturn(1);
            when(executionPipeline.execute(anyString(), anyString(), eq(100L), eq("user-1"),
                    eq("sub-1")))
                    .thenReturn(null);
            when(submissionService.updateSubmissionResultFenced(eq("sub-1"), eq(1L), anyString(),
                    eq("System Error"), eq(0), eq(0.0), isNull()))
                    .thenReturn(true);

            processor.processJob(sampleJob);

            verify(submissionService).updateSubmissionResultFenced(eq("sub-1"), eq(1L),
                    anyString(), eq("System Error"), eq(0), eq(0.0), isNull());
            verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().status()).isEqualTo("System Error");
        }

        @Test
        @DisplayName("fenced write rejected (fence CAS mismatch) → drops result, no push")
        void fencedWriteRejected_dropsResult() throws Exception {
            when(featureFlags.isUseGenerationFence()).thenReturn(true);
            Submission current = new Submission();
            current.setId("sub-1");
            current.setGeneration(1L);
            when(submissionMapper.selectById("sub-1")).thenReturn(current);
            when(submissionMapper.acquireLease(eq("sub-1"), anyString(), eq(1L), anyLong()))
                    .thenReturn(1);
            when(executionPipeline.execute(anyString(), anyString(), eq(100L), eq("user-1"),
                    eq("sub-1")))
                    .thenReturn(buildAcceptedResult(1));
            // Simulate fence CAS rejecting the write (gen was bumped during judging)
            when(submissionService.updateSubmissionResultFenced(eq("sub-1"), eq(1L), anyString(),
                    eq("Accepted"), anyInt(), anyDouble(), any()))
                    .thenReturn(false);

            processor.processJob(sampleJob);

            verify(submissionService).updateSubmissionResultFenced(eq("sub-1"), eq(1L),
                    anyString(), eq("Accepted"), anyInt(), anyDouble(), any());
            verify(submissionResultPushPort, never()).emitSubmissionResult(anyString(), any());
        }

        @Test
        @DisplayName("pipeline throws on fenced path → fenced System Error write attempted")
        void fencedPipelineException_writesSystemError() throws Exception {
            when(featureFlags.isUseGenerationFence()).thenReturn(true);
            Submission current = new Submission();
            current.setId("sub-1");
            current.setGeneration(1L);
            when(submissionMapper.selectById("sub-1")).thenReturn(current);
            when(submissionMapper.acquireLease(eq("sub-1"), anyString(), eq(1L), anyLong()))
                    .thenReturn(1);
            when(executionPipeline.execute(anyString(), anyString(), eq(100L), eq("user-1"),
                    eq("sub-1")))
                    .thenThrow(new RuntimeException("sandbox crash"));
            when(submissionService.updateSubmissionResultFenced(eq("sub-1"), eq(1L), anyString(),
                    eq("System Error"), eq(0), eq(0.0), isNull()))
                    .thenReturn(true);

            processor.processJob(sampleJob);

            verify(submissionService).updateSubmissionResultFenced(eq("sub-1"), eq(1L),
                    anyString(), eq("System Error"), eq(0), eq(0.0), isNull());
            verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().status()).isEqualTo("System Error");
        }
    }

    // === shouldRetry ===

    @Nested
    @DisplayName("shouldRetry")
    class ShouldRetry {

        @Test
        @DisplayName("returns false for compile errors")
        void compileError_noRetry() {
            Exception error = new RuntimeException("Compile error: invalid syntax");

            boolean result = processor.shouldRetry(sampleJob, error, 0, 3);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns false for SUBMISSION_LANGUAGE_UNSUPPORTED BusinessException")
        void unsupportedLanguage_noRetry() {
            Exception error = new BusinessException(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED);

            boolean result = processor.shouldRetry(sampleJob, error, 0, 3);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("returns true for transient failures when attempts < maxRetries")
        void transientFailure_shouldRetry() {
            Exception error = new RuntimeException("Docker timeout");

            boolean result = processor.shouldRetry(sampleJob, error, 1, 3);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when attempts >= maxRetries")
        void exhaustedRetries_noRetry() {
            Exception error = new RuntimeException("Docker timeout");

            boolean result = processor.shouldRetry(sampleJob, error, 3, 3);

            assertThat(result).isFalse();
        }
    }

    // === onFailure ===

    @Nested
    @DisplayName("onFailure")
    class OnFailure {

        @Test
        @DisplayName("marks submission as System Error when retries exhausted")
        void retriesExhausted_marksSystemError() {
            sampleJob.setAttempts(3);

            processor.onFailure(sampleJob, new RuntimeException("Docker down"));

            verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("System Error"), eq(0), eq(0.0), isNull());
            verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().status()).isEqualTo("System Error");
        }

        @Test
        @DisplayName("retries job when shouldRetry returns true")
        void retryableError_retriesJob() {
            sampleJob.setAttempts(1);

            processor.onFailure(sampleJob, new RuntimeException("Transient failure"));

            verify(queueService).retryJob(eq(sampleJob.getId()));
        }
    }

    // === process (JobProcessor interface) ===

    @Test
    @DisplayName("process returns COMPLETED JobStatusDTO after processJob")
    void process_returnsCompletedStatus() throws Exception {
        JobStatusDTO status = processor.process(sampleJob);

        assertThat(status.getJobType()).isEqualTo("judge_queue");
        assertThat(status.getStatus().name()).isEqualTo("COMPLETED");
    }

    // === Helper methods ===

    private JudgeExecutionResult buildAcceptedResult(int caseCount) {
        var details = new java.util.ArrayList<Submission.TestCaseDetail>();
        for (int i = 0; i < caseCount; i++) {
            Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
            detail.setStatus("Accepted");
            detail.setTime(50 + i * 10);
            detail.setMemory(4.0 + i);
            details.add(detail);
        }
        return new JudgeExecutionResult(
                "Accepted",
                50 + (caseCount - 1) * 10,
                4.0 + (caseCount - 1),
                details);
    }

    private JudgeExecutionResult buildWrongAnswerResult(int caseCount) {
        var details = new java.util.ArrayList<Submission.TestCaseDetail>();
        for (int i = 0; i < caseCount; i++) {
            String status = (i == caseCount - 1) ? "Wrong Answer" : "Accepted";
            Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
            detail.setStatus(status);
            detail.setTime(50);
            detail.setMemory(4.0);
            details.add(detail);
        }
        return new JudgeExecutionResult("Wrong Answer", 50, 4.0, details);
    }
}