package com.ulticode;

import com.ulticode.common.config.SecurityConfig;
import com.ulticode.modules.queue.config.QueueConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * P1-INFRA-005: Admin service placeholder boot entry.
 *
 * <p>Excludes legacy configurations that conflict with admin-specific ones
 * or require infrastructure not available in the admin shell
 * (P7-RELOCATE-ADMIN-001).
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.ulticode",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = QueueConfig.class)
        }
)
@EnableAsync
@EnableScheduling
public class BackendAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendAdminApplication.class, args);
    }
}
