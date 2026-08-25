package com.ulticode.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkerSloMeters shared worker SLO meter set")
class WorkerSloMetersTest {

    @Test
    @DisplayName("gauges start UNKNOWN and reflect updates")
    void gaugesStartUnknownAndReflectUpdates() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WorkerSloMeters slo = WorkerSloMeters.register(registry, "test.worker");

        assertThat(slo.getQueueLag()).isEqualTo(WorkerSloMeters.UNKNOWN);
        assertThat(slo.getPelSize()).isEqualTo(WorkerSloMeters.UNKNOWN);
        assertThat(slo.getDlqSize()).isEqualTo(WorkerSloMeters.UNKNOWN);
        assertThat(slo.getLastSuccessEpochMs()).isZero();

        slo.setQueueLag(7);
        slo.setPelSize(3);
        slo.setPelOldestAgeSeconds(42);
        slo.setDlqSize(1);

        assertThat(slo.getQueueLag()).isEqualTo(7);
        assertThat(slo.getPelSize()).isEqualTo(3);
        assertThat(slo.getPelOldestAgeSeconds()).isEqualTo(42);
        assertThat(slo.getDlqSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("meters are visible in the registry under the given prefix")
    void metersAreRegisteredUnderPrefix() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WorkerSloMeters.register(registry, "test.worker");

        assertThat(registry.get("test.worker.queue.lag").gauge().value())
                .isEqualTo(WorkerSloMeters.UNKNOWN);
        assertThat(registry.get("test.worker.queue.pel.size").gauge()).isNotNull();
        assertThat(registry.get("test.worker.queue.pel.oldest.age.seconds").gauge()).isNotNull();
        assertThat(registry.get("test.worker.queue.dlq.size").gauge()).isNotNull();
        assertThat(registry.get("test.worker.last.success.timestamp").gauge()).isNotNull();
        assertThat(registry.get("test.worker.consume.failures").counter().count()).isZero();
    }

    @Test
    @DisplayName("markSuccess records epoch millis and failures accumulate")
    void successAndFailures() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WorkerSloMeters slo = WorkerSloMeters.register(registry, "test.worker");

        long before = System.currentTimeMillis();
        slo.markSuccess();
        assertThat(slo.getLastSuccessEpochMs()).isBetween(before, System.currentTimeMillis());

        slo.incrementFailures();
        slo.incrementFailures();
        assertThat(registry.get("test.worker.consume.failures").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("re-registering the same prefix reuses existing gauges")
    void reRegistrationIsIdempotent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WorkerSloMeters first = WorkerSloMeters.register(registry, "test.worker");
        WorkerSloMeters second = WorkerSloMeters.register(registry, "test.worker");

        first.setQueueLag(11);
        assertThat(second.getQueueLag()).isEqualTo(11);
    }
}
