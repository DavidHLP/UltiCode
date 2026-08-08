package com.ulticode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
/**
 * P1-INFRA-005: App service placeholder boot entry.
 *
 * <p>Mapper scanning lives in {@code app.config.MapperScanConfig} so the test
 * profile can exclude database-backed mapper factories while production
 * profiles still scan every App-owned mapper package.</p>
 */
@EnableScheduling
@SpringBootApplication
public class BackendAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendAppApplication.class, args);
    }
}
