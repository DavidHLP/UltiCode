package com.ulticode;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.modules.follow.port.FollowEventPublisher;
import com.ulticode.modules.bookmark.port.BookmarkReadPort;
import com.ulticode.modules.follow.port.FollowCountPort;
import com.ulticode.app.api.service.SubscriptionReadPort;
import com.ulticode.app.i18n.service.I18nService;
import com.ulticode.websecurity.jwt.ResourceServerJwtVerifier;
import com.ulticode.modules.bookmark.projection.BookmarkProjection;
import com.ulticode.modules.bookmark.service.BookmarkService;
import com.ulticode.modules.follow.inspector.FollowInspector;
import com.ulticode.modules.follow.port.UserReadPort;
import com.ulticode.modules.follow.service.FollowService;
import com.ulticode.modules.queue.migration.JudgeStreamLegacyMigration;
import com.ulticode.modules.queue.outbox.reaper.UnackedStreamEntriesReaper;
import com.ulticode.modules.queue.processor.DefaultJudgeAttemptExecutor;
import com.ulticode.modules.queue.processor.JudgeWorkerProcessor;
import com.ulticode.modules.subscription.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * P1-INFRA-005: verify the app service shell boots and exposes health.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BackendAppApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ResourceServerJwtVerifier resourceServerJwtVerifier;

    @Autowired
    private ApplicationContext applicationContext;


    @Autowired
    private com.ulticode.modules.submission.port.JudgeFeatureFlagsPort judgeFeatureFlagsPort;

    @Autowired
    private com.ulticode.common.audit.AuditSinkPort auditSinkPort;
    @MockitoBean
    private I18nService i18nService;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private SubscriptionReadPort subscriptionReadPort;

    @MockitoBean
    private BookmarkService bookmarkService;

    @MockitoBean
    private BookmarkReadPort bookmarkReadPort;

    @MockitoBean
    private BookmarkProjection bookmarkProjection;

    @MockitoBean
    private FollowService followService;

    @MockitoBean
    private FollowCountPort followCountPort;

    @MockitoBean
    private FollowInspector followInspector;

    @MockitoBean
    private UserReadPort userReadPort;

    @MockitoBean
    private FollowEventPublisher followEventPublisher;

    @MockitoBean
    private com.ulticode.modules.solution.service.SolutionService solutionService;

    @MockitoBean
    private com.ulticode.modules.solution.projection.SolutionProjection solutionProjection;

    @MockitoBean
    private com.ulticode.modules.solution.service.SolutionTopicService solutionTopicService;

    @MockitoBean
    private com.ulticode.modules.solution.mapper.SolutionMapper solutionMapper;

    @MockitoBean
    private com.ulticode.modules.solution.mapper.SolutionCommentMapper solutionCommentMapper;

    @MockitoBean
    private com.ulticode.modules.problem.port.ProblemExistencePort problemExistencePort;

    @MockitoBean
    private com.ulticode.app.api.service.SolutionOwnerPort solutionOwnerPort;

    @MockitoBean
    private com.ulticode.app.api.service.SolutionCommentOwnerPort solutionCommentOwnerPort;

    @MockitoBean
    private com.ulticode.modules.achievement.port.AchievementBadgeReadPort achievementBadgeReadPort;

    @MockitoBean
    private com.ulticode.modules.solution.port.ProblemTagReadPort problemTagReadPort;

    @MockitoBean
    private com.ulticode.modules.solution.port.SolutionVoteReadPort solutionVoteReadPort;

    @MockitoBean
    private com.ulticode.modules.solution.port.SolutionUserReadPort solutionUserReadPort;

    @MockitoBean
    private com.ulticode.app.api.service.SolutionReadPort solutionReadPort;

    @MockitoBean
    private com.ulticode.app.security.BanCheckPort banCheckPort;

    @MockitoBean
    private com.ulticode.modules.forum.mapper.ForumPostMapper forumPostMapper;


    @MockitoBean
    private com.ulticode.modules.reconciliation.port.AppReconciliationReadMapper appReconciliationReadMapper;

    @MockitoBean
    private com.ulticode.modules.problemlist.mapper.ProblemListMapper problemListMapper;

    @MockitoBean
    private com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper problemListProblemMapper;

    @MockitoBean
    private com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper problemListCategoryMapper;

    @MockitoBean
    private com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper problemListBookmarkMapper;

    @MockitoBean
    private com.ulticode.app.user.port.UserFactsProjection userFactsProjection;

    @MockitoBean
    private com.ulticode.app.user.port.UserDirectoryProjection userDirectoryProjection;


    @MockitoBean
    private com.ulticode.modules.forum.port.ForumUserReadPort forumUserReadPort;

    @MockitoBean
    private com.ulticode.app.userprofile.mapper.UserProfileMapper userProfileMapper;

    @MockitoBean
    private com.ulticode.modules.forum.mapper.ForumCommentMapper forumCommentMapper;

    @MockitoBean
    private com.ulticode.modules.forum.mapper.ForumCommunityMapper forumCommunityMapper;

    @MockitoBean
    private com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper forumCommunityMemberMapper;

    @MockitoBean
    private com.ulticode.modules.forum.mapper.ForumTagMapper forumTagMapper;

    @MockitoBean
    private com.ulticode.modules.forum.mapper.ForumUserMapper forumUserMapper;

    @MockitoBean
    private com.ulticode.modules.forum.port.ForumVoteReadPort forumVoteReadPort;

    @MockitoBean
    private com.ulticode.modules.forum.port.ForumPostReadPort forumPostReadPort;

    // ==================== Submission family (P7-RELOCATE-SUBMISSION-001) ====================

    @MockitoBean
    private com.ulticode.app.api.service.ProblemFactsPort submissionProblemFactsPort;
    @MockitoBean
    private com.ulticode.app.api.service.UserExistencePort userExistencePort;
    @MockitoBean
    private com.ulticode.modules.queue.port.JudgeEnqueuePort judgeEnqueuePort;
    @MockitoBean
    private com.ulticode.modules.contest.integration.ContestSubmissionAdapter contestSubmissionAdapter;
    // FeatureFlagsProperties is a real @ConfigurationProperties bean; must NOT be mocked
    @MockitoBean
    private com.ulticode.common.uuid.UuidGenerator uuidGenerator;
    @MockitoBean
    private com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper appCommandReceiptMapper;
    @MockitoBean
    private com.ulticode.app.api.service.SubmissionUserReadPort submissionUserReadPort;
    @MockitoBean
    private com.ulticode.modules.submission.port.InteractiveCodeRunner codeExecutionPort;
    @MockitoBean
    private com.ulticode.submission.api.service.SubmissionReadPort submissionReadPort;
    @MockitoBean
    private com.ulticode.submission.api.service.SubmissionStreakPort submissionStreakPort;
    @MockitoBean
    private com.ulticode.modules.submission.port.JudgeConfigPort judgeConfigPort;
    @MockitoBean
    private com.ulticode.submission.api.service.SubmissionUserStatsPort submissionUserStatsPort;
    @MockitoBean
    private com.ulticode.submission.api.service.ProblemSubmissionStatsPort problemSubmissionStatsPort;
    @MockitoBean
    private com.ulticode.modules.submission.config.DockerSandboxConfig dockerSandboxConfig;
    @MockitoBean
    private com.ulticode.modules.submission.sandbox.SandboxExecutor sandboxExecutor;
    @MockitoBean
    private com.ulticode.modules.submission.service.VerdictResolver verdictResolver;
    @MockitoBean
    private com.ulticode.modules.submission.service.SandboxOutputFormatter sandboxOutputFormatter;
    @MockitoBean
    private com.ulticode.modules.submission.port.JudgingLanguageSupport judgingLanguageSupport;
    @MockitoBean
    private com.ulticode.modules.submission.port.ProblemFactsPort submissionPortProblemFactsPort;

    // ==================== Problem family (P7-RELOCATE-PROBLEM-001) ====================

    @MockitoBean
    private com.ulticode.modules.problem.mapper.ProblemMapper problemMapper;
    @MockitoBean
    private com.ulticode.modules.problem.mapper.ProblemDetailMapper problemDetailMapper;
    @MockitoBean
    private com.ulticode.modules.problem.mapper.ProblemExampleMapper problemExampleMapper;
    @MockitoBean
    private com.ulticode.modules.problem.mapper.ProblemLanguageMapper problemLanguageMapper;
    @MockitoBean
    private com.ulticode.modules.problem.mapper.ProblemNoteMapper problemNoteMapper;
    @MockitoBean
    private com.ulticode.modules.problem.mapper.ProblemTagMapper problemTagMapper;
    @MockitoBean
    private com.ulticode.modules.problem.mapper.ProblemTagRelationMapper problemTagRelationMapper;
    @MockitoBean
    private com.ulticode.modules.problem.mapper.ProblemVersionMapper problemVersionMapper;
    @MockitoBean
    private com.ulticode.modules.problem.mapper.TestCaseMapper testCaseMapper;
    @MockitoBean
    private com.ulticode.modules.problem.port.ProblemInteractionQueryPort problemInteractionQueryPort;
    @MockitoBean
    private com.ulticode.app.api.service.ProblemAnalyticsReadPort problemAnalyticsReadPort;
    @MockitoBean
    private com.ulticode.modules.problem.port.ProblemLanguageCatalog problemJudgingLanguageSupport;
    // P7-RELOCATE-CONTEST-001: contest mappers + app-api ports
    @MockitoBean private com.ulticode.modules.contest.mapper.ContestMapper contestMapper;
    @MockitoBean private com.ulticode.modules.contest.mapper.ContestAnnouncementMapper contestAnnouncementMapper;
    @MockitoBean private com.ulticode.modules.contest.mapper.ContestParticipantMapper contestParticipantMapper;
    @MockitoBean private com.ulticode.modules.contest.mapper.ContestProblemMapper contestProblemMapper;
    @MockitoBean private com.ulticode.modules.contest.mapper.ContestProblemResultMapper contestProblemResultMapper;
