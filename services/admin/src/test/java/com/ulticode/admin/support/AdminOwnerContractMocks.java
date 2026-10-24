package com.ulticode.admin.support;

import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.app.api.service.ContestAnnouncementReadPort;
import com.ulticode.app.api.service.ContestLiveRankingReadPort;
import com.ulticode.app.api.service.ContestParticipantReadPort;
import com.ulticode.app.api.service.DashboardAdminReadPort;
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
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.auth.api.service.ReconciliationQueryService;
import com.ulticode.notification.api.service.NotificationAdminReadPort;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import com.ulticode.submission.api.service.SubmissionStreakPort;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Isolates external owners when a real Admin database context disables Dubbo. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@MockitoBean(types = {
        ContestAdminReadPort.class,
        ContestAnnouncementReadPort.class,
        ContestLiveRankingReadPort.class,
        ContestParticipantReadPort.class,
        DashboardAdminReadPort.class,
        ForumCommentAdministrationService.class,
        ForumCommentReadPort.class,
        ForumPostAdministrationService.class,
        ForumTagAdministrationService.class,
        ForumTagReadPort.class,
        ProblemAdminReadPort.class,
        ProblemAnalyticsReadPort.class,
        ProblemListAdministrationService.class,
        ProblemListChainReadPort.class,
        ProblemListSearchReadPort.class,
        ProblemOwnerPort.class,
        ProblemTagOwnerPort.class,
        SolutionAdminReadPort.class,
        SolutionCommentOwnerPort.class,
        SolutionCommentReadPort.class,
        SolutionOwnerPort.class,
        SolutionReadPort.class,
        SubscriptionReadPort.class,
        TestCaseOwnerPort.class,
        UserProfileQueryService.class,
        AccountManagementService.class,
        AccountQueryService.class,
        AuthorizationSnapshotService.class,
        IdentityQueryService.class,
        ReconciliationQueryService.class,
        NotificationAdminReadPort.class,
        SubmissionAdminReadPort.class,
        SubmissionStreakPort.class,
        SubmissionUserStatsPort.class
})
public @interface AdminOwnerContractMocks {
}

