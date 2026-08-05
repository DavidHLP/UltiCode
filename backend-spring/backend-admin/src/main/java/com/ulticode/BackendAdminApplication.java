package com.ulticode;

import com.ulticode.app.audit.AppAuditSinkAdapter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * P1-INFRA-005: Admin service placeholder boot entry.
 *
 * <p>Excludes App-owned config, security, and audit-sink adapters from
 * the admin shell (the admin shell binds its own {@code AuditSinkPort}). The
 * Legacy monolith security/configuration classes were deleted by
 * P7-LEGACY-DEAD-INFRA-DELETE-001; the former regex exclusion for
 * {@code com.ulticode.common.config.SecurityConfig} is no longer needed.</p>
 *
 * <p>P7-RELOCATE: {@code QueueConfig} is intentionally NOT excluded any
 * more — the admin shell scans the App-owned contest/submission domain
 * services whose write path enqueues through {@code QueueServiceImpl}, and
 * excluding the queue config left the {@code RQueue} beans missing (which
 * Spring then mis-reports as a circular reference because {@code RQueue}
 * is a {@code Collection} sub-interface and falls into the
 * multi-bean-collection injection fallback). The admin shell shares the
 * same Redis instance as backend-app, so the queue wiring is safe to
 * instantiate here.</p>
 *
 * <p>The explicit {@link MapperScan} is required: with the App boot class and
 * {@code com.ulticode.app.config.MapperScanConfig} both excluded from this
 * context, the MyBatis-Plus auto-configured scan would otherwise scan all of
 * {@code com.ulticode} and collide on the legacy vs App-owned
 * {@code UserProfileMapper}. The legacy {@code com.ulticode.modules.user}
 * family is excluded, so its mapper package is intentionally absent here.</p>
 *
 * <p>P7-RELOCATE: the legacy {@code com.ulticode.modules.auth} family
 * ({@code AuthCutoverService}, {@code DefaultAuthAccountAdapter}) is excluded
 * as well — those beans require the legacy {@code UserMapper} and have no
 * consumer in the admin shell. Admin-owned classes formerly misplaced under
 * {@code com.ulticode.modules.user} ({@code UserProvisioningAdapter},
 * {@code UserActivityAnalyticsProjection}) were relocated under
 * {@code com.ulticode.modules.admin} so the exclusion stops hitting them.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "com.ulticode",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = BackendAppApplication.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AppAuditSinkAdapter.class),
                @ComponentScan.Filter(
                        type = FilterType.REGEX, pattern = "com\\.ulticode\\.app\\.config\\..*"),
                @ComponentScan.Filter(
                        type = FilterType.REGEX, pattern = "com\\.ulticode\\.app\\.security\\..*"),
                @ComponentScan.Filter(
                        type = FilterType.REGEX, pattern = "com\\.ulticode\\.modules\\.user\\..*"),
                @ComponentScan.Filter(
                        type = FilterType.REGEX, pattern = "com\\.ulticode\\.modules\\.auth\\..*"),
                // Admin must NOT load JudgeWorkerProcessor — its @Scheduled pollAndProcess
                // steals judge jobs from the app's queue (both connect to the same Redis).
                // Also exclude the sandbox executor and pipeline to avoid partial judge runs.
                @ComponentScan.Filter(
                        type = FilterType.REGEX, pattern = "com\\.ulticode\\.modules\\.queue\\.processor\\..*"),
                @ComponentScan.Filter(
                        type = FilterType.REGEX, pattern = "com\\.ulticode\\.modules\\.queue\\.pipeline\\..*"),
                @ComponentScan.Filter(
                        type = FilterType.REGEX, pattern = "com\\.ulticode\\.modules\\.submission\\.sandbox\\..*")
        }
)
@MapperScan({
        "com.ulticode.modules.admin.mapper",
        "com.ulticode.modules.admin.outbox.mapper",
        "com.ulticode.modules.backup.mapper",
        "com.ulticode.modules.reconciliation",
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
        "com.ulticode.modules.submission.outbox.mapper",
        "com.ulticode.modules.subscription.mapper",
        "com.ulticode.app.userprofile.mapper",
        "com.ulticode.app.audit",
        "com.ulticode.app.user.port",
        "com.ulticode.app.i18n.mapper",
        "com.ulticode.app.idempotency.mapper",
})
@EnableAsync
@EnableScheduling
public class BackendAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendAdminApplication.class, args);
    }
}
