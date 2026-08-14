package com.ulticode.judge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Boot entry for the independent judge execution runtime. */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.ulticode.judge")
public class BackendJudgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendJudgeApplication.class, args);
    }
}
