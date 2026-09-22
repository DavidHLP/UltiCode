package com.ulticode.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Owns the executor used by the Admin backup lifecycle.
 *
 * <p>Backup execution is deliberately isolated from the default async pool so
 * service shutdown can wait for dump/upload/state-transition work before the
 * legacy metadata cutover starts.</p>
 */
@Configuration(proxyBeanMethods = false)
public class AdminBackupExecutorConfiguration {

    @Bean(name = "adminBackupExecutor")
    public ThreadPoolTaskExecutor adminBackupExecutor(
            @Value("${admin.backup.executor.core-pool-size:1}") int corePoolSize,
            @Value("${admin.backup.executor.max-pool-size:1}") int maxPoolSize,
            @Value("${admin.backup.executor.queue-capacity:0}") int queueCapacity,
            @Value("${admin.backup.executor.await-termination-seconds:3600}") int awaitTerminationSeconds) {
        if (corePoolSize < 1 || maxPoolSize < corePoolSize || queueCapacity < 0
                || awaitTerminationSeconds < 1) {
            throw new IllegalArgumentException(
                    "invalid Admin backup executor bounds: core=" + corePoolSize
                            + ", max=" + maxPoolSize
                            + ", queue=" + queueCapacity
                            + ", awaitTerminationSeconds=" + awaitTerminationSeconds);
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("admin-backup-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.setAcceptTasksAfterContextClose(false);
        executor.initialize();
        return executor;
    }
}
