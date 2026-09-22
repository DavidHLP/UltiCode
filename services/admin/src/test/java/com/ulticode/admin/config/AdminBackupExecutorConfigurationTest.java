package com.ulticode.admin.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.modules.backup.service.impl.BackupExecutionServiceImpl;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AdminBackupExecutorConfigurationTest {

    @Test
    void backupExecutorWaitsForRunningWorkDuringShutdown() throws Exception {
        AdminBackupExecutorConfiguration configuration = new AdminBackupExecutorConfiguration();
        ThreadPoolTaskExecutor executor = configuration.adminBackupExecutor(1, 1, 0, 2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        Thread shutdown = null;
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            CountDownLatch shutdownReturned = new CountDownLatch(1);
            shutdown = new Thread(() -> {
                executor.shutdown();
                shutdownReturned.countDown();
            });
            shutdown.start();
            assertThat(shutdownReturned.await(200, TimeUnit.MILLISECONDS)).isFalse();

            release.countDown();
            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(shutdownReturned.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            if (shutdown != null) {
                shutdown.join(2_000);
            }
            executor.shutdown();
        }
    }

    @Test
    void backupLifecycleUsesTheDrainedExecutor() {
        Async async = BackupExecutionServiceImpl.class.getAnnotation(Async.class);

        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("adminBackupExecutor");
    }
}
