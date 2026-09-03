package com.ulticode.modules.admin.port.adapter;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
