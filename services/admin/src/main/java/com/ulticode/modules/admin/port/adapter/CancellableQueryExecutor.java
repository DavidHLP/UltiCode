package com.ulticode.modules.admin.port.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded, interrupt-aware executor for one Admin owner-query slice. */
public final class CancellableQueryExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;

    public CancellableQueryExecutor(String threadName, int maximumPoolSize) {
        this.executor = new ThreadPoolExecutor(
                maximumPoolSize,
                maximumPoolSize,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(maximumPoolSize),
                new NamedDaemonThreadFactory(threadName),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> Query<T> submit(Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Future<?> execution;
        try {
            execution = executor.submit(() -> {
                try {
                    result.complete(task.call());
                } catch (Error error) {
                    result.completeExceptionally(error);
                    throw error;
                } catch (Exception exception) {
                    result.completeExceptionally(exception);
                }
            });
        } catch (RejectedExecutionException rejected) {
            result.completeExceptionally(rejected);
            execution = null;
        }
        return new Query<>(result, execution);
    }

    /**
     * Wait for one query and cancel it whenever the wait cannot complete.
     * Callers remain responsible for translating the failure into their
     * domain-specific degradation result.
     */
    public <T> T await(Query<T> query, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (timeout <= 0) {
            cancel(query);
            throw new TimeoutException("query deadline exhausted");
        }
        try {
            return query.result().get(timeout, unit);
        } catch (InterruptedException | ExecutionException | TimeoutException exception) {
            cancel(query);
            throw exception;
        }
    }

    /**
     * Wait for a bounded query fan-out and cancel every query on failure.
     * This is the single cancellation boundary for Admin owner-query slices.
     */
    @SafeVarargs
    public final <T> List<T> awaitAll(long timeout, TimeUnit unit, Query<? extends T>... queries)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (timeout <= 0) {
            cancel(queries);
            throw new TimeoutException("query deadline exhausted");
        }
        CompletableFuture<?>[] results = new CompletableFuture<?>[queries.length];
        for (int index = 0; index < queries.length; index++) {
            results[index] = queries[index].result();
        }
        try {
            CompletableFuture.allOf(results).get(timeout, unit);
        } catch (InterruptedException | ExecutionException | TimeoutException exception) {
            cancel(queries);
            throw exception;
        }
        List<T> values = new ArrayList<>(queries.length);
        for (Query<? extends T> query : queries) {
            values.add(query.result().join());
        }
        return Collections.unmodifiableList(values);
    }

    @SafeVarargs
    public static void cancel(Query<?>... queries) {
        for (Query<?> query : queries) {
            // Mark the public result cancelled before interrupting the task;
            // otherwise the task can race in with completeExceptionally and
            // turn cancellation into a normal exceptional completion.
            query.result().cancel(true);
            if (query.execution() != null) {
                query.execution().cancel(true);
            }
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    public record Query<T>(CompletableFuture<T> result, Future<?> execution) {
    }

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final String name;
        private final AtomicInteger sequence = new AtomicInteger();

        private NamedDaemonThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