@MockitoBean private com.ulticode.modules.contest.mapper.ContestAdjudicationReceiptMapper contestAdjudicationReceiptMapper;
    @MockitoBean private com.ulticode.modules.contest.mapper.ContestSubmissionMapper contestSubmissionMapper;
@MockitoBean private com.ulticode.modules.contest.mapper.ContestRatingCalculationMapper contestRatingCalculationMapper;
@MockitoBean private com.ulticode.modules.contest.mapper.ContestCascadeMapper contestCascadeMapper;
    @MockitoBean private com.ulticode.modules.contest.mapper.FirstSolveRecordMapper firstSolveRecordMapper;
    @MockitoBean private com.ulticode.modules.contest.mapper.GlobalRankingMapper globalRankingMapper;
    @MockitoBean private com.ulticode.modules.contest.mapper.ScoringRuleMapper scoringRuleMapper;
    @MockitoBean private com.ulticode.modules.contest.clock.ContestClock contestClock;
    @MockitoBean private com.ulticode.modules.achievement.port.ContestAchievementPort contestAchievementPort;
    @MockitoBean private com.ulticode.modules.notification.port.ContestNotificationPort contestNotificationPort;
    @MockitoBean private com.ulticode.modules.websocket.port.ContestStatusPushPort contestStatusPushPortBean;
    @MockitoBean private com.ulticode.modules.websocket.port.ContestRankingMarkDirtyPort contestRankingMarkDirtyPortBean;
    @MockitoBean private com.ulticode.app.api.service.ContestLiveRankingReadPort contestLiveRankingReadPortBean;
    @MockitoBean private com.ulticode.modules.contest.service.ContestParticipantTransitions contestParticipantTransitions;
    @MockitoBean private com.ulticode.modules.contest.service.RatingCalculationService ratingCalculationService;
    @MockitoBean private com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor contestRankingCacheEvictor;
    // P7-INFRA-S1: vote + event infrastructure
    @MockitoBean private com.ulticode.modules.vote.mapper.EdgeOperationMapper edgeOperationMapper;
    @MockitoBean private com.ulticode.modules.event.outbox.IntegrationOutboxMapper integrationOutboxMapper;
    @MockitoBean private com.ulticode.modules.event.inbox.ConsumerInboxMapper consumerInboxMapper;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    @MockitoBean private org.redisson.api.RedissonClient redissonClient;
    @MockitoBean(name = "judgeQueue") private org.redisson.api.RQueue<Object> judgeQueueBean;
    @MockitoBean(name = "emailQueue") private org.redisson.api.RQueue<Object> emailQueueBean;
    @MockitoBean(name = "notificationQueue") private org.redisson.api.RQueue<Object> notificationQueueBean;
    @MockitoBean private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    @MockitoBean private com.ulticode.modules.websocket.port.SubmissionResultPushPort submissionResultPushPort;
    @MockitoBean private com.ulticode.modules.moderation.port.ModerationUserReadPort moderationUserReadPort;
    @MockitoBean private com.ulticode.modules.moderation.port.ModerationAccountPort moderationAccountPort;
    @MockitoBean private com.ulticode.modules.moderation.port.ContentModerationActionPort contentModerationActionPort;
    // P7-INFRA-S4: achievement (notification delivery is owned by backend-notification)
    @MockitoBean private com.ulticode.modules.achievement.mapper.AchievementMapper achievementMapper;
    @MockitoBean private com.ulticode.modules.achievement.mapper.UserAchievementMapper userAchievementMapper;
    @MockitoBean private com.ulticode.modules.user.port.UserReadPort userReadPortBean;
    @MockitoBean private com.ulticode.modules.notification.port.NotificationPushPort notificationPushPortBean;
    @MockitoBean private com.ulticode.modules.achievement.port.BadgePushPort badgePushPortBean;
    // P7-RELOCATE-WEBSOCKET-001
    @MockitoBean private com.ulticode.common.security.JwtValidationPort jwtValidationPort;
    @MockitoBean private com.ulticode.common.security.AccountReadPort accountReadPort;
    @MockitoBean private com.ulticode.modules.websocket.port.TokenBlacklistPort tokenBlacklistPort;

    @MockitoBean private com.ulticode.modules.moderation.mapper.UserWarningMapper userwarningmapperMapper;

    @MockitoBean private com.ulticode.modules.moderation.mapper.UserBanMapper userbanmapperMapper;

    @MockitoBean private com.ulticode.modules.moderation.mapper.AppealMapper appealmapperMapper;

    @MockitoBean private com.ulticode.modules.moderation.mapper.ReportMapper reportmapperMapper;

    @MockitoBean private com.ulticode.modules.moderation.mapper.ModerationActionMapper moderationactionmapperMapper;

    @MockitoBean private com.ulticode.modules.moderation.mapper.ModerationQueueMapper moderationqueuemapperMapper;

    // P7-AUDIT-SINK-OWNER-BINDING-001: mapper bean for AppAuditSinkAdapter in the test profile
    @MockitoBean private com.ulticode.app.audit.AppAuditOutboxMapper appAuditOutboxMapper;

    // Pre-existing shell-test wiring gap unmasked by the audit fix: UserProfileQueryProvider needs this mapper
    @MockitoBean private com.ulticode.app.user.port.UserProfileReadMapper userProfileReadMapper;

    // Pre-existing shell-test wiring gap (P7-INFRA-MODERATION-BRIDGE-001): ModerationServiceImpl needs this port
    @MockitoBean private com.ulticode.modules.moderation.port.ContentModerationPort contentModerationPort;


    @Test
    @DisplayName("context loads and /actuator/health is UP")
    void healthEndpointReturnsUp() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("App context does not auto-register Judge-only scheduled wiring")
    void judgeOnlyWiringStaysOutOfAppContext() {
        assertThat(applicationContext.getBeansOfType(JudgeWorkerProcessor.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(DefaultJudgeAttemptExecutor.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(UnackedStreamEntriesReaper.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(JudgeStreamLegacyMigration.class)).isEmpty();
    }

    @Test
    @DisplayName("ResourceServerJwtVerifier uses real test JWKS wiring")
    void resourceServerJwtVerifierIsWired() {
        assertThat(resourceServerJwtVerifier).isNotNull();
        assertThat(applicationContext.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class))
                .containsOnlyKeys("securityFilterChain");
    }

    @Test
    @DisplayName("placeholder /api/v1/app/health returns success")
    void placeholderReturnsOk() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/api/v1/app/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("backend-app shell up");
    }

    @Test
    @DisplayName("JudgeFeatureFlagsPort resolves to the backend-app adapter")
    void judgeFeatureFlagsPortResolvesToAppAdapter() {
        assertThat(judgeFeatureFlagsPort)
                .isInstanceOf(com.ulticode.modules.submission.port.DefaultJudgeFeatureFlagsPort.class);
    }

    @Test
    @DisplayName("AuditSinkPort resolves to the App-local outbox adapter (P7-AUDIT-SINK-OWNER-BINDING-001)")
    void auditSinkPortResolvesToAppAdapter() {
        assertThat(auditSinkPort)
                .isInstanceOf(com.ulticode.app.audit.AppAuditSinkAdapter.class);
    }
}
