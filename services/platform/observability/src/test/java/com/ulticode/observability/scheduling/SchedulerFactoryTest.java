package com.ulticode.observability.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class SchedulerFactoryTest {

    @Test
    void keepsNamedPoolPoliciesAndRejectionMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            var scheduler = SchedulerFactory.create("owner-work", 2, registry);
            try {
                var executor = scheduler.getScheduledThreadPoolExecutor();
                assertThat(executor.getCorePoolSize()).isEqualTo(2);
                assertThat(scheduler.getThreadNamePrefix()).isEqualTo("owner-work-");
                assertThat(executor.getRemoveOnCancelPolicy()).isTrue();
                assertThat(executor.getContinueExistingPeriodicTasksAfterShutdownPolicy()).isFalse();
                assertThat(executor.getExecuteExistingDelayedTasksAfterShutdownPolicy()).isFalse();
                assertThat(registry.find("executor.active")
                        .tag("scheduler", "owner-work").gauge()).isNotNull();

                scheduler.shutdown();

                assertThatThrownBy(() -> executor.execute(() -> { }))
                        .isInstanceOf(RejectedExecutionException.class)
                        .hasMessage("scheduler rejected task: owner-work");
                assertThat(registry.get("ulticode.scheduler.rejected")
                        .tag("scheduler", "owner-work").counter().count()).isEqualTo(1);
            } finally {
                scheduler.shutdown();
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void rejectsInvalidPoolSizesWithTheOwnerName() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            for (int poolSize : new int[] {0, 17}) {
                assertThatThrownBy(() -> SchedulerFactory.create("owner-work", poolSize, registry))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("scheduler pool size must be between 1 and 16: owner-work");
            }
            assertThat(registry.getMeters()).isEmpty();
        } finally {
            registry.close();
        }
    }
}
