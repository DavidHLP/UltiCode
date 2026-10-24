package com.ulticode.admin.config;

import static com.ulticode.observability.scheduling.SchedulerFactory.create;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AdminSchedulerConfiguration {

    @Bean(name = "adminAuditScheduler")
    public ThreadPoolTaskScheduler adminAuditScheduler(
            @Value("${admin.scheduler.audit-pool-size:2}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("admin-audit", poolSize, meterRegistry);
    }

    @Bean(name = "adminReconciliationScheduler")
    public ThreadPoolTaskScheduler adminReconciliationScheduler(
            @Value("${admin.scheduler.reconciliation-pool-size:1}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("admin-reconciliation", poolSize, meterRegistry);
    }

    @Bean(name = "adminBackupScheduler")
    public ThreadPoolTaskScheduler adminBackupScheduler(
            @Value("${admin.scheduler.backup-pool-size:1}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("admin-backup", poolSize, meterRegistry);
    }

}
