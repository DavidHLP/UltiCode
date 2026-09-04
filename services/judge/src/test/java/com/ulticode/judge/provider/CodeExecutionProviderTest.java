package com.ulticode.judge.provider;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.judge.api.JudgeRunCommand;
import com.ulticode.judge.api.JudgeRunResult;
import com.ulticode.judge.api.JudgeRunService;
import com.ulticode.judge.api.JudgeRunService.AsyncExecutionHandle;
import com.ulticode.modules.submission.runtime.JudgeRunResponse;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.runtime.async.AsyncSandboxExecutor;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class CodeExecutionProviderTest {

    @Test
    void delegatesToJudgeRuntimeAndMapsResult() {
        CodeExecutionService delegate = mock(CodeExecutionService.class);
        CodeExecutionProvider provider = new CodeExecutionProvider(delegate);
        JudgeRunCommand command = new JudgeRunCommand(
                "request-1", 42L, "user-1", "python", "print('ok')", List.of(),
                TraceMetadata.EMPTY);
        when(delegate.execute(any(), org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq("user-1")))
                .thenReturn(JudgeRunResponse.builder().id("run-1").cases(List.of()).build());

        var result = provider.execute(command);
        assertThat(result.success()).isTrue();
        assertThat(result.data().id()).isEqualTo("run-1");
    }

    @Test
    void mapsRuntimeValidationFailureToTypedBadRequest() {
        CodeExecutionService delegate = mock(CodeExecutionService.class);
        CodeExecutionProvider provider = new CodeExecutionProvider(delegate);
        JudgeRunCommand command = new JudgeRunCommand(
                "request-1", 42L, "user-1", "python", "print('ok')", List.of(),
                TraceMetadata.EMPTY);
        when(delegate.execute(any(), org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq("user-1")))
                .thenThrow(new BusinessException(BaseErrorCode.BAD_REQUEST));

        var result = provider.execute(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(BaseErrorCode.BAD_REQUEST.code());
    }

    @Test
    void asyncResultRetainsExecutionIdentityMetadata() {
        CodeExecutionService delegate = mock(CodeExecutionService.class);
        AsyncSandboxExecutor executor = mock(AsyncSandboxExecutor.class);
        CodeExecutionProvider provider = new CodeExecutionProvider(delegate, executor);
        JudgeRunCommand.TestCase testCase = new JudgeRunCommand.TestCase(
                "case-1", "Case 1", "ok", List.of());
        JudgeRunCommand command = new JudgeRunCommand(
                "request-1", 42L, "user-1", "python", "print('ok')",
                List.of(testCase), TraceMetadata.EMPTY);
        when(delegate.resolveExecutionLimits(42L))
                .thenReturn(new CodeExecutionService.ExecutionLimits(7, 512));
        when(executor.submit(any()))
                .thenReturn(new AsyncSandboxExecutor.ExecutionHandle("execution-1"));

        var submitted = provider.submit(command);
        org.mockito.ArgumentCaptor<AsyncSandboxExecutor.ExecutionRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(AsyncSandboxExecutor.ExecutionRequest.class);
        org.mockito.Mockito.verify(executor).submit(requestCaptor.capture());
        assertThat(requestCaptor.getValue().job().timeoutSeconds()).isEqualTo(7);
        assertThat(requestCaptor.getValue().job().memoryMb()).isEqualTo(512);
        when(executor.poll(any())).thenReturn(
                AsyncSandboxExecutor.ExecutionSnapshot.completed(
                        RunCaseResult.acceptedWithOutput(4L, 1024L, "ok", "ok", List.of())));

        var polled = provider.poll(submitted.data());

        assertThat(polled.data().result().id()).isEqualTo("execution-1");
        assertThat(polled.data().result().problemId()).isEqualTo(42L);
        assertThat(polled.data().result().userId()).isEqualTo("user-1");
        assertThat(polled.data().result().cases()).singleElement()
                .satisfies(result -> {
                    assertThat(result.runId()).isEqualTo("execution-1");
                    assertThat(result.testCaseId()).isEqualTo("case-1");
                    assertThat(result.caseLabel()).isEqualTo("Case 1");
                });

        assertThat(provider.poll(new AsyncExecutionHandle("execution-1"))
                .data().result().problemId()).isEqualTo(42L);
    }

    @Test
    void asyncRequestUsesCanonicalLanguageId() {
        CodeExecutionService delegate = mock(CodeExecutionService.class);
        AsyncSandboxExecutor executor = mock(AsyncSandboxExecutor.class);
        CodeExecutionProvider provider = new CodeExecutionProvider(delegate, executor);
        when(delegate.resolveExecutionLimits(42L))
                .thenReturn(new CodeExecutionService.ExecutionLimits(7, 512));
        when(executor.submit(any()))
                .thenReturn(new AsyncSandboxExecutor.ExecutionHandle("execution-1"));

        provider.submit(new JudgeRunCommand(
                "request-1", 42L, "user-1", " Python ", "print('ok')",
                List.of(new JudgeRunCommand.TestCase("case-1", "Case 1", "ok", List.of())),
                TraceMetadata.EMPTY));

        org.mockito.ArgumentCaptor<AsyncSandboxExecutor.ExecutionRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(AsyncSandboxExecutor.ExecutionRequest.class);
        org.mockito.Mockito.verify(executor).submit(requestCaptor.capture());
        assertThat(requestCaptor.getValue().job().languageId()).isEqualTo("python");
        assertThat(requestCaptor.getValue().job().runId()).isNotEqualTo("request-1");
        assertThat(requestCaptor.getValue().job().submissionId())
                .isEqualTo(requestCaptor.getValue().job().runId());
        assertThat(java.util.UUID.fromString(requestCaptor.getValue().job().runId()))
                .isNotNull();

    }

    @Test
    void failedPollKeepsHandleRecoverableAndBounded() {
        CodeExecutionService delegate = mock(CodeExecutionService.class);
        AsyncSandboxExecutor executor = mock(AsyncSandboxExecutor.class);
        CodeExecutionProvider provider = new CodeExecutionProvider(delegate, executor);
        when(delegate.resolveExecutionLimits(42L))
                .thenReturn(new CodeExecutionService.ExecutionLimits(7, 512));
        when(executor.submit(any()))
                .thenReturn(new AsyncSandboxExecutor.ExecutionHandle("execution-1"));
        when(executor.poll(any())).thenReturn(
                AsyncSandboxExecutor.ExecutionSnapshot.failed("Judge0 is unavailable"));

        var failed = provider.submit(asyncCommand("request-1"));
        assertThat(provider.poll(failed.data()).data().state())
                .isEqualTo(JudgeRunService.State.FAILED);

        assertThat(provider.poll(failed.data()).data().state())
                .as("a recoverable FAILED must keep the handle pollable")
                .isEqualTo(JudgeRunService.State.FAILED);
        org.mockito.Mockito.verify(executor, org.mockito.Mockito.times(2))
                .poll(any());
    }

    @Test
    void abandonedAsyncMetadataExpiresAndCapacityRecovers() {
        CodeExecutionService delegate = mock(CodeExecutionService.class);
        AsyncSandboxExecutor executor = mock(AsyncSandboxExecutor.class);
        AtomicLong now = new AtomicLong();
        CodeExecutionProvider provider = new CodeExecutionProvider(
                delegate, executor, 100L, now::get);
        when(delegate.resolveExecutionLimits(42L))
                .thenReturn(new CodeExecutionService.ExecutionLimits(7, 512));
        var executionIds = new AtomicInteger();
        when(executor.submit(any())).thenAnswer(invocation ->
                new AsyncSandboxExecutor.ExecutionHandle(
                        "execution-" + executionIds.incrementAndGet()));

        for (int i = 0; i < 1_024; i++) {
            assertThat(provider.submit(asyncCommand("request-" + i)).success()).isTrue();
        }
        assertThat(provider.submit(asyncCommand("request-over-capacity")).success()).isFalse();

        now.set(101L);

        assertThat(provider.submit(asyncCommand("request-after-expiry")).success()).isTrue();
    }

    private static JudgeRunCommand asyncCommand(String requestId) {
        return new JudgeRunCommand(
                requestId, 42L, "user-1", "python", "print('ok')",
                List.of(new JudgeRunCommand.TestCase("case-1", "Case 1", "ok", List.of())),
                TraceMetadata.EMPTY);
    }

    @Test
    void rejectsNonPublicVisibilityBeforeRuntimeDelegate() {
        CodeExecutionService delegate = mock(CodeExecutionService.class);
        CodeExecutionProvider provider = new CodeExecutionProvider(delegate);
        JudgeRunCommand command = new JudgeRunCommand(
                "request-1", 42L, "user-1", "python", "print('ok')", List.of(),
                TraceMetadata.EMPTY, JudgeRunCommand.Visibility.PRIVATE);

        var result = provider.execute(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(BaseErrorCode.BAD_REQUEST.code());
        org.mockito.Mockito.verify(delegate, never()).execute(any(), any(), any());
    }
}
