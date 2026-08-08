package com.ulticode.common.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MetricsCollector}.
 */
class MetricsCollectorTest {

    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
    }

    @Test
    @DisplayName("new collector starts with zero counters")
    void newCollectorStartsAtZero() {
        assertEquals(0L, metricsCollector.getQueryCount());
        assertEquals(0L, metricsCollector.getSlowQueryCount());
    }

    @Test
    @DisplayName("incrementQuery raises the query counter by 1")
    void incrementQueryRaisesQueryCount() {
        metricsCollector.incrementQuery();
        metricsCollector.incrementQuery();
        metricsCollector.incrementQuery();
        assertEquals(3L, metricsCollector.getQueryCount());
    }

    @Test
    @DisplayName("incrementSlowQuery raises the slow-query counter by 1")
    void incrementSlowQueryRaisesSlowCount() {
        metricsCollector.incrementSlowQuery();
        assertEquals(1L, metricsCollector.getSlowQueryCount());
    }

    @Test
    @DisplayName("query and slow-query counters are independent")
    void countersAreIndependent() {
        metricsCollector.incrementQuery();
        metricsCollector.incrementQuery();
        metricsCollector.incrementSlowQuery();
        assertEquals(2L, metricsCollector.getQueryCount());
        assertEquals(1L, metricsCollector.getSlowQueryCount());
    }

    @Test
    @DisplayName("concurrent increments are atomic — no lost updates")
    void concurrentIncrementsAreAtomic() throws InterruptedException {
        int threads = 50;
        int incrementsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        metricsCollector.incrementQuery();
                    }
                });
            }
            executor.shutdown();
            boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);
            assertTrue(finished, "executor should finish within 30s");
        } finally {
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
        }
        // If AtomicLong were replaced with plain long, concurrent updates
        // would lose increments and the total would be < threads*incrementsPerThread.
        assertEquals((long) threads * incrementsPerThread, metricsCollector.getQueryCount());
    }
}
