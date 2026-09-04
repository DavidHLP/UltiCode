package com.ulticode.modules.admin.query;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.port.AdminSubmissionUserDetailStatsReadPort;
import com.ulticode.modules.admin.projection.AdminUserEnricher;
import com.ulticode.modules.admin.projection.DefaultAdminUserProjection;
import com.ulticode.submission.api.dto.SubmissionUserDetailStatsSnapshotDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserDetailQuery")
class AdminUserDetailQueryTest {

    @Mock
    private AdminUserEnricher userEnricher;
    @Mock
    private AdminSubmissionUserDetailStatsReadPort submissionStatsReadPort;
    @Mock
    private SolutionReadPort solutionReadPort;
    @Mock
    private AuthorizationSnapshotService authorizationSnapshotService;

    private DefaultAdminUserDetailQuery query;

    @BeforeEach
    void setUp() {
        query = new DefaultAdminUserDetailQuery(
                userEnricher,
                submissionStatsReadPort,
                solutionReadPort,
                authorizationSnapshotService,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        query.shutdownQueryExecutor();
    }

    @Test
    @DisplayName("runs one Auth round and four optional reads in one bounded second round")
    void completeDetailUsesSingleSubmissionSnapshot() {
        stubHealthyDetail();

        AdminUserDetailResult result = query.loadUserDetail("user-123");

        assertThat(result.availability()).isEqualTo(AdminUserDetailResult.Availability.OK);
        assertThat(result.profile().status()).isEqualTo(AdminUserDetailResult.Availability.OK);
        assertThat(result.stats().status()).isEqualTo(AdminUserDetailResult.Availability.OK);
        assertThat(result.permissions().status()).isEqualTo(AdminUserDetailResult.Availability.OK);
        assertThat(result.user().getDetailStatus()).isEqualTo(DegradationStatus.OK);
        assertThat(result.user().getProfileStatus()).isEqualTo(DegradationStatus.OK);
        assertThat(result.user().getStatsStatus()).isEqualTo(DegradationStatus.OK);
        assertThat(result.user().getPermissionsStatus()).isEqualTo(DegradationStatus.OK);
        assertThat(result.user().getStats().getTotalSubmissions()).isEqualTo(12);
        assertThat(result.user().getStats().getAcceptedSubmissions()).isEqualTo(4);
        assertThat(result.user().getStats().getTotalSolutions()).isEqualTo(3);
        assertThat(result.user().getStats().getStreak()).isEqualTo(2);
        assertThat(result.user().getPermissions())
                .extracting(AdminUserVO.PermissionInfo::getAction)
                .containsExactlyInAnyOrder("READ", "WRITE");
        assertThat(result.permissionSnapshot().source())
                .isEqualTo("auth.authorization-snapshot");
        assertThat(result.permissionSnapshot().version()).isEqualTo(9L);

        verify(userEnricher).findAccountAuthoritatively("user-123");
        verify(userEnricher).findProfileWithStatus("user-123");
        verify(solutionReadPort).countByUserId("user-123");
        verify(submissionStatsReadPort).loadUserDetailStats("user-123");
        verify(authorizationSnapshotService).getSnapshot("user-123");
    }

    @Test
    @DisplayName("Auth not-found stops before optional reads")
    void notFoundIsAuthoritative() {
        when(userEnricher.findAccountAuthoritatively("missing")).thenReturn(null);

        AdminUserDetailResult result = query.loadUserDetail("missing");

        assertThat(result.failure()).isEqualTo(AdminUserDetailResult.Failure.NOT_FOUND);
        assertThat(result.availability()).isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        verifyNoInteractions(submissionStatsReadPort, solutionReadPort, authorizationSnapshotService);
        verify(userEnricher, never()).findProfileWithStatus("missing");
    }

    @Test
    @DisplayName("Auth transport failure is unavailable, not not-found")
    void authTransportFailureIsDistinct() {
        when(userEnricher.findAccountAuthoritatively("down"))
                .thenThrow(new BusinessException(
                        AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                        "Auth account query unavailable"));

        AdminUserDetailResult result = query.loadUserDetail("down");

        assertThat(result.failure())
                .isEqualTo(AdminUserDetailResult.Failure.TRANSPORT_UNAVAILABLE);
        assertThat(result.availability())
                .isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        verifyNoInteractions(submissionStatsReadPort, solutionReadPort, authorizationSnapshotService);
    }

    @Test
    @DisplayName("profile failure is explicit while account, stats, and permissions remain usable")
    void profileFailureIsPartial() {
        stubHealthyDetail();
        when(userEnricher.findProfileWithStatus("user-123"))
                .thenThrow(new RuntimeException("App down"));

        AdminUserDetailResult result = query.loadUserDetail("user-123");

        assertThat(result.availability()).isEqualTo(AdminUserDetailResult.Availability.PARTIAL);
        assertThat(result.profile().status())
                .isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        assertThat(result.profile().reason()).isEqualTo("App profile query unavailable");
        assertThat(result.user().getName()).isNull();
        assertThat(result.stats().status()).isEqualTo(AdminUserDetailResult.Availability.OK);
    }

    @Test
    @DisplayName("Submission failure never becomes zero stats")
    void submissionFailureIsPartialAndNotZero() {
        stubHealthyDetail();
        when(submissionStatsReadPort.loadUserDetailStats("user-123"))
                .thenThrow(new RuntimeException("Submission down"));

        AdminUserDetailResult result = query.loadUserDetail("user-123");

        assertThat(result.stats().status()).isEqualTo(AdminUserDetailResult.Availability.PARTIAL);
        assertThat(result.stats().reason()).contains("Submission stats query unavailable");
        assertThat(result.user().getStats()).isNull();
        verify(submissionStatsReadPort).loadUserDetailStats("user-123");
    }

    @Test
    @DisplayName("App solution failure never becomes zero stats")
    void solutionFailureIsPartialAndNotZero() {
        stubHealthyDetail();
        when(solutionReadPort.countByUserId("user-123"))
                .thenThrow(new RuntimeException("App down"));

        AdminUserDetailResult result = query.loadUserDetail("user-123");

        assertThat(result.stats().status()).isEqualTo(AdminUserDetailResult.Availability.PARTIAL);
        assertThat(result.stats().reason()).contains("App solution count query unavailable");
        assertThat(result.user().getStats()).isNull();
    }

    @Test
    @DisplayName("combined App owner outage preserves Submission facts and marks stats partial")
    void combinedAppOutageIsPartial() {
        stubHealthyDetail();
        when(userEnricher.findProfileWithStatus("user-123"))
                .thenThrow(new RuntimeException("App profile down"));
        when(solutionReadPort.countByUserId("user-123"))
                .thenThrow(new RuntimeException("App solution down"));

        AdminUserDetailResult result = query.loadUserDetail("user-123");

        assertThat(result.profile().status())
                .isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        assertThat(result.stats().status())
                .isEqualTo(AdminUserDetailResult.Availability.PARTIAL);
        assertThat(result.stats().reason()).contains("App solution count query unavailable");
        assertThat(result.user().getStats()).isNull();
        assertThat(result.user().getPermissions()).isNotNull();
    }

    @Test
    @DisplayName("combined Submission and Auth snapshot outage isolates both optional sections")
    void combinedSubmissionAndPermissionOutageIsPartial() {
        stubHealthyDetail();
        when(submissionStatsReadPort.loadUserDetailStats("user-123"))
                .thenThrow(new RuntimeException("Submission down"));
        when(authorizationSnapshotService.getSnapshot("user-123"))
                .thenThrow(new RuntimeException("Auth snapshot down"));

        AdminUserDetailResult result = query.loadUserDetail("user-123");

        assertThat(result.availability()).isEqualTo(AdminUserDetailResult.Availability.PARTIAL);
        assertThat(result.stats().status()).isEqualTo(AdminUserDetailResult.Availability.PARTIAL);
        assertThat(result.permissions().status())
                .isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        assertThat(result.user().getStats()).isNull();
        assertThat(result.user().getPermissions()).isNull();
    }

    @Test
    @DisplayName("all optional owners down yields found user with unavailable sections")
    void allOptionalOwnersDown() {
        when(userEnricher.findAccountAuthoritatively("user-123")).thenReturn(account());
        when(userEnricher.findProfileWithStatus("user-123"))
                .thenThrow(new RuntimeException("App profile down"));
        when(solutionReadPort.countByUserId("user-123"))
                .thenThrow(new RuntimeException("App solution down"));
        when(submissionStatsReadPort.loadUserDetailStats("user-123"))
                .thenThrow(new RuntimeException("Submission down"));
        when(authorizationSnapshotService.getSnapshot("user-123"))
                .thenThrow(new RuntimeException("Auth snapshot down"));

        AdminUserDetailResult result = query.loadUserDetail("user-123");

        assertThat(result.failure()).isNull();
        assertThat(result.user()).isNotNull();
        assertThat(result.availability()).isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        assertThat(result.profile().status()).isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        assertThat(result.stats().status()).isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        assertThat(result.permissions().status())
                .isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        assertThat(result.user().getStats()).isNull();
        assertThat(result.user().getPermissions()).isNull();
    }

    @Test
    @DisplayName("permission null/failure/exception leaves permissions null")
    void permissionProviderFailuresDoNotBecomeEmptyPermissions() {
        stubHealthyAccountAndFacts();
        when(authorizationSnapshotService.getSnapshot("user-123")).thenReturn(null);
        AdminUserDetailResult nullResult = query.loadUserDetail("user-123");
        assertPermissionUnavailable(nullResult);

        when(authorizationSnapshotService.getSnapshot("user-123"))
                .thenReturn(RpcResult.failure(AuthErrorCode.INVALID_ACCOUNT_REQUEST, "t-failure"));
        AdminUserDetailResult failureResult = query.loadUserDetail("user-123");
        assertPermissionUnavailable(failureResult);

        when(authorizationSnapshotService.getSnapshot("user-123"))
                .thenThrow(new RuntimeException("Auth snapshot down"));
        AdminUserDetailResult exceptionResult = query.loadUserDetail("user-123");
        assertPermissionUnavailable(exceptionResult);
    }

    @Test
    @DisplayName("proven zero stats remain a successful zero snapshot")
    void provenZeroStatsAreNotFailure() {
        when(userEnricher.findAccountAuthoritatively("user-123")).thenReturn(account());
        when(userEnricher.findProfileWithStatus("user-123"))
                .thenReturn(new AdminUserEnricher.ProfileDetail(profile(), DegradationStatus.OK));
        when(solutionReadPort.countByUserId("user-123")).thenReturn(0L);
        when(submissionStatsReadPort.loadUserDetailStats("user-123"))
                .thenReturn(new SubmissionUserDetailStatsSnapshotDTO(0L, 0L, 0));
        when(authorizationSnapshotService.getSnapshot("user-123"))
                .thenReturn(RpcResult.success(snapshot(), "t-auth"));

        AdminUserDetailResult result = query.loadUserDetail("user-123");

        assertThat(result.stats().status()).isEqualTo(AdminUserDetailResult.Availability.OK);
        assertThat(result.user().getStats().getTotalSubmissions()).isZero();
        assertThat(result.user().getStats().getAcceptedSubmissions()).isZero();
        assertThat(result.user().getStats().getTotalSolutions()).isZero();
        assertThat(result.user().getStats().getStreak()).isZero();
    }

    @Test
    @DisplayName("optional wall timeout cancels unfinished owner calls and exposes timeout reason")
    void optionalTimeoutIsObservable() throws InterruptedException {
        stubHealthyDetail();
        CountDownLatch interrupted = new CountDownLatch(1);
        when(submissionStatsReadPort.loadUserDetailStats("user-123"))
                .thenAnswer(invocation -> {
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException exception) {
                        interrupted.countDown();
                        throw exception;
                    }
                    return new SubmissionUserDetailStatsSnapshotDTO(1L, 1L, 1);
                });

        AdminUserDetailResult result = query.loadUserDetail("user-123");

        assertThat(result.stats().status()).isIn(
                AdminUserDetailResult.Availability.PARTIAL,
                AdminUserDetailResult.Availability.UNAVAILABLE);
        assertThat(result.stats().reason()).contains("timed out");
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("caller interruption cancels optional calls and exposes interrupt reason")
    void callerInterruptIsObservable() throws InterruptedException {
        when(userEnricher.findAccountAuthoritatively("user-123")).thenReturn(account());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(submissionStatsReadPort.loadUserDetailStats("user-123"))
                .thenAnswer(invocation -> {
                    started.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException exception) {
                        interrupted.countDown();
                        throw exception;
                    }
                    return new SubmissionUserDetailStatsSnapshotDTO(1L, 1L, 1);
                });

        java.util.concurrent.atomic.AtomicReference<AdminUserDetailResult> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread caller = new Thread(() -> result.set(query.loadUserDetail("user-123")));
        caller.start();
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        caller.interrupt();
        caller.join(1_000);

        assertThat(result.get()).isNotNull();
        assertThat(result.get().stats().reason()).contains("interrupted");
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("compatibility projection keeps existing error mapping while query owns fanout")
    void compatibilityProjectionPreservesErrorSemantics() {
        AdminUserDetailQuery notFound = id -> AdminUserDetailResult.notFound();
        DefaultAdminUserProjection notFoundProjection =
                new DefaultAdminUserProjection(userEnricher, notFound);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> notFoundProjection.getUserById("missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AdminErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("malformed legacy flat permission entries make the permission section unavailable")
    void malformedFlatPermissionsAreRejected() {
        stubHealthyAccountAndFacts();
        when(authorizationSnapshotService.getSnapshot("user-123"))
                .thenReturn(RpcResult.success(
                        new AuthorizationSnapshotDTO(
                                "user-123", "USER",
                                Set.of("READ:PROBLEM", "not-a-permission"), 7L),
                        "t-auth"));

        AdminUserDetailResult result = query.loadUserDetail("user-123");

        assertThat(result.permissions().status())
                .isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        assertThat(result.permissionSnapshot()).isNull();
        assertThat(result.user().getPermissions()).isNull();
    }

    private void stubHealthyDetail() {
        stubHealthyAccountAndFacts();
        when(authorizationSnapshotService.getSnapshot("user-123"))
                .thenReturn(RpcResult.success(snapshot(), "t-auth"));
    }

    private void stubHealthyAccountAndFacts() {
        when(userEnricher.findAccountAuthoritatively("user-123")).thenReturn(account());
        when(userEnricher.findProfileWithStatus("user-123"))
                .thenReturn(new AdminUserEnricher.ProfileDetail(profile(), DegradationStatus.OK));
        when(solutionReadPort.countByUserId("user-123")).thenReturn(3L);
        when(submissionStatsReadPort.loadUserDetailStats("user-123"))
                .thenReturn(new SubmissionUserDetailStatsSnapshotDTO(12L, 4L, 2));
    }

    private void assertPermissionUnavailable(AdminUserDetailResult result) {
        assertThat(result.permissions().status())
                .isEqualTo(AdminUserDetailResult.Availability.UNAVAILABLE);
        assertThat(result.user().getPermissions()).isNull();
        assertThat(result.permissionSnapshot()).isNull();
    }

    private AuthAccountDTO account() {
        return new AuthAccountDTO(
                "user-123", "testuser", "test@example.com", "USER",
                true, false, null, null,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0), 1L);
    }

    private UserProfileDTO profile() {
        return new UserProfileDTO(
                "user-123", "Test User", "https://avatar.test/user-123", null,
                null, null, null, null, null, "en");
    }

    private AuthorizationSnapshotDTO snapshot() {
        return new AuthorizationSnapshotDTO(
                "user-123",
                "USER",
                Set.of("READ:PROBLEM", "WRITE:PROBLEM"),
                9L,
                List.of(
                        new PermissionEntry("READ", "PROBLEM", "role", null),
                        new PermissionEntry("WRITE", "PROBLEM", "direct",
                                OffsetDateTime.parse("2026-01-02T00:00:00Z"))));
    }
}
