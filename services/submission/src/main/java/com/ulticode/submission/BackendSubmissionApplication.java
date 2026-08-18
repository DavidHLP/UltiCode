package com.ulticode.submission;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Boot shell for the Submission owner runtime.
 *
 * <p>SPLIT-003 adds the local storage writer and direct owner providers:
 * the same submission domain classes (entity/mapper/outbox/result/stats)
 * are scanned from {@code com.ulticode.modules.submission}, and the
 * {@code submission} schema tables are written in one local transaction.
 * SPLIT-003 slice-3/7 adds the local outbox consumers
 * ({@code JudgeOutboxDispatcher}, {@code SubmissionResultDispatcher}, and
 * {@code SubmissionCreatedDispatcher}).
 * App routing stays {@code local} until the separately authorized runtime
 * cutover; after that cutover this process remains the sole Submission writer.
 */
@SpringBootApplication(scanBasePackages = "com.ulticode.submission")
@EnableScheduling
@ComponentScan(basePackages = {
        "com.ulticode.submission",
        "com.ulticode.modules.submission",
        "com.ulticode.modules.queue"
})
@MapperScan({"com.ulticode.modules.submission.mapper",
        "com.ulticode.modules.submission.outbox.mapper",
        "com.ulticode.modules.submission.result",
        "com.ulticode.modules.submission.created"})
public class BackendSubmissionApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendSubmissionApplication.class, args);
    }
}
