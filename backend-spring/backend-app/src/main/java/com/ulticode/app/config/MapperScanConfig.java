package com.ulticode.app.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Explicit {@link MapperScan} for all relocated family mapper packages.
 *
 * <p>Profile-gated to activate only outside the test profile. The shell
 * smoke test ({@code BackendAppApplicationTest}) uses a test profile that
 * excludes {@code MybatisAutoConfiguration}; this scan would create
 * mapper factory beans that require a {@code SqlSessionFactory} which
 * is absent in that profile.
 *
 * <p>P7-RELOCATE-SOLUTION-001: added when the solution family relocated
 * from backend-legacy to backend-app.
 */
@Configuration
@Profile("!test")
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
        "com.ulticode.app.userprofile.mapper",
        "com.ulticode.app.audit",
        "com.ulticode.app.user.port",
        "com.ulticode.app.i18n.mapper",
        "com.ulticode.app.idempotency.mapper",
        "com.ulticode.modules.problemlist.mapper",
        "com.ulticode.modules.reconciliation.port",
        "com.ulticode.modules.search.port",
        "com.ulticode.modules.submission.mapper",
        "com.ulticode.modules.submission.outbox.mapper",
        "com.ulticode.modules.submission.result",
        "com.ulticode.modules.subscription.mapper",
})
public class MapperScanConfig {
}
