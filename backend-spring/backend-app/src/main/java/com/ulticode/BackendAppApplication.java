package com.ulticode;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * P1-INFRA-005: App service placeholder boot entry.
 *
 * <p>{@code @MapperScan} is placed directly on the boot class (not delegated to
 * {@code MapperScanConfig}) because the profile-gated config class was not
 * reliably activating under PM2-launched {@code spring-boot:run}.
 */
@SpringBootApplication
@MapperScan({
        "com.ulticode.modules.follow.mapper",
        "com.ulticode.modules.bookmark.mapper",
        "com.ulticode.modules.solution.mapper",
        "com.ulticode.modules.forum.mapper",
        "com.ulticode.modules.problem.mapper",
        "com.ulticode.modules.contest.mapper",
        "com.ulticode.modules.vote.mapper",
        "com.ulticode.modules.moderation.mapper",
        "com.ulticode.modules.achievement.mapper",
        "com.ulticode.modules.notification.mapper",
        "com.ulticode.modules.email.mapper",
        "com.ulticode.modules.notification.ledger.mapper",
        "com.ulticode.modules.event.outbox",
        "com.ulticode.modules.event.inbox",
        "com.ulticode.modules.problemlist.mapper",
        "com.ulticode.modules.reconciliation.port",
        "com.ulticode.modules.search.port",
        "com.ulticode.modules.submission.mapper",
        "com.ulticode.modules.submission.result",
        "com.ulticode.modules.subscription.mapper",
        "com.ulticode.app.userprofile.mapper",
        "com.ulticode.app.audit",
        "com.ulticode.app.user.port",
        "com.ulticode.app.i18n.mapper",
        "com.ulticode.app.idempotency.mapper",
})
public class BackendAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendAppApplication.class, args);
    }
}
