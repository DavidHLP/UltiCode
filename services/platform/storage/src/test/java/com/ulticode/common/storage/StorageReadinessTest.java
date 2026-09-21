package com.ulticode.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StorageReadiness")
class StorageReadinessTest {

    @Test
    @DisplayName("shares one asynchronous recovery probe across concurrent checks")
    void sharesOneRecoveryProbe() throws Exception {
        StorageReadiness readiness = new StorageReadiness();
        readiness.markFailed("object store unavailable");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        readiness.setRecoveryProbe(() -> {
            calls.incrementAndGet();
            started.countDown();
            try {
                if (!release.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("recovery probe was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("recovery probe interrupted", exception);
            }
            readiness.markReady();
            finished.countDown();
        });

        readiness.probeIfFailed();
        readiness.probeIfFailed();

        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(calls).hasValue(1);

        release.countDown();
        assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(readiness.isReady()).isTrue();
    }

    @Test
    @DisplayName("does not let a stale recovery failure downgrade a ready store")
    void staleRecoveryFailureDoesNotDowngradeReadyState() throws Exception {
        StorageReadiness readiness = new StorageReadiness();
        readiness.markFailed("object store unavailable");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        readiness.setRecoveryProbe(() -> {
            started.countDown();
            try {
                if (!release.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("recovery probe was not released");
                }
                throw new IllegalStateException("stale recovery failure");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("recovery probe interrupted", exception);
            } finally {
                finished.countDown();
            }
        });

        readiness.probeIfFailed();
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        readiness.markReady();
        release.countDown();

        assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50);
        assertThat(readiness.state()).isEqualTo(StorageReadiness.State.READY);
        assertThat(readiness.detail()).isNull();
    }
}
