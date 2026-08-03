package com.ulticode;

import static org.assertj.core.api.Assertions.assertThat;

import com.ulticode.app.api.event.FollowEventPublisher;
import com.ulticode.app.api.service.BookmarkReadPort;
import com.ulticode.app.api.service.FollowCountPort;
import com.ulticode.app.api.service.SubscriptionReadPort;
import com.ulticode.app.i18n.service.I18nService;
import com.ulticode.app.security.AppTestSecurityConfig;
import com.ulticode.modules.bookmark.projection.BookmarkProjection;
import com.ulticode.modules.bookmark.service.BookmarkService;
import com.ulticode.modules.follow.inspector.FollowInspector;
import com.ulticode.modules.follow.port.UserReadPort;
import com.ulticode.modules.follow.service.FollowService;
import com.ulticode.modules.subscription.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * P1-INFRA-005: verify the app service shell boots and exposes health.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AppTestSecurityConfig.class)
class BackendAppApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;


    @Autowired
    private com.ulticode.app.api.service.JudgeFeatureFlagsPort judgeFeatureFlagsPort;
    @MockBean
    private I18nService i18nService;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private SubscriptionReadPort subscriptionReadPort;

    @MockBean
    private BookmarkService bookmarkService;

    @MockBean
    private BookmarkReadPort bookmarkReadPort;

    @MockBean
    private BookmarkProjection bookmarkProjection;

    @MockBean
    private FollowService followService;

    @MockBean
    private FollowCountPort followCountPort;

    @MockBean
    private FollowInspector followInspector;

    @MockBean
    private UserReadPort userReadPort;

    @MockBean
    private FollowEventPublisher followEventPublisher;

    @MockBean
    private com.ulticode.modules.solution.service.SolutionService solutionService;

    @MockBean
    private com.ulticode.modules.solution.projection.SolutionProjection solutionProjection;

    @MockBean
    private com.ulticode.modules.solution.service.SolutionTopicService solutionTopicService;

    @MockBean
    private com.ulticode.app.api.service.ProblemExistencePort problemExistencePort;

    @MockBean
    private com.ulticode.app.api.service.SolutionOwnerPort solutionOwnerPort;

    @MockBean
    private com.ulticode.app.api.service.SolutionCommentOwnerPort solutionCommentOwnerPort;

    @MockBean
    private com.ulticode.app.api.service.AchievementBadgeReadPort achievementBadgeReadPort;

    @MockBean
    private com.ulticode.app.api.service.ProblemTagReadPort problemTagReadPort;

    @MockBean
    private com.ulticode.app.api.service.SolutionVoteReadPort solutionVoteReadPort;

    @MockBean
    private com.ulticode.modules.solution.port.SolutionUserReadPort solutionUserReadPort;

    @MockBean
    private com.ulticode.app.api.service.SolutionReadPort solutionReadPort;

    @MockBean
    private com.ulticode.app.security.BanCheckPort banCheckPort;

    @MockBean
    private com.ulticode.modules.forum.mapper.ForumPostMapper forumPostMapper;

    @MockBean
    private com.ulticode.modules.search.port.UserSearchReadMapper userSearchReadMapper;

    @MockBean
    private com.ulticode.modules.reconciliation.port.AppReconciliationReadMapper appReconciliationReadMapper;

    @MockBean
    private com.ulticode.modules.problemlist.mapper.ProblemListMapper problemListMapper;

    @MockBean
    private com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper problemListProblemMapper;

    @MockBean
    private com.ulticode.modules.problemlist.mapper.ProblemListCategoryMapper problemListCategoryMapper;

    @MockBean
    private com.ulticode.modules.problemlist.mapper.ProblemListBookmarkMapper problemListBookmarkMapper;

    @MockBean
    private com.ulticode.modules.forum.port.ForumUserReadPort forumUserReadPort;

    @MockBean
    private com.ulticode.app.userprofile.mapper.UserProfileMapper userProfileMapper;

    @MockBean
    private com.ulticode.modules.forum.mapper.ForumCommentMapper forumCommentMapper;

    @MockBean
    private com.ulticode.modules.forum.mapper.ForumCommunityMapper forumCommunityMapper;

    @MockBean
    private com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper forumCommunityMemberMapper;

    @MockBean
    private com.ulticode.modules.forum.mapper.ForumTagMapper forumTagMapper;

    @MockBean
    private com.ulticode.modules.forum.mapper.ForumUserMapper forumUserMapper;

    @MockBean
    private com.ulticode.app.api.service.ForumVoteReadPort forumVoteReadPort;

    @MockBean
    private com.ulticode.app.api.service.ForumPostReadPort forumPostReadPort;

    // ==================== Submission family (P7-RELOCATE-SUBMISSION-001) ====================

    @MockBean
    private com.ulticode.modules.submission.mapper.SubmissionMapper submissionMapper;
    @MockBean
    private com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper judgeOutboxMapper;
    @MockBean
    private com.ulticode.modules.submission.result.SubmissionResultOutboxMapper submissionResultOutboxMapper;
    @MockBean
    private com.ulticode.modules.submission.projection.SubmissionProjection submissionProjection;
    @MockBean
    private com.ulticode.modules.submission.stats.SubmissionPerformanceStats submissionPerformanceStats;
    @MockBean
    private com.ulticode.app.api.service.ProblemFactsPort submissionProblemFactsPort;
    @MockBean
    private com.ulticode.app.api.service.UserExistencePort userExistencePort;
    @MockBean
    private com.ulticode.app.api.service.JudgeEnqueuePort judgeEnqueuePort;
    @MockBean
    private com.ulticode.app.api.service.ContestSubmissionPort contestSubmissionPort;
    @MockBean
    private com.ulticode.app.api.service.AchievementTriggerPort achievementTriggerPort;
    @MockBean
    private com.ulticode.modules.submission.dispatcher.JudgedNotificationDispatcher judgedNotificationDispatcher;
    // FeatureFlagsProperties is a real @ConfigurationProperties bean; must NOT be mocked
    @MockBean
    private com.ulticode.common.uuid.UuidGenerator uuidGenerator;
    @MockBean
    private com.ulticode.app.api.service.SubmissionUserReadPort submissionUserReadPort;
    @MockBean
    private com.ulticode.modules.submission.service.CodeExecutionService codeExecutionService;
    @MockBean
    private com.ulticode.app.api.service.RejudgePolicy rejudgePolicy;
    @MockBean
    private com.ulticode.app.api.service.SubmissionReadPort submissionReadPort;
    @MockBean
    private com.ulticode.app.api.service.SubmissionStreakPort submissionStreakPort;
    @MockBean
    private com.ulticode.app.api.service.JudgeConfigPort judgeConfigPort;
    @MockBean
    private com.ulticode.app.api.service.SubmissionNotificationPort submissionNotificationPort;
    @MockBean
    private com.ulticode.app.api.service.SubmissionActivityAnalyticsPort submissionActivityAnalyticsPort;
    @MockBean
    private com.ulticode.app.api.service.SubmissionAnalyticsPort submissionAnalyticsPort;
    @MockBean
    private com.ulticode.app.api.service.SubmissionUserStatsPort submissionUserStatsPort;
    @MockBean
    private com.ulticode.app.api.service.ProblemSubmissionStatsPort problemSubmissionStatsPort;
    @MockBean
    private com.ulticode.modules.submission.config.DockerSandboxConfig dockerSandboxConfig;
    @MockBean
    private com.ulticode.modules.submission.sandbox.SandboxExecutor sandboxExecutor;
    @MockBean
    private com.ulticode.modules.submission.service.VerdictResolver verdictResolver;
    @MockBean
    private com.ulticode.modules.submission.service.SandboxOutputFormatter sandboxOutputFormatter;
    @MockBean
    private com.ulticode.modules.submission.port.JudgingLanguageSupport judgingLanguageSupport;
    @MockBean
    private com.ulticode.modules.submission.port.ProblemFactsPort submissionPortProblemFactsPort;

    // ==================== Problem family (P7-RELOCATE-PROBLEM-001) ====================

    @MockBean
    private com.ulticode.modules.problem.mapper.ProblemMapper problemMapper;
    @MockBean
    private com.ulticode.modules.problem.mapper.ProblemDetailMapper problemDetailMapper;
    @MockBean
    private com.ulticode.modules.problem.mapper.ProblemExampleMapper problemExampleMapper;
    @MockBean
    private com.ulticode.modules.problem.mapper.ProblemLanguageMapper problemLanguageMapper;
    @MockBean
    private com.ulticode.modules.problem.mapper.ProblemNoteMapper problemNoteMapper;
    @MockBean
    private com.ulticode.modules.problem.mapper.ProblemTagMapper problemTagMapper;
    @MockBean
    private com.ulticode.modules.problem.mapper.ProblemTagRelationMapper problemTagRelationMapper;
    @MockBean
    private com.ulticode.modules.problem.mapper.ProblemVersionMapper problemVersionMapper;
    @MockBean
    private com.ulticode.modules.problem.mapper.TestCaseMapper testCaseMapper;
    @MockBean
    private com.ulticode.app.api.service.ProblemInteractionQueryPort problemInteractionQueryPort;
    @MockBean
    private com.ulticode.app.api.service.ProblemAnalyticsReadPort problemAnalyticsReadPort;
    @MockBean
    private com.ulticode.app.api.service.JudgingLanguageSupport problemJudgingLanguageSupport;
    // P7-RELOCATE-CONTEST-001: contest mappers + app-api ports
    @MockBean private com.ulticode.modules.contest.mapper.ContestMapper contestMapper;
    @MockBean private com.ulticode.modules.contest.mapper.ContestAnnouncementMapper contestAnnouncementMapper;
    @MockBean private com.ulticode.modules.contest.mapper.ContestParticipantMapper contestParticipantMapper;
    @MockBean private com.ulticode.modules.contest.mapper.ContestProblemMapper contestProblemMapper;
    @MockBean private com.ulticode.modules.contest.mapper.ContestProblemResultMapper contestProblemResultMapper;
    @MockBean private com.ulticode.modules.contest.mapper.ContestSubmissionMapper contestSubmissionMapper;
    @MockBean private com.ulticode.modules.contest.mapper.FirstSolveRecordMapper firstSolveRecordMapper;
    @MockBean private com.ulticode.modules.contest.mapper.GlobalRankingMapper globalRankingMapper;
    @MockBean private com.ulticode.modules.contest.mapper.ScoringRuleMapper scoringRuleMapper;
    @MockBean private com.ulticode.modules.contest.clock.ContestClock contestClock;
    @MockBean private com.ulticode.app.api.service.ContestAchievementPort contestAchievementPort;
    @MockBean private com.ulticode.app.api.service.ContestNotificationPort contestNotificationPort;
    @MockBean private com.ulticode.app.api.service.ContestStatusPushPort contestStatusPushPortBean;
    @MockBean private com.ulticode.app.api.service.ContestRankingMarkDirtyPort contestRankingMarkDirtyPortBean;
    @MockBean private com.ulticode.app.api.service.ContestLiveRankingReadPort contestLiveRankingReadPortBean;
    @MockBean private com.ulticode.modules.contest.service.ContestParticipantTransitions contestParticipantTransitions;
    @MockBean private com.ulticode.modules.contest.service.RatingCalculationService ratingCalculationService;
    @MockBean private com.ulticode.modules.contest.scoring.ContestRankingCacheEvictor contestRankingCacheEvictor;
    // P7-INFRA-S1: vote + event infrastructure
    @MockBean private com.ulticode.modules.vote.mapper.EdgeOperationMapper edgeOperationMapper;
    @MockBean private com.ulticode.modules.event.outbox.IntegrationOutboxMapper integrationOutboxMapper;
    @MockBean private com.ulticode.modules.event.inbox.ConsumerInboxMapper consumerInboxMapper;
    @MockBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    @MockBean private org.redisson.api.RedissonClient redissonClient;
    @MockBean(name = "judgeQueue") private org.redisson.api.RQueue<Object> judgeQueueBean;
    @MockBean(name = "emailQueue") private org.redisson.api.RQueue<Object> emailQueueBean;
    @MockBean(name = "notificationQueue") private org.redisson.api.RQueue<Object> notificationQueueBean;
    @MockBean private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    @MockBean private com.ulticode.modules.queue.port.SubmissionResultPushPort submissionResultPushPort;
    @MockBean private com.ulticode.modules.moderation.port.ModerationUserReadPort moderationUserReadPort;
    @MockBean private com.ulticode.app.api.service.ModerationAccountPort moderationAccountPort;
    @MockBean private com.ulticode.app.api.service.ModerationContentActionPort moderationContentActionPort;
    // P7-INFRA-S4: achievement + notification + email
    @MockBean private com.ulticode.modules.achievement.mapper.AchievementMapper achievementMapper;
    @MockBean private com.ulticode.modules.achievement.mapper.UserAchievementMapper userAchievementMapper;
    @MockBean private com.ulticode.modules.notification.mapper.NotificationMapper notificationMapper;
    @MockBean private com.ulticode.modules.notification.mapper.NotificationPreferenceMapper notificationPreferenceMapper;
    @MockBean private com.ulticode.modules.email.mapper.EmailLogMapper emailLogMapper;
    @MockBean private com.ulticode.modules.email.mapper.EmailTemplateMapper emailTemplateMapper;
    @MockBean private com.ulticode.app.api.service.UserReadPort userReadPortBean;
    @MockBean private com.ulticode.modules.notification.port.NotificationPushPort notificationPushPortBean;
    @MockBean private com.ulticode.modules.achievement.port.BadgePushPort badgePushPortBean;
    @MockBean private com.ulticode.modules.notification.ledger.mapper.NotificationDeliveryLedgerMapper notificationDeliveryLedgerMapper;
    // P7-RELOCATE-WEBSOCKET-001
    @MockBean private com.ulticode.app.api.service.JwtValidationPort jwtValidationPort;
    @MockBean private com.ulticode.app.api.service.AccountReadPort accountReadPort;
    @MockBean private com.ulticode.modules.websocket.port.TokenBlacklistPort tokenBlacklistPort;

    @MockBean private com.ulticode.modules.moderation.mapper.UserWarningMapper userwarningmapperMapper;

    @MockBean private com.ulticode.modules.moderation.mapper.UserBanMapper userbanmapperMapper;

    @MockBean private com.ulticode.modules.moderation.mapper.AppealMapper appealmapperMapper;

    @MockBean private com.ulticode.modules.moderation.mapper.ReportMapper reportmapperMapper;

    @MockBean private com.ulticode.modules.moderation.mapper.ModerationActionMapper moderationactionmapperMapper;

    @MockBean private com.ulticode.modules.moderation.mapper.ModerationQueueMapper moderationqueuemapperMapper;


    @Test
    @DisplayName("context loads and /actuator/health is UP")
    void healthEndpointReturnsUp() {
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
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
}
