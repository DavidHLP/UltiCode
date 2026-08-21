package com.ulticode.modules.admin.port.adapter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellableQueryExecutorCrTest {

    @Test
    void sixQueriesRunInParallelWithinDeadline() throws Exception {
        CancellableQueryExecutor executor = new CancellableQueryExecutor("cr-parallel", 6);
        try {
            long start = System.nanoTime();
            List<CompletableFuture<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    Thread.sleep(100);
                    return 1;
                }).result());
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(800, TimeUnit.MILLISECONDS);
            long ms = (System.nanoTime() - start) / 1_000_000;
            assertThat(ms).isLessThan(300);
        } finally {
            executor.close();
        }
    }

    @Test
    void fatalErrorIsNotSwallowedAsBusinessException() {
        CancellableQueryExecutor executor = new CancellableQueryExecutor("cr-fatal", 2);
        try {
            var query = executor.submit(() -> {
                throw new AssertionError("fatal-vm");
            });
            assertThatThrownBy(() -> query.result().join())
                    .hasCauseInstanceOf(AssertionError.class);
            // the worker thread should have propagated the Error, not converted to normal completion
            assertThat(query.result().isCompletedExceptionally()).isTrue();
        } finally {
            executor.close();
        }
    }
}
