package com.ulticode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * P1-INFRA-005: Admin service placeholder boot entry.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class BackendAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendAdminApplication.class, args);
    }
}
