package com.ulticode.modules.submission.runtime.async;

import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.sandbox.RunCaseResult;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Optional Judge0 Adapter. Vendor status codes and tokens stop at this seam.
 * Only explicitly public preview requests may reach the transport.
 */
@Component
@ConditionalOnExpression("'${judge.async.executor:docker}' == 'judge0' "
        + "&& '${judge0.enabled:false}' == 'true'")
public class Judge0AsyncSandboxAdapter implements AsyncSandboxExecutor {
    private static final int MAX_IN_FLIGHT = 1_024;
    private static final int MAX_CANCEL_ATTEMPTS = 5;
    private static final long CANCEL_RETRY_DELAY_MS = 1_000L;

    private final Judge0Transport transport;
    private final ScheduledExecutorService timeoutExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "judge0-timeout");
                thread.setDaemon(true);
                return thread;
            });
    private final Judge0Properties properties;
    private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT);
    private final Map<String, PendingExecution> executions = new ConcurrentHashMap<>();
    private final AsyncExecutionReceiptStore receipts = new AsyncExecutionReceiptStore();

    public Judge0AsyncSandboxAdapter(Judge0Transport transport, Judge0Properties properties) {
        this.transport = transport;
        this.properties = properties;
        properties.validateEnabledConfiguration();
    }

    @Override
    public ExecutionHandle submit(ExecutionRequest request) {
        requirePublicPreview(request);
        AsyncExecutionReceiptStore.Receipt existing = receipts.findByKey(
                request.idempotencyKey(), request.fingerprint());
        if (existing != null) {
            return new ExecutionHandle(existing.handle());
        }
        if (!inFlight.tryAcquire()) {
            throw new IllegalStateException("Judge0 execution queue is full");
        }
        ExecutionHandle handle = new ExecutionHandle(UUID.randomUUID().toString());
        try {
            AsyncExecutionReceiptStore.Receipt raced = receipts.register(
                    request.idempotencyKey(), request.fingerprint(), handle.id());
            if (raced != null) {
                inFlight.release();
                return new ExecutionHandle(raced.handle());
            }
            int languageId = properties.languageId(request.job().languageId());
            String token = transport.submit(new Judge0Transport.Submission(
                    languageId,
                    request.job().code(),
                    toStdin(request.testCase()),
                    request.testCase().expectedOutput(),
                    request.job().timeoutSeconds() * 1_000L,
                    memoryLimitKb(request.job().memoryMb()),
                    properties.getMaxOutputBytes()));
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Judge0 returned no submission token");
            }
            PendingExecution pending = new PendingExecution(
                    token, request, System.nanoTime());
            executions.put(handle.id(), pending);
            timeoutExecutor.schedule(
                    () -> expire(handle.id(), pending),
                    maxExecutionMs(request), TimeUnit.MILLISECONDS);
            return handle;
        } catch (RuntimeException exception) {
            inFlight.release();
            receipts.complete(request.idempotencyKey(), handle.id(),
                    ExecutionSnapshot.failed("Judge0 submission failed"));
            throw exception;
        }
    }

    @Override
    public ExecutionSnapshot poll(ExecutionHandle handle) {
        AsyncExecutionReceiptStore.Receipt terminal = receipts.findByHandle(handle.id());
        if (terminal != null && terminal.snapshot() != null) {
            return terminal.snapshot();
        }
        PendingExecution pending = executions.get(handle.id());
        if (pending == null) {
            return ExecutionSnapshot.failed("execution handle is unknown or expired");
        }
        if (elapsedMsSinceSubmit(pending) > maxExecutionMs(pending.request())) {
            expire(handle.id(), pending);
            return ExecutionSnapshot.timedOut("Judge0 execution deadline exceeded");
        }

        Judge0Transport.Poll poll;
        try {
            poll = transport.poll(pending.token());
        } catch (RuntimeException exception) {
            // Keep the token live so a later poll/cancel can recover from a
            // transient transport failure.
            return ExecutionSnapshot.failed("Judge0 is unavailable");
        }
        if (poll == null || poll.status() == null) {
            if (!removeExecution(handle.id(), pending)) {
                return terminalSnapshot(handle);
            }
            ExecutionSnapshot snapshot = ExecutionSnapshot.failed(
                    "Judge0 returned an invalid poll response");
            receipts.complete(pending.request().idempotencyKey(), handle.id(), snapshot);
            return snapshot;
        }
        if (poll.status() == Judge0Transport.Status.QUEUED) {
            return ExecutionSnapshot.queued();
        }
        if (poll.status() == Judge0Transport.Status.RUNNING) {
            return ExecutionSnapshot.running();
        }

        if (!removeExecution(handle.id(), pending)) {
            return terminalSnapshot(handle);
        }
        String stdout = boundedText(poll.stdout(), properties.getMaxOutputBytes());
        String stderr = boundedText(poll.stderr(), properties.getMaxOutputBytes());
        ExecutionSnapshot snapshot;
        if (outputBytes(poll.stdout()) > properties.getMaxOutputBytes()
                || outputBytes(poll.stderr()) > properties.getMaxOutputBytes()) {
            snapshot = ExecutionSnapshot.completed(RunCaseResult.rejectedWithOutput(
                    SubmissionStatus.OUTPUT_LIMIT_EXCEEDED,
                    "Judge0 output exceeded the configured limit",
                    poll.elapsedMs(), poll.memoryBytes(), stdout,
                    pending.request().testCase().expectedOutput(),
                    pending.request().testCase().inputs()));
        } else {
            SubmissionStatus status = toStatus(poll.status());
            String detail = boundedText(
                    firstText(poll.compileOutput(), stderr, poll.message()),
                    properties.getMaxOutputBytes());
            RunCaseResult result = status == SubmissionStatus.ACCEPTED
                    ? RunCaseResult.acceptedWithOutput(
                            poll.elapsedMs(), poll.memoryBytes(), stdout,
                            pending.request().testCase().expectedOutput(),
                            pending.request().testCase().inputs())
                    : RunCaseResult.rejectedWithOutput(
                            status, detail, poll.elapsedMs(), poll.memoryBytes(), stdout,
                            pending.request().testCase().expectedOutput(),
                            pending.request().testCase().inputs());
            snapshot = ExecutionSnapshot.completed(result);
        }
        receipts.complete(pending.request().idempotencyKey(), handle.id(), snapshot);
        return snapshot;
    }
    @Override
    public void cancel(ExecutionHandle handle) {
        PendingExecution pending = executions.get(handle.id());
        if (pending == null) {
            return;
        }
        if (!cancelRemote(pending.token())) {
            throw new IllegalStateException("Judge0 cancellation pending");
        }
        if (!removeExecution(handle.id(), pending)) {
            return;
        }
        receipts.complete(pending.request().idempotencyKey(), handle.id(),
                ExecutionSnapshot.cancelled());
    }

    private static void requirePublicPreview(ExecutionRequest request) {
        if (request.visibility() != Visibility.PUBLIC_PREVIEW) {
            throw new IllegalArgumentException(
                    "Judge0 is restricted to PUBLIC_PREVIEW executions");
        }
    }

    private ExecutionSnapshot terminalSnapshot(ExecutionHandle handle) {
        AsyncExecutionReceiptStore.Receipt receipt = receipts.findByHandle(handle.id());
        return receipt == null || receipt.snapshot() == null
                ? ExecutionSnapshot.failed("execution completed concurrently")
                : receipt.snapshot();
    }

    private void expire(String id, PendingExecution pending) {
        expire(id, pending, 1);
    }

    private void expire(String id, PendingExecution pending, int cancelAttempts) {
        if (!cancelRemote(pending.token())) {
            if (cancelAttempts >= MAX_CANCEL_ATTEMPTS) {
                if (removeExecution(id, pending)) {
                    receipts.complete(pending.request().idempotencyKey(), id,
                            ExecutionSnapshot.failed(
                                    "Judge0 execution cancellation failed after retries"));
                }
                return;
            }
            timeoutExecutor.schedule(
                    () -> expire(id, pending, cancelAttempts + 1),
                    CANCEL_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
            return;
        }
        if (!removeExecution(id, pending)) {
            return;
        }
        receipts.complete(pending.request().idempotencyKey(), id,
                ExecutionSnapshot.timedOut("Judge0 execution deadline exceeded"));
    }
    private boolean cancelRemote(String token) {
        try {
            transport.cancel(token);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean removeExecution(String id, PendingExecution pending) {
        if (executions.remove(id, pending)) {
            inFlight.release();
            return true;
        }
        return false;
    }


    @PreDestroy
    void shutdown() {
        timeoutExecutor.shutdownNow();
    }

    private static String toStdin(com.ulticode.modules.submission.sandbox.TestCase testCase) {
        if (testCase.inputs() == null || testCase.inputs().isEmpty()) {
            return "";
        }
        return testCase.inputs().stream()
                .map(input -> input == null || input.value() == null ? "" : input.value())
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
    }

    private static SubmissionStatus toStatus(Judge0Transport.Status status) {
        return switch (status) {
            case ACCEPTED -> SubmissionStatus.ACCEPTED;
            case WRONG_ANSWER -> SubmissionStatus.WRONG_ANSWER;
            case TIME_LIMIT_EXCEEDED -> SubmissionStatus.TIME_LIMIT_EXCEEDED;
            case MEMORY_LIMIT_EXCEEDED -> SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
            case COMPILE_ERROR -> SubmissionStatus.COMPILE_ERROR;
            case RUNTIME_ERROR -> SubmissionStatus.RUNTIME_ERROR;
            case OUTPUT_LIMIT_EXCEEDED -> SubmissionStatus.OUTPUT_LIMIT_EXCEEDED;
            case FAILED, QUEUED, RUNNING -> SubmissionStatus.SANDBOX_ERROR;
        };
    }

    private static int outputBytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String boundedText(String value, int maxBytes) {
        if (value == null || maxBytes < 1) {
            return value == null ? null : "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        int prefixLength = utf8PrefixLength(bytes, maxBytes);
        return new String(bytes, 0, prefixLength, StandardCharsets.UTF_8);
    }

    private static int utf8PrefixLength(byte[] bytes, int maxBytes) {
        int end = Math.min(bytes.length, maxBytes);
        if (end == bytes.length) {
            return end;
        }
        int start = end - 1;
        while (start >= 0 && (bytes[start] & 0xC0) == 0x80) {
            start--;
        }
        if (start < 0) {
            return 0;
        }
        int lead = bytes[start] & 0xFF;
        int width = (lead & 0x80) == 0 ? 1
                : (lead & 0xE0) == 0xC0 ? 2
                : (lead & 0xF0) == 0xE0 ? 3
                : (lead & 0xF8) == 0xF0 ? 4 : 1;
        return end - start < width ? start : end;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "Judge0 execution failed";
    }

    private long elapsedMsSinceSubmit(PendingExecution pending) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                Math.max(0L, System.nanoTime() - pending.submittedAtNanos()));
    }

    private long maxExecutionMs(ExecutionRequest request) {
        long seconds = Math.max(1L, request.job().timeoutSeconds());
        long executionMs = seconds > Long.MAX_VALUE / 1_000L
                ? Long.MAX_VALUE : seconds * 1_000L;
        long requestTimeoutMs = properties.getRequestTimeoutMs();
        return executionMs > Long.MAX_VALUE - requestTimeoutMs
                ? Long.MAX_VALUE : executionMs + requestTimeoutMs;
    }
    private static long memoryLimitKb(int memoryMb) {
        if (memoryMb < 1) {
            throw new IllegalArgumentException("memory limit must be positive");
        }
        return memoryMb * 1_024L;
    }

    private record PendingExecution(
            String token, ExecutionRequest request, long submittedAtNanos) {
    }
}
