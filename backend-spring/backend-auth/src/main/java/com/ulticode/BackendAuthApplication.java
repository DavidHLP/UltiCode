package com.ulticode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * P1-INFRA-005: Auth service placeholder boot entry.
 *
 * <p>No business logic is migrated in Phase 1; this shell only proves that
 * the backend-auth module can boot independently, register a Dubbo service
 * name, expose {@code /actuator/health}, and provide a placeholder controller.
 */
@SpringBootApplication
public class BackendAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendAuthApplication.class, args);
    }
}
