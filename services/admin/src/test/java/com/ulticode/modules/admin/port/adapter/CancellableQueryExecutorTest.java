package com.ulticode.modules.admin.port.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class CancellableQueryExecutorTest {

    @Test
    void cancelInterruptsTheRunningOwnerTask() throws Exception {
        CancellableQueryExecutor executor = new CancellableQueryExecutor("test-query", 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        try {
            CancellableQueryExecutor.Query<String> query = executor.submit(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    throw exception;
                }
                return "never";
            });

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            CancellableQueryExecutor.cancel(query);

            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(query.result()).isCancelled();
        } finally {
            executor.close();
        }
    }

    @Test
    void rejectsBeyondTheBoundedRunningAndQueuedCapacity() throws Exception {
        CancellableQueryExecutor executor = new CancellableQueryExecutor("test-query", 1);
        CountDownLatch started = new CountDownLatch(1);
        try {
            CancellableQueryExecutor.Query<String> running = executor.submit(() -> {
                started.countDown();
                new CountDownLatch(1).await();
                return "never";
            });
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            CancellableQueryExecutor.Query<String> queued = executor.submit(() -> "queued");
            CancellableQueryExecutor.Query<String> rejected = executor.submit(() -> "rejected");

            assertThat(rejected.result()).isCompletedExceptionally();
            CancellableQueryExecutor.cancel(running, queued);
            assertThatCode(executor::close).doesNotThrowAnyException();
        } finally {
            executor.close();
        }
    }

    @Test
    void awaitAllCancelsEveryQueryWhenTheSharedDeadlineExpires() throws Exception {
        CancellableQueryExecutor executor = new CancellableQueryExecutor("test-await", 2);
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch interrupted = new CountDownLatch(2);
        try {
            CancellableQueryExecutor.Query<String> first = blockingQuery(executor, started, interrupted);
            CancellableQueryExecutor.Query<String> second = blockingQuery(executor, started, interrupted);

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> executor.awaitAll(10, TimeUnit.MILLISECONDS, first, second))
                    .isInstanceOf(TimeoutException.class);

            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(first.result()).isCancelled();
            assertThat(second.result()).isCancelled();
        } finally {
            executor.close();
        }
    }

    @Test
    void awaitAllRejectsAnAlreadyCompletedQueryWhenTheDeadlineIsExhausted() throws Exception {
        CancellableQueryExecutor executor = new CancellableQueryExecutor("test-expired", 1);
        try {
            CancellableQueryExecutor.Query<String> query = executor.submit(() -> "done");
            assertThat(query.result().get(1, TimeUnit.SECONDS)).isEqualTo("done");

            assertThatThrownBy(() -> executor.awaitAll(0, TimeUnit.NANOSECONDS, query))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            executor.close();
        }
    }

    @Test
    void awaitAllPreservesNullResults() throws Exception {
        CancellableQueryExecutor executor = new CancellableQueryExecutor("test-null", 1);
        try {
            CancellableQueryExecutor.Query<String> query = executor.submit(() -> null);

            List<String> values = executor.awaitAll(1, TimeUnit.SECONDS, query);

            assertThat(values).containsExactly((String) null);
            assertThatThrownBy(() -> values.add("unexpected"))
                    .isInstanceOf(UnsupportedOperationException.class);
        } finally {
            executor.close();
        }
    }

    private static CancellableQueryExecutor.Query<String> blockingQuery(
            CancellableQueryExecutor executor,
            CountDownLatch started,
            CountDownLatch interrupted) {
        return executor.submit(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                throw exception;
            }
            return "never";
        });
    }
}
