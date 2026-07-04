package com.ulticode.modules.queue.processor;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.queue.config.QueueConfig;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import com.ulticode.modules.queue.job.JudgeJob;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.SubmissionService;
import com.ulticode.modules.submission.service.VerdictResolver;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JudgeWorkerProcessor")
class JudgeWorkerProcessorTest {

    @Mock
    private QueueService queueService;

    @Mock
    private CodeExecutionService codeExecutionService;

    @Mock
    private SubmissionService submissionService;

    @Mock
    private SubmissionResultPushPort submissionResultPushPort;

    @Mock
    private ProblemExampleMapper problemExampleMapper;

    @Mock
    private ContestSubmissionMapper contestSubmissionMapper;

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
    private com.ulticode.common.config.FeatureFlagsProperties featureFlags =
            new com.ulticode.common.config.FeatureFlagsProperties();

    /**
     * ADR-003 M3b: metrics registry mock; never invoked on the flag-off path.
     */
    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    /**
     * ObjectMapper used by {@code buildRunSubmissionDTO}. Real instance wrapped
     * as a {@link Spy} so {@link InjectMocks} injects it via the constructor.
     */
    @Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * VerdictResolver is a pure function; use a real spy so the existing
     * determineVerdict tests exercise the real reduction logic without
     * needing per-test stubbing. {@link InjectMocks} picks up spies the
     * same way it picks up mocks for constructor injection (see project
     * rule mockito5-lombok-constructor-injection).
     */
    @Spy
    private VerdictResolver verdictResolver = new VerdictResolver();

    /**
     * P0-1: judge source properties. These pre-P0-1 tests exercise the
     * legacy {@code problem_examples} path (no flag concept existed), so we
     * force {@code useTestCases=false} here to preserve byte-for-byte
     * legacy behaviour. New P0-1 tests in {@code JudgeWorkerTestCasesSourceIT}
     * / {@code JudgeWorkerFailClosedIT} cover the flag-on paths.
     */
    @Spy
    private com.ulticode.common.config.JudgeSourceProperties judgeSourceProperties =
            buildLegacyJudgeSource();

    /**
     * P0-1: TestCaseMapper mock — never invoked on the flag-off legacy path.
     */
    @Mock
    private com.ulticode.modules.problem.mapper.TestCaseMapper testCaseMapper;

    private static com.ulticode.common.config.JudgeSourceProperties buildLegacyJudgeSource() {
        com.ulticode.common.config.JudgeSourceProperties p =
                new com.ulticode.common.config.JudgeSourceProperties();
        p.setUseTestCases(false);
        return p;
    }

    @InjectMocks
    private JudgeWorkerProcessor processor;

    @Captor
    private ArgumentCaptor<SubmissionResultPayload> payloadCaptor;

    @Captor
    private ArgumentCaptor<List<Submission.TestCaseDetail>> detailsCaptor;

    private JudgeJob sampleJob;

    @BeforeEach
    void setUp() {
        sampleJob = JudgeJob.create("sub-1", "100", "user-1", "javascript", "console.log('hello');");
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
        void emptyQueue_doesNothing() {
            when(queueService.pollJob("judge_queue")).thenReturn(null);

            processor.pollAndProcess();

            verify(submissionService, never()).updateSubmissionResult(anyString(), anyString(),
                    anyInt(), any(), any());
        }

        @Test
        @DisplayName("processes JudgeJob and decrements activeJobs")
        void withJudgeJob_processesAndDecrementsActiveJobs() {
            when(queueService.pollJob("judge_queue")).thenReturn(sampleJob);
            when(problemExampleMapper.findByProblemIdOrderByOrder(100L))
                    .thenReturn(buildProblemExamples(2));
            when(codeExecutionService.execute(any(RunSubmissionDTO.class), eq(100L), eq("user-1")))
                    .thenReturn(buildAcceptedResult(2));

            processor.pollAndProcess();

            verify(submissionService).updateSubmissionResult(eq("sub-1"), eq("Accepted"),
                    anyInt(), any(), any());
            verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().status()).isEqualTo("Accepted");
        }

