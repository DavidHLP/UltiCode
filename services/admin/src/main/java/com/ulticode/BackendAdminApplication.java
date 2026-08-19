package com.ulticode;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Admin service boot entry.
 *
 * <p>The scan is explicitly bounded to Admin, backup, reconciliation, common
 * and the service's own security packages. App/legacy/judge components are
 * outside the scan boundary, so the boot class no longer needs a growing list
 * of regex exclusions.</p>
 *
 * <p>Admin reaches App/Submission through Contract adapters. It must not
 * instantiate App-owned queue, sandbox, or judge processors in the Admin
 * process, because those scheduled workers would compete for the same Redis
 * streams.</p>
 *
 * <p>The explicit {@link MapperScan} is required so only Admin, backup and
 * reconciliation persistence is registered. App-owned and legacy mapper
 * packages are intentionally outside the bounded component scan.</p>
 *
 * <p>Admin-owned adapters formerly misplaced under legacy packages are now
 * under {@code com.ulticode.modules.admin}; no legacy auth/user package is
 * scanned.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
                "com.ulticode.admin",
                "com.ulticode.modules.admin",
                "com.ulticode.modules.backup",
                "com.ulticode.modules.reconciliation",
                "com.ulticode.common"
        }
)
@MapperScan({
        "com.ulticode.modules.admin.mapper",
        "com.ulticode.modules.admin.outbox.mapper",
        "com.ulticode.modules.backup.mapper",
        "com.ulticode.modules.reconciliation",
})
@EnableAsync
@EnableScheduling
public class BackendAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendAdminApplication.class, args);
    }
}
