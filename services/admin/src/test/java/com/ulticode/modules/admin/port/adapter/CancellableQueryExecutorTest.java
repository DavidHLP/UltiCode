package com.ulticode.modules.admin.port.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
}