        @Test
        @DisplayName("returns early when max concurrent jobs reached")
        void maxConcurrentJobs_returnsEarly() {
            when(queueConfig.getMaxConcurrentJobs()).thenReturn(0);

            processor.pollAndProcess();

            verify(queueService, never()).pollJob(anyString());
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
        @DisplayName("sets status to Judging, executes, writes verdict")
        void setsJudgingThenWritesVerdict() {
            when(problemExampleMapper.findByProblemIdOrderByOrder(100L))
                    .thenReturn(buildProblemExamples(1));
            when(codeExecutionService.execute(any(RunSubmissionDTO.class), eq(100L), eq("user-1")))
                    .thenReturn(buildAcceptedResult(1));

            processor.processJob(sampleJob);

            var statusOrder = inOrder(submissionService);
            statusOrder.verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("Judging"), eq(0), isNull(), isNull());
            statusOrder.verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("Accepted"), anyInt(), any(), any());
        }

        @Test
        @DisplayName("null test cases marks as System Error")
        void nullTestCases_marksSystemError() {
            when(problemExampleMapper.findByProblemIdOrderByOrder(100L)).thenReturn(null);

            processor.processJob(sampleJob);

            verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("System Error"), eq(0), eq(0.0), isNull());
            verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue().status()).isEqualTo("System Error");
        }

        @Test
        @DisplayName("empty test cases marks as System Error")
        void emptyTestCases_marksSystemError() {
            when(problemExampleMapper.findByProblemIdOrderByOrder(100L)).thenReturn(List.of());

            processor.processJob(sampleJob);

            verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("System Error"), eq(0), eq(0.0), isNull());
        }

        @Test
        @DisplayName("pushes WebSocket result after writing verdict")
        void pushesWebSocketAfterVerdict() {
            when(problemExampleMapper.findByProblemIdOrderByOrder(100L))
                    .thenReturn(buildProblemExamples(1));
            when(codeExecutionService.execute(any(RunSubmissionDTO.class), eq(100L), eq("user-1")))
                    .thenReturn(buildWrongAnswerResult(1));

            processor.processJob(sampleJob);

            var inOrder = inOrder(submissionService, submissionResultPushPort);
            inOrder.verify(submissionService).updateSubmissionResult(
                    eq("sub-1"), eq("Wrong Answer"), anyInt(), any(), any());
            inOrder.verify(submissionResultPushPort).emitSubmissionResult(eq("user-1"), any());
        }

        @Test
        @DisplayName("contest lookup failure does not overwrite successful verdict")
        void contestLookupFailure_keepsVerdict() {
            when(problemExampleMapper.findByProblemIdOrderByOrder(100L))
                    .thenReturn(buildProblemExamples(1));
            when(codeExecutionService.execute(any(RunSubmissionDTO.class), eq(100L), eq("user-1")))
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
        @DisplayName("load failure marks submission as System Error instead of leaving Judging")
        void testCaseLoadFailure_marksSystemError() {
            when(problemExampleMapper.findByProblemIdOrderByOrder(100L))
                    .thenThrow(new RuntimeException("problem_examples unavailable"));

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

    // === determineVerdict ===

    @Nested
    @DisplayName("determineVerdict")
    class DetermineVerdict {

        @Test
        @DisplayName("returns Runtime Error when any case has RE (highest priority)")
        void runtimeError_hasHighestPriority() {
            var cases = List.of(
                    buildCaseResult("Accepted", "50ms", "4.0MB"),
                    buildCaseResult("Runtime Error", "100ms", "8.0MB"),
                    buildCaseResult("Accepted", "30ms", "3.0MB")
            );

            String verdict = processor.determineVerdict(cases);

            assertThat(verdict).isEqualTo("Runtime Error");
        }

        @Test
        @DisplayName("returns Accepted when all cases pass")
        void allAccepted_returnsAccepted() {
            var cases = List.of(
                    buildCaseResult("Accepted", "50ms", "4.0MB"),
                    buildCaseResult("Accepted", "30ms", "3.0MB")
            );

            String verdict = processor.determineVerdict(cases);

            assertThat(verdict).isEqualTo("Accepted");
        }

        @Test
        @DisplayName("returns Wrong Answer when any case fails with WA")
        void wrongAnswer_whenPresent() {
            var cases = List.of(
                    buildCaseResult("Accepted", "50ms", "4.0MB"),
                    buildCaseResult("Wrong Answer", "30ms", "3.0MB")
            );

            String verdict = processor.determineVerdict(cases);

            assertThat(verdict).isEqualTo("Wrong Answer");
        }

        @Test
        @DisplayName("returns TLE when any case times out")
        void timeLimitExceeded_whenPresent() {
            var cases = List.of(
                    buildCaseResult("Accepted", "50ms", "4.0MB"),
                    buildCaseResult("Time Limit Exceeded", "2000ms", "4.0MB")
            );

            String verdict = processor.determineVerdict(cases);

            assertThat(verdict).isEqualTo("Time Limit Exceeded");
        }

        @Test
        @DisplayName("priority order: RE > MLE > TLE > WA > PE > Accepted")
        void fullPriorityOrder() {
            var cases = List.of(
                    buildCaseResult("Accepted", "10ms", "1.0MB"),
                    buildCaseResult("Presentation Error", "10ms", "1.0MB"),
                    buildCaseResult("Wrong Answer", "10ms", "1.0MB"),
                    buildCaseResult("Time Limit Exceeded", "2000ms", "1.0MB"),
                    buildCaseResult("Memory Limit Exceeded", "10ms", "256.0MB"),
                    buildCaseResult("Runtime Error", "10ms", "1.0MB")
            );

            assertThat(processor.determineVerdict(cases)).isEqualTo("Runtime Error");
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

    // === parseMemoryMb ===

    @Nested
    @DisplayName("parseMemoryMb")
    class ParseMemoryMb {

        @Test
        @DisplayName("parses standard MB format")
        void standardFormat() {
            assertThat(processor.parseMemoryMb("4.2MB")).isEqualTo(4.2);
        }

        @Test
        @DisplayName("handles zero")
        void zeroValue() {
            assertThat(processor.parseMemoryMb("0.0MB")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0.0 for null input")
        void nullInput() {
            assertThat(processor.parseMemoryMb(null)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0.0 for malformed input")
        void malformedInput() {
            assertThat(processor.parseMemoryMb("invalid")).isEqualTo(0.0);
        }
    }

    // === parseRuntimeMs ===

    @Nested
    @DisplayName("parseRuntimeMs")
    class ParseRuntimeMs {

        @Test
        @DisplayName("parses standard ms format")
        void standardFormat() {
            assertThat(processor.parseRuntimeMs("123ms")).isEqualTo(123L);
        }

        @Test
        @DisplayName("returns 0 for null input")
        void nullInput() {
            assertThat(processor.parseRuntimeMs(null)).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns 0 for malformed input")
        void malformedInput() {
            assertThat(processor.parseRuntimeMs("invalid")).isEqualTo(0L);
        }
    }

    // === Helper methods ===

    private List<ProblemExample> buildProblemExamples(int count) {
        java.util.ArrayList<ProblemExample> cases = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ProblemExample tc = new ProblemExample();
            tc.setId(String.valueOf(1000 + i));
            tc.setProblemId(100L);
            tc.setExampleOrder(i + 1);
            tc.setInputText(String.valueOf(i));
            tc.setOutputText(String.valueOf(i));
            cases.add(tc);
        }
        return cases;
    }

    private RunResultDTO buildAcceptedResult(int caseCount) {
        var caseResults = new java.util.ArrayList<RunResultDTO.RunCaseResult>();
        for (int i = 0; i < caseCount; i++) {
            caseResults.add(buildCaseResult("Accepted", (50 + i * 10) + "ms", (4.0 + i) + "MB"));
        }
        return RunResultDTO.builder()
                .verdict("Accepted")
                .runtime((50 + (caseCount - 1) * 10) + "ms")
                .memory((4.0 + (caseCount - 1)) + "MB")
                .cases(caseResults)
                .passedCases(caseCount)
                .totalCases(caseCount)
                .build();
    }

    private RunResultDTO buildWrongAnswerResult(int caseCount) {
        var caseResults = new java.util.ArrayList<RunResultDTO.RunCaseResult>();
        for (int i = 0; i < caseCount; i++) {
            String status = (i == caseCount - 1) ? "Wrong Answer" : "Accepted";
            caseResults.add(buildCaseResult(status, "50ms", "4.0MB"));
        }
        return RunResultDTO.builder()
                .verdict("Wrong Answer")
                .runtime("50ms")
                .memory("4.0MB")
                .cases(caseResults)
                .passedCases(caseCount - 1)
                .totalCases(caseCount)
                .build();
    }

    private RunResultDTO.RunCaseResult buildCaseResult(String status, String runtime, String memory) {
        return RunResultDTO.RunCaseResult.builder()
                .status(status)
                .runtime(runtime)
                .memory(memory)
                .build();
    }
}
