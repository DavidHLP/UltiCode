package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.app.api.service.ContestAnnouncementReadPort;
import com.ulticode.app.api.service.ContestLiveRankingReadPort;
import com.ulticode.app.api.service.ContestParticipantReadPort;
import com.ulticode.app.api.service.ForumCommentAdministrationService;
import com.ulticode.app.api.service.ForumCommentReadPort;
import com.ulticode.app.api.service.ForumPostAdministrationService;
import com.ulticode.app.api.service.ForumTagAdministrationService;
import com.ulticode.app.api.service.ForumTagReadPort;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.ProblemAnalyticsReadPort;
import com.ulticode.app.api.service.ProblemListAdministrationService;
import com.ulticode.app.api.service.ProblemListChainReadPort;
import com.ulticode.app.api.service.ProblemListSearchReadPort;
import com.ulticode.app.api.service.ProblemOwnerPort;
import com.ulticode.app.api.service.ProblemTagOwnerPort;
import com.ulticode.app.api.service.SolutionAdminReadPort;
import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.app.api.service.SolutionCommentReadPort;
import com.ulticode.app.api.service.SolutionOwnerPort;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.app.api.service.SubscriptionReadPort;
import com.ulticode.app.api.service.TestCaseOwnerPort;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.notification.api.service.NotificationAdminReadPort;
import com.ulticode.notification.api.service.NotificationServiceContract;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import com.ulticode.submission.api.service.SubmissionStreakPort;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Local registration point for Admin's pure one-hop owner references.
 *
 * <p>These ports carry no Admin behavior: they only expose a configured Dubbo
 * reference under the entity-free API contract. Keeping the references and
 * primary registrations together makes RPC policy changes local to this
 * boundary. Adapters that enrich, normalize or orchestrate responses remain
 * ordinary classes beside this registry.</p>
 */
@Configuration(proxyBeanMethods = false)
public class AdminDubboReferenceRegistry {

    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private AccountManagementService accountManagementReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ContestAdminReadPort contestAdminReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ContestAnnouncementReadPort contestAnnouncementReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ContestLiveRankingReadPort contestLiveRankingReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ContestParticipantReadPort contestParticipantReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ForumCommentAdministrationService forumCommentAdministrationReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ForumCommentReadPort forumCommentReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ForumPostAdministrationService forumPostAdministrationReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ForumTagAdministrationService forumTagAdministrationReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ForumTagReadPort forumTagReadReference;

    @DubboReference(group = NotificationServiceContract.DUBBO_GROUP,
            version = NotificationServiceContract.DUBBO_VERSION,
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private NotificationAdminReadPort notificationAdminReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemAdminReadPort problemAdminReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemAnalyticsReadPort problemAnalyticsReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ProblemListAdministrationService problemListAdministrationReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemListChainReadPort problemListChainReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemListSearchReadPort problemListSearchReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ProblemOwnerPort problemOwnerReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ProblemTagOwnerPort problemTagOwnerReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SolutionAdminReadPort solutionAdminReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private SolutionCommentOwnerPort solutionCommentOwnerReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SolutionCommentReadPort solutionCommentReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private SolutionOwnerPort solutionOwnerReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SolutionReadPort solutionReadReference;

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionAdminReadPort submissionAdminReadReference;

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionStreakPort submissionStreakReference;

    @DubboReference(group = "backend-submission", version = "1.1.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionUserStatsPort submissionUserStatsReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubscriptionReadPort subscriptionReadReference;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private TestCaseOwnerPort testCaseOwnerReference;

    @Bean
    @Primary
    public AccountManagementService accountManagementService() {
        return accountManagementReference;
    }

    @Bean
    @Primary
    public ContestAdminReadPort contestAdminReadPort() {
        return contestAdminReadReference;
    }

    @Bean
    @Primary
    public ContestAnnouncementReadPort contestAnnouncementReadPort() {
        return contestAnnouncementReadReference;
    }

    @Bean
    @Primary
    public ContestLiveRankingReadPort contestLiveRankingReadPort() {
        return contestLiveRankingReadReference;
    }

    @Bean
    @Primary
    public ContestParticipantReadPort contestParticipantReadPort() {
        return contestParticipantReadReference;
    }

    @Bean
    @Primary
    public ForumCommentAdministrationService forumCommentAdministrationService() {
        return forumCommentAdministrationReference;
    }

    @Bean
    @Primary
    public ForumCommentReadPort forumCommentReadPort() {
        return forumCommentReadReference;
    }

    @Bean
    @Primary
    public ForumPostAdministrationService forumPostAdministrationService() {
        return forumPostAdministrationReference;
    }

    @Bean
    @Primary
    public ForumTagAdministrationService forumTagAdministrationService() {
        return forumTagAdministrationReference;
    }

    @Bean
    @Primary
    public ForumTagReadPort forumTagReadPort() {
        return forumTagReadReference;
    }

    @Bean
    @Primary
    public NotificationAdminReadPort notificationAdminReadPort() {
        return notificationAdminReadReference;
    }

    @Bean
    @Primary
    public ProblemAdminReadPort problemAdminReadPort() {
        return problemAdminReadReference;
    }

    @Bean
    @Primary
    public ProblemAnalyticsReadPort problemAnalyticsReadPort() {
        return problemAnalyticsReadReference;
    }

    @Bean
    @Primary
    public ProblemListAdministrationService problemListAdministrationService() {
        return problemListAdministrationReference;
    }

    @Bean
    @Primary
    public ProblemListChainReadPort problemListChainReadPort() {
        return problemListChainReadReference;
    }

    @Bean
    @Primary
    public ProblemListSearchReadPort problemListSearchReadPort() {
        return problemListSearchReadReference;
    }

    @Bean
    @Primary
    public ProblemOwnerPort problemOwnerPort() {
        return problemOwnerReference;
    }

    @Bean
    @Primary
    public ProblemTagOwnerPort problemTagOwnerPort() {
        return problemTagOwnerReference;
    }

    @Bean
    @Primary
    public SolutionAdminReadPort solutionAdminReadPort() {
        return solutionAdminReadReference;
    }

    @Bean
    @Primary
    public SolutionCommentOwnerPort solutionCommentOwnerPort() {
        return solutionCommentOwnerReference;
    }

    @Bean
    @Primary
    public SolutionCommentReadPort solutionCommentReadPort() {
        return solutionCommentReadReference;
    }

    @Bean
    @Primary
    public SolutionOwnerPort solutionOwnerPort() {
        return solutionOwnerReference;
    }

    @Bean
    @Primary
    public SolutionReadPort solutionReadPort() {
        return solutionReadReference;
    }

    @Bean
    @Primary
    public SubmissionAdminReadPort submissionAdminReadPort() {
        return submissionAdminReadReference;
    }

    @Bean
    @Primary
    public SubmissionStreakPort submissionStreakPort() {
        return submissionStreakReference;
    }

    @Bean
    @Primary
    public SubmissionUserStatsPort submissionUserStatsPort() {
        return submissionUserStatsReference;
    }

    @Bean
    @Primary
    public SubscriptionReadPort subscriptionReadPort() {
        return subscriptionReadReference;
    }

    @Bean
    @Primary
    public TestCaseOwnerPort testCaseOwnerPort() {
        return testCaseOwnerReference;
    }
}
