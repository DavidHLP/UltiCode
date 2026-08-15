package com.ulticode.notification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Independent notification/email owner runtime. */
@SpringBootApplication(scanBasePackages = {
        "com.ulticode.notification",
        "com.ulticode.modules.notification",
        "com.ulticode.modules.email"
})
@MapperScan({
        "com.ulticode.modules.notification.mapper",
        "com.ulticode.modules.notification.ledger.mapper",
        "com.ulticode.modules.email.mapper",
        "com.ulticode.notification.idempotency.mapper",
        "com.ulticode.modules.event.inbox"
})
@EnableScheduling
public class BackendNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendNotificationApplication.class, args);
    }
}
