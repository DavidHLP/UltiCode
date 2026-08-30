package com.ulticode.admin.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.core.task.TaskRejectedException;

class AdminSchedulerConfigurationTest {

    @Test
    void blockedBackupDoesNotStarveReconciliationAndShutdownRejectsNewWork()
            throws Exception {
        AdminSchedulerConfiguration configuration = new AdminSchedulerConfiguration();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskScheduler backup = configuration.adminBackupScheduler(1, registry);
        ThreadPoolTaskScheduler reconciliation =
                configuration.adminReconciliationScheduler(1, registry);
        CountDownLatch backupStarted = new CountDownLatch(1);
        CountDownLatch releaseBackup = new CountDownLatch(1);
        CountDownLatch reconciliationProgress = new CountDownLatch(1);

        try {
            backup.getScheduledExecutor().submit(() -> {
                backupStarted.countDown();
                try {
                    releaseBackup.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(backupStarted.await(2, TimeUnit.SECONDS)).isTrue();

            reconciliation.schedule(
                    reconciliationProgress::countDown, Instant.now().plusMillis(20));
            assertThat(reconciliationProgress.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.find("executor.active")
                    .tags("scheduler", "admin-reconciliation")
                    .gauge()).isNotNull();

            releaseBackup.countDown();
            backup.shutdown();
            assertThatThrownBy(() -> backup.schedule(() -> { }, Instant.now()))
                    .isInstanceOf(TaskRejectedException.class);
            assertThatThrownBy(() -> backup.getScheduledExecutor().execute(() -> { }))
                    .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            assertThat(registry.get("ulticode.scheduler.rejected")
                    .tag("scheduler", "admin-backup")
                    .counter().count()).isPositive();
        } finally {
            releaseBackup.countDown();
            backup.shutdown();
            reconciliation.shutdown();
            registry.close();
        }
    }
}
