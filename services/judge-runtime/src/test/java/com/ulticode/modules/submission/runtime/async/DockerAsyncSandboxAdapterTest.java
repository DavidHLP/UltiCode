package com.ulticode.modules.submission.runtime.async;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.sandbox.BatchRunResult;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxExecutor;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.TestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DockerAsyncSandboxAdapterTest {

    @Test
    void delegatesCompletionThroughTheAsyncContract() throws Exception {
        SandboxExecutor delegate = delegate((job, testCase) -> RunCaseResult.accepted(3L, 4L));
        DockerAsyncSandboxAdapter adapter = new DockerAsyncSandboxAdapter(delegate);
        try {
            AsyncSandboxExecutor.ExecutionHandle handle = adapter.submit(request());
            AsyncSandboxExecutor.ExecutionSnapshot snapshot = await(adapter, handle);

            assertThat(snapshot.state()).isEqualTo(AsyncSandboxExecutor.State.COMPLETED);
            assertThat(snapshot.result().status()).isEqualTo(SubmissionStatus.ACCEPTED);
        } finally {
            adapter.shutdown();
        }
    }

    @Test
    void cancellationIsVisibleThroughTheAsyncContract() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        SandboxExecutor delegate = delegate((job, testCase) -> {
            started.countDown();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return RunCaseResult.rejected(SubmissionStatus.RUNTIME_ERROR,
                    "cancelled", 0L, 0L);
        });
        DockerAsyncSandboxAdapter adapter = new DockerAsyncSandboxAdapter(delegate);
        try {
            AsyncSandboxExecutor.ExecutionHandle handle = adapter.submit(request());
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            adapter.cancel(handle);
            assertThat(adapter.poll(handle).state())
                    .isEqualTo(AsyncSandboxExecutor.State.CANCELLED);
        } finally {
            adapter.shutdown();
        }
    }
    @Test
    void sameKeyReplaysTerminalReceiptAndDifferentPayloadConflicts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SandboxExecutor delegate = delegate((job, testCase) -> {
            calls.incrementAndGet();
            return RunCaseResult.accepted(1L, 1L);
        });
        DockerAsyncSandboxAdapter adapter = new DockerAsyncSandboxAdapter(delegate);
        try {
            AsyncSandboxExecutor.ExecutionRequest first = request();
            AsyncSandboxExecutor.ExecutionHandle handle = adapter.submit(first);
            assertThat(adapter.submit(first)).isEqualTo(handle);
            assertThat(await(adapter, handle).state())
                    .isEqualTo(AsyncSandboxExecutor.State.COMPLETED);
            assertThat(adapter.submit(first)).isEqualTo(handle);
            assertThat(calls).hasValue(1);

            AsyncSandboxExecutor.ExecutionRequest different =
                    new AsyncSandboxExecutor.ExecutionRequest(
                            new SandboxJob("run-1", "user-1", "submission-1", 0L,
                                    "python", "different", 2, 128),
                            first.testCase(), first.visibility(), first.idempotencyKey());
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> adapter.submit(different))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("idempotency key conflicts");
        } finally {
            adapter.shutdown();
        }
    }

    private static SandboxExecutor delegate(
            java.util.function.BiFunction<SandboxJob, TestCase, RunCaseResult> runner) {
        return new SandboxExecutor() {
            @Override
            public RunCaseResult run(SandboxJob job, TestCase testCase) {
                return runner.apply(job, testCase);
            }

            @Override
            public BatchRunResult runBatch(SandboxJob job, List<TestCase> cases) {
                return new BatchRunResult(cases.stream()
                        .map(testCase -> runner.apply(job, testCase))
                        .toList());
            }
        };
    }
    private static AsyncSandboxExecutor.ExecutionRequest request() {
        return new AsyncSandboxExecutor.ExecutionRequest(
                new SandboxJob("run-1", "user-1", "submission-1", 0L,
                        "python", "print('ok')", 2, 128),
                new TestCase("case-1", "Case 1", List.of(), "ok"),
                AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW);
    }

    private static AsyncSandboxExecutor.ExecutionSnapshot await(
            DockerAsyncSandboxAdapter adapter,
            AsyncSandboxExecutor.ExecutionHandle handle) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            AsyncSandboxExecutor.ExecutionSnapshot snapshot = adapter.poll(handle);
            if (snapshot.state() != AsyncSandboxExecutor.State.RUNNING) {
                return snapshot;
            }
            Thread.sleep(10L);
        }
        return AsyncSandboxExecutor.ExecutionSnapshot.failed("test timed out");
    }
}
