package com.ulticode.modules.submission.runtime.async;

import com.ulticode.modules.submission.sandbox.RunCaseResult;
import com.ulticode.modules.submission.sandbox.SandboxExecutor;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Async Adapter that keeps the existing Docker SandboxExecutor as the default. */
@Component
@ConditionalOnProperty(name = "judge.async.executor", havingValue = "docker", matchIfMissing = true)
public class DockerAsyncSandboxAdapter implements AsyncSandboxExecutor {

    private static final int WORKERS = 4;
    private static final int QUEUE_CAPACITY = 32;
    private static final long RECEIPT_TTL_MS = TimeUnit.MINUTES.toMillis(15);

    private final SandboxExecutor delegate;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService reaper =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "docker-async-reaper");
                thread.setDaemon(true);
                return thread;
            });
    private final Semaphore inFlight = new Semaphore(WORKERS + QUEUE_CAPACITY);
    private final Map<String, Future<RunCaseResult>> executions = new HashMap<>();
    private final Map<String, String> handleKeys = new HashMap<>();
    private final AsyncExecutionReceiptStore receipts = new AsyncExecutionReceiptStore();

    public DockerAsyncSandboxAdapter(SandboxExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public ExecutionHandle submit(ExecutionRequest request) {
        AsyncExecutionReceiptStore.Receipt existing = receipts.findByKey(
                request.idempotencyKey(), request.fingerprint());
        if (existing != null) {
            return new ExecutionHandle(existing.handle());
        }
        if (!inFlight.tryAcquire()) {
            throw new RejectedExecutionException("Docker execution queue is full");
        }
        ExecutionHandle handle = new ExecutionHandle(UUID.randomUUID().toString());
        try {
            AsyncExecutionReceiptStore.Receipt raced = receipts.register(
                    request.idempotencyKey(), request.fingerprint(), handle.id());
            if (raced != null) {
                inFlight.release();
                return new ExecutionHandle(raced.handle());
            }
        } catch (RuntimeException exception) {
            inFlight.release();
            throw exception;
        }

        Future<RunCaseResult> future;
        try {
            future = executor.submit(
                    () -> delegate.run(request.job(), request.testCase()));
        } catch (RejectedExecutionException exception) {
            inFlight.release();
            receipts.complete(request.idempotencyKey(), handle.id(),
                    ExecutionSnapshot.failed("Docker execution queue is full"));
            return handle;
        } catch (RuntimeException exception) {
            inFlight.release();
            receipts.complete(request.idempotencyKey(), handle.id(),
                    ExecutionSnapshot.failed("Docker execution submission failed"));
            throw exception;
        }

        synchronized (this) {
            executions.put(handle.id(), future);
            handleKeys.put(handle.id(), request.idempotencyKey());
        }
        try {
            reaper.schedule(
                    () -> reap(handle.id(), future, request.idempotencyKey()),
                    RECEIPT_TTL_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            String key = removeExecution(handle.id(), future);
            if (key != null) {
                future.cancel(true);
                receipts.complete(key, handle.id(),
                        ExecutionSnapshot.failed("Docker execution reaper unavailable"));
            }
        }
        return handle;
    }

    @Override
    public ExecutionSnapshot poll(ExecutionHandle handle) {
        AsyncExecutionReceiptStore.Receipt terminal = receipts.findByHandle(handle.id());
        if (terminal != null && terminal.snapshot() != null) {
            return terminal.snapshot();
        }
        Future<RunCaseResult> future;
        synchronized (this) {
            future = executions.get(handle.id());
        }
        if (future == null) {
            return ExecutionSnapshot.failed("execution handle is unknown or expired");
        }
        if (!future.isDone()) {
            return ExecutionSnapshot.running();
        }
        ExecutionSnapshot snapshot;
        try {
            snapshot = ExecutionSnapshot.completed(future.get());
        } catch (CancellationException exception) {
            snapshot = ExecutionSnapshot.cancelled();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            snapshot = ExecutionSnapshot.failed("execution poll interrupted");
        } catch (ExecutionException exception) {
            snapshot = ExecutionSnapshot.failed(exception.getCause() == null
                    ? "Docker execution failed" : exception.getCause().getMessage());
        }
        String key = removeExecution(handle.id(), future);
        if (key == null) {
            return terminalSnapshot(handle);
        }
        receipts.complete(key, handle.id(), snapshot);
        return snapshot;
    }

    @Override
    public void cancel(ExecutionHandle handle) {
        Future<RunCaseResult> future;
        String key;
        synchronized (this) {
            future = executions.remove(handle.id());
            key = handleKeys.remove(handle.id());
            if (future != null) {
                inFlight.release();
            }
        }
        if (future != null) {
            future.cancel(true);
        }
        if (key != null) {
            receipts.complete(key, handle.id(), ExecutionSnapshot.cancelled());
        }
    }

    private String removeExecution(String id, Future<RunCaseResult> future) {
        synchronized (this) {
            if (!executions.remove(id, future)) {
                return null;
            }
            String key = handleKeys.remove(id);
            inFlight.release();
            return key;
        }
    }

    private ExecutionSnapshot terminalSnapshot(ExecutionHandle handle) {
        AsyncExecutionReceiptStore.Receipt receipt = receipts.findByHandle(handle.id());
        return receipt == null || receipt.snapshot() == null
                ? ExecutionSnapshot.failed("execution completed concurrently")
                : receipt.snapshot();
    }

    private void reap(String id, Future<RunCaseResult> future, String key) {
        if (removeExecution(id, future) == null) {
            return;
        }
        ExecutionSnapshot snapshot;
        if (!future.isDone()) {
            future.cancel(true);
            snapshot = ExecutionSnapshot.failed("Docker execution receipt expired");
        } else {
            try {
                snapshot = ExecutionSnapshot.completed(future.get());
            } catch (CancellationException exception) {
                snapshot = ExecutionSnapshot.cancelled();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                snapshot = ExecutionSnapshot.failed("Docker execution reap interrupted");
            } catch (ExecutionException exception) {
                snapshot = ExecutionSnapshot.failed("Docker execution failed");
            }
        }
        receipts.complete(key, id, snapshot);
    }

    @PreDestroy
    void shutdown() {
        reaper.shutdownNow();
        executor.shutdownNow();
    }
}
