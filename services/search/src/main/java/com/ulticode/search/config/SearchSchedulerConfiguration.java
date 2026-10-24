package com.ulticode.search.config;

import static com.ulticode.observability.scheduling.SchedulerFactory.create;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SearchSchedulerConfiguration {

    @Bean(name = "searchConsumeScheduler")
    public ThreadPoolTaskScheduler searchConsumeScheduler(
            @Value("${search.worker.scheduler.consume-pool-size:1}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("search-consume", poolSize, meterRegistry);
    }

    @Bean(name = "searchHeartbeatScheduler")
    public ThreadPoolTaskScheduler searchHeartbeatScheduler(
            @Value("${search.worker.scheduler.heartbeat-pool-size:1}") int poolSize,
            MeterRegistry meterRegistry) {
        return create("search-heartbeat", poolSize, meterRegistry);
    }

}
