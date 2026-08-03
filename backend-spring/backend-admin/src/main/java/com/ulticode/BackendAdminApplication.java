package com.ulticode;

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
 * <p>Excludes App-owned queue, config, and security adapters from the admin
 * shell. The Legacy monolith security/configuration classes were deleted by
 * P7-LEGACY-DEAD-INFRA-DELETE-001; the former regex exclusion for
 * {@code com.ulticode.common.config.SecurityConfig} is no longer needed.</p>
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.ulticode",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = QueueConfig.class),
                @ComponentScan.Filter(
                        type = FilterType.REGEX, pattern = "com\\.ulticode\\.app\\.config\\..*"),
                @ComponentScan.Filter(
                        type = FilterType.REGEX, pattern = "com\\.ulticode\\.app\\.security\\..*")
        }
)
@EnableAsync
@EnableScheduling
public class BackendAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendAdminApplication.class, args);
    }
}
