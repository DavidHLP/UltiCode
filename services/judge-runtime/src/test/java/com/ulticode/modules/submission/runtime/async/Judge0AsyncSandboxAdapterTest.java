package com.ulticode.modules.submission.runtime.async;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.sandbox.SandboxJob;
import com.ulticode.modules.submission.sandbox.TestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Judge0AsyncSandboxAdapterTest {

    @Test
    void privateAndHiddenExecutionsAreRejectedBeforeTransport() {
        FakeTransport transport = new FakeTransport();
        Judge0AsyncSandboxAdapter adapter = new Judge0AsyncSandboxAdapter(
                transport, properties());

        for (AsyncSandboxExecutor.Visibility visibility : List.of(
                AsyncSandboxExecutor.Visibility.PRIVATE,
                AsyncSandboxExecutor.Visibility.HIDDEN)) {
            assertThatThrownBy(() -> adapter.submit(request(visibility)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PUBLIC_PREVIEW");
        }
        assertThat(transport.submitCalls).isZero();
        adapter.shutdown();
    }

    @Test
    void publicPreviewMapsQueuedAndCompletedVendorStates() {
        FakeTransport transport = new FakeTransport();
        Judge0AsyncSandboxAdapter adapter = new Judge0AsyncSandboxAdapter(
                transport, properties());
        AsyncSandboxExecutor.ExecutionHandle handle = adapter.submit(request(
                AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW));
        assertThat(transport.lastSubmission.memoryLimitKb()).isEqualTo(131_072L);

        assertThat(transport.lastSubmission.languageId()).isEqualTo(71);
        assertThat(adapter.poll(handle).state())
                .isEqualTo(AsyncSandboxExecutor.State.QUEUED);

        transport.poll = new Judge0Transport.Poll(
                Judge0Transport.Status.ACCEPTED, "ok", "", null, null, 12L, 2048L);
        AsyncSandboxExecutor.ExecutionSnapshot completed = adapter.poll(handle);

        assertThat(completed.state()).isEqualTo(AsyncSandboxExecutor.State.COMPLETED);
        assertThat(completed.result().status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(completed.result().output()).isEqualTo("ok");
        adapter.shutdown();
    }

    @Test
    void outputLimitIsMappedWithoutLeakingVendorState() {
        FakeTransport transport = new FakeTransport();
        Judge0Properties properties = properties();
        properties.setMaxOutputBytes(2);
        Judge0AsyncSandboxAdapter adapter = new Judge0AsyncSandboxAdapter(transport, properties);
        AsyncSandboxExecutor.ExecutionHandle handle = adapter.submit(request(
                AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW));
        transport.poll = new Judge0Transport.Poll(
                Judge0Transport.Status.ACCEPTED, "toolong", "", null, null, 1L, 1L);

        AsyncSandboxExecutor.ExecutionSnapshot snapshot = adapter.poll(handle);

        assertThat(snapshot.result().status()).isEqualTo(SubmissionStatus.OUTPUT_LIMIT_EXCEEDED);
        assertThat(snapshot.result().output().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSizeLessThanOrEqualTo(2);
        adapter.shutdown();
    }

    @Test
    void vendorResourceAndInternalErrorsMapTruthfully() {
        assertThat(Judge0HttpTransport.status(13))
                .isEqualTo(Judge0Transport.Status.FAILED);
        assertThat(Judge0HttpTransport.status(14))
                .isEqualTo(Judge0Transport.Status.FAILED);
        assertThat(Judge0HttpTransport.status(15))
                .isEqualTo(Judge0Transport.Status.MEMORY_LIMIT_EXCEEDED);
        assertThat(Judge0HttpTransport.status(17))
                .isEqualTo(Judge0Transport.Status.OUTPUT_LIMIT_EXCEEDED);
    }

    @Test
    void cancelDelegatesAndRemovesExecution() {
        FakeTransport transport = new FakeTransport();
        Judge0AsyncSandboxAdapter adapter = new Judge0AsyncSandboxAdapter(
                transport, properties());
        AsyncSandboxExecutor.ExecutionHandle handle = adapter.submit(request(
                AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW));

        adapter.cancel(handle);

        assertThat(transport.cancelledToken).isEqualTo("token-1");
        assertThat(adapter.poll(handle).state()).isEqualTo(AsyncSandboxExecutor.State.CANCELLED);
        adapter.shutdown();
    }

    @Test
    void failedCancellationKeepsTokenForRetry() {
        FakeTransport transport = new FakeTransport();
        transport.cancelFailures = 1;
        Judge0AsyncSandboxAdapter adapter = new Judge0AsyncSandboxAdapter(
                transport, properties());
        AsyncSandboxExecutor.ExecutionHandle handle = adapter.submit(request(
                AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW));

        assertThatThrownBy(() -> adapter.cancel(handle))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancellation pending");
        assertThat(transport.cancelledToken).isNull();
        assertThat(adapter.poll(handle).state()).isEqualTo(AsyncSandboxExecutor.State.QUEUED);

        adapter.cancel(handle);

        assertThat(transport.cancelledToken).isEqualTo("token-1");
        assertThat(adapter.poll(handle).state()).isEqualTo(AsyncSandboxExecutor.State.CANCELLED);
        adapter.shutdown();
    }
    @Test
    void permanentCancellationFailureReleasesExecutionSlot() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.cancelFailures = 10;
        Judge0Properties properties = properties();
        properties.setRequestTimeoutMs(100);
        Judge0AsyncSandboxAdapter adapter = new Judge0AsyncSandboxAdapter(
                transport, properties);
        try {
            AsyncSandboxExecutor.ExecutionHandle handle = adapter.submit(shortRequest());

            assertThat(transport.cancellationAttempts.await(8, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100L);
            assertThat(adapter.poll(handle).state())
                    .isEqualTo(AsyncSandboxExecutor.State.FAILED);

            transport.cancelFailures = 0;
            AsyncSandboxExecutor.ExecutionHandle next = adapter.submit(request(
                    "print('next')", AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW,
                    "run-2:case-2"));
            assertThat(next).isNotNull();
            adapter.cancel(next);
        } finally {
            adapter.shutdown();
        }
    }


    @Test
    void failedPollKeepsTokenForRecoveryPoll() {
        FakeTransport transport = new FakeTransport();
        transport.pollFailures = 1;
        Judge0AsyncSandboxAdapter adapter = new Judge0AsyncSandboxAdapter(
                transport, properties());
        AsyncSandboxExecutor.ExecutionHandle handle = adapter.submit(request(
                AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW));

        assertThat(adapter.poll(handle).state()).isEqualTo(AsyncSandboxExecutor.State.FAILED);
        transport.poll = new Judge0Transport.Poll(
                Judge0Transport.Status.ACCEPTED, "ok", "", null, null, 1L, 1L);

        assertThat(adapter.poll(handle).state()).isEqualTo(AsyncSandboxExecutor.State.COMPLETED);
        adapter.shutdown();
    }

    @Test
    void sameKeyReusesTerminalReceiptAndDifferentPayloadConflicts() {
        FakeTransport transport = new FakeTransport();
        Judge0AsyncSandboxAdapter adapter = new Judge0AsyncSandboxAdapter(
                transport, properties());
        AsyncSandboxExecutor.ExecutionRequest first = request(
                "print('ok')", AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW, "same-key");
        AsyncSandboxExecutor.ExecutionHandle handle = adapter.submit(first);

        assertThat(adapter.submit(first)).isEqualTo(handle);
        assertThatThrownBy(() -> adapter.submit(request(
                "print('different')", AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW, "same-key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency key conflicts");

        transport.poll = new Judge0Transport.Poll(
                Judge0Transport.Status.ACCEPTED, "ok", "", null, null, 1L, 1L);
        assertThat(adapter.poll(handle).state())
                .isEqualTo(AsyncSandboxExecutor.State.COMPLETED);
        assertThat(adapter.submit(first)).isEqualTo(handle);
        assertThat(transport.submitCalls).isEqualTo(1);
        adapter.shutdown();
    }

    private static Judge0Properties properties() {
        Judge0Properties properties = new Judge0Properties();
        properties.setEnabled(true);
        properties.setEndpoint("https://judge0.example.invalid");
        properties.setApiKey("test-secret");
        properties.getLanguageIds().put("python", 71);
        return properties;
    }

    private static AsyncSandboxExecutor.ExecutionRequest request(
            AsyncSandboxExecutor.Visibility visibility) {
        return request("print('ok')", visibility, "run-1:case-1");
    }

    private static AsyncSandboxExecutor.ExecutionRequest request(
            String code, AsyncSandboxExecutor.Visibility visibility, String idempotencyKey) {
        return new AsyncSandboxExecutor.ExecutionRequest(
                new SandboxJob("run-1", "user-1", "submission-1", 0L,
                        "python", code, 2, 128),
                new TestCase("case-1", "Case 1", List.of(), "ok"),
                visibility, idempotencyKey);
    }
    private static AsyncSandboxExecutor.ExecutionRequest shortRequest() {
        return new AsyncSandboxExecutor.ExecutionRequest(
                new SandboxJob("run-1", "user-1", "submission-1", 0L,
                        "python", "print('ok')", 1, 128),
                new TestCase("case-1", "Case 1", List.of(), "ok"),
                AsyncSandboxExecutor.Visibility.PUBLIC_PREVIEW, "short-run:case-1");
    }


    private static final class FakeTransport implements Judge0Transport {
        private int submitCalls;
        private Submission lastSubmission;
        private Poll poll = new Poll(Status.QUEUED, null, null, null, null, 0L, 0L);
        private String cancelledToken;
        private int pollFailures;
        private int cancelFailures;
        private final CountDownLatch cancellationAttempts = new CountDownLatch(5);

        @Override
        public String submit(Submission submission) {
            submitCalls++;
            lastSubmission = submission;
            return "token-1";
        }

        @Override
        public Poll poll(String token) {
            if (pollFailures > 0) {
                pollFailures--;
                throw new IllegalStateException("temporary poll failure");
            }
            return poll;
        }

        @Override
        public void cancel(String token) {
            if (cancelFailures > 0) {
                cancellationAttempts.countDown();
                cancelFailures--;
                throw new IllegalStateException("temporary cancel failure");
            }
            cancelledToken = token;
        }
    }
}
