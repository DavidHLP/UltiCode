package com.ulticode.submission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boot shell for the Submission owner seam.
 *
 * <p>SPLIT-002 deliberately keeps the existing App database writer behind a
 * Dubbo compatibility adapter. SPLIT-003 moves the writer and its tables here;
 * this shell already has its own process, service identity and port boundary.
 */
@SpringBootApplication(scanBasePackages = "com.ulticode.submission")
public class BackendSubmissionApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendSubmissionApplication.class, args);
    }
}
