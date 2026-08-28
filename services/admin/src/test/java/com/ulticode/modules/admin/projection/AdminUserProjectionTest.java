package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.response.PageResult;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.RoleTemplateService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.port.AdminUserStatsReadPort;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAdminUserProjection")
class AdminUserProjectionTest {

    @Mock private AccountQueryService accountQueryService;
    @Mock private UserProfileQueryService userProfileQueryService;
    @Mock private AdminUserStatsReadPort userStatsReadPort;
    @Mock private AuthorizationSnapshotService authorizationSnapshotService;
    @Mock private RoleTemplateService roleTemplateService;

    private DefaultAdminUserProjection projection;

    private AuthAccountDTO createValidAccount() {
        return new AuthAccountDTO(
                "user-123", "testuser", "test@example.com", "ADMIN",
                true, false, null, null,
                LocalDateTime.now(), LocalDateTime.now(), 1L);
    }

    private UserProfileDTO createValidProfile() {
        return new UserProfileDTO(
                "user-123", "Test User", "https://avatar.com/123", "bio",
                "Acme", "github", "Beijing", "twitter", "website", "zh-CN");
    }

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminUserProjection(
                accountQueryService, userProfileQueryService, userStatsReadPort, authorizationSnapshotService, roleTemplateService);
    }

    @Test
    void crossOwnerAggregationLivesOnlyInAdminUserEnricher() {
        assertThat(Arrays.stream(DefaultAdminUserProjection.class.getDeclaredFields())
                .map(Field::getType))
                .contains(AdminUserEnricher.class)
                .doesNotContain(AccountQueryService.class, UserProfileQueryService.class);
    }

    @Nested
    @DisplayName("getUsers()")
    class GetUsers {

        @Test
        @DisplayName("queries account and profile RPC services and maps VO correctly")
        void queriesRpcServices() {
            AuthAccountDTO account = createValidAccount();
            UserProfileDTO profile = createValidProfile();

            when(accountQueryService.queryAccounts(any(AccountQueryDTO.class)))
                    .thenReturn(RpcResult.page(List.of(account), 1L, 1, 10, "t-123"));
            when(userProfileQueryService.getProfilesByAccountIds(any()))
                    .thenReturn(RpcResult.success(List.of(profile), "t-123"));

            var result = projection.getUsers(new AdminUserQueryDTO());

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getId()).isEqualTo("user-123");
            assertThat(result.getItems().get(0).getName()).isEqualTo("Test User");
            assertThat(result.getDegradationStatus()).isEqualTo(DegradationStatus.OK);
        }

        @Test
        @DisplayName("auth query RPC failure -> 503 OWNER_QUERY_UNAVAILABLE, not an empty page")
        void authQueryDownThrowsUnavailable() {
            when(accountQueryService.queryAccounts(any(AccountQueryDTO.class)))
                    .thenThrow(new RuntimeException("rpc down"));

            assertThatThrownBy(() -> projection.getUsers(new AdminUserQueryDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE);
                        assertThat(((AdminErrorCode) be.getErrorCode()).getHttpStatus())
                                .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    });
        }

        @Test
        @DisplayName("auth query failure result -> 503 OWNER_QUERY_UNAVAILABLE, not an empty page")
        void authQueryFailureResultThrowsUnavailable() {
            when(accountQueryService.queryAccounts(any(AccountQueryDTO.class)))
                    .thenReturn(RpcResult.failure(AuthErrorCode.INVALID_ACCOUNT_REQUEST, "t"));

            assertThatThrownBy(() -> projection.getUsers(new AdminUserQueryDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
        }

        @Test
        @DisplayName("unwired AccountQueryService (provider never registered) -> 503, not an empty page")
        void unwiredAuthThrowsUnavailable() {
            projection = new DefaultAdminUserProjection(
                    null, userProfileQueryService, userStatsReadPort,
                    authorizationSnapshotService, roleTemplateService);

            assertThatThrownBy(() -> projection.getUsers(new AdminUserQueryDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
        }

        @Test
        @DisplayName("profile provider down while auth is healthy -> PARTIAL marker, items kept")
        void profileDownMarksPartial() {
            AuthAccountDTO account = createValidAccount();

            when(accountQueryService.queryAccounts(any(AccountQueryDTO.class)))
                    .thenReturn(RpcResult.page(List.of(account), 1L, 1, 10, "t-123"));
            when(userProfileQueryService.getProfilesByAccountIds(any()))
                    .thenThrow(new RuntimeException("app provider down"));

            PageResult<AdminUserVO> result = projection.getUsers(new AdminUserQueryDTO());

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getUsername()).isEqualTo("testuser");
            assertThat(result.getItems().get(0).getName()).isNull();
            assertThat(result.getDegradationStatus()).isEqualTo(DegradationStatus.PARTIAL);
        }

        @Test
        @DisplayName("empty result from a healthy auth query stays business-empty with OK status")
        void businessEmptyStaysOk() {
            when(accountQueryService.queryAccounts(any(AccountQueryDTO.class)))
                    .thenReturn(RpcResult.page(List.of(), 0L, 1, 10, "t-123"));

            PageResult<AdminUserVO> result = projection.getUsers(new AdminUserQueryDTO());

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getTotal()).isZero();
            assertThat(result.getDegradationStatus()).isEqualTo(DegradationStatus.OK);
        }
    }

    @Nested
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("returns full user VO when account exists")
        void getUserByIdSuccess() {
            AuthAccountDTO account = createValidAccount();
            UserProfileDTO profile = createValidProfile();

            when(accountQueryService.getAccountById("user-123")).thenReturn(RpcResult.success(account, "t-123"));
            when(userProfileQueryService.getProfilesByAccountIds(any()))
                    .thenReturn(RpcResult.success(List.of(profile), "t-123"));

            AdminUserVO vo = projection.getUserById("user-123");

            assertThat(vo.getId()).isEqualTo("user-123");
            assertThat(vo.getUsername()).isEqualTo("testuser");
            assertThat(vo.getName()).isEqualTo("Test User");
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when account is absent (authoritative Auth answer)")
        void getUserByIdNotFound() {
            when(accountQueryService.getAccountById("user-999")).thenReturn(RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-123"));

            assertThatThrownBy(() -> projection.getUserById("user-999"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AdminErrorCode.USER_NOT_FOUND));
        }

        @Test
        @DisplayName("account RPC transport failure -> 503 OWNER_QUERY_UNAVAILABLE, not USER_NOT_FOUND")
        void getUserByIdTransportFailureThrowsUnavailable() {
            when(accountQueryService.getAccountById("user-123"))
                    .thenThrow(new RuntimeException("rpc down"));

            assertThatThrownBy(() -> projection.getUserById("user-123"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
        }

        @Test
        @DisplayName("non-NOT_FOUND account RPC failure -> 503 OWNER_QUERY_UNAVAILABLE, not USER_NOT_FOUND")
        void getUserByIdOtherFailureThrowsUnavailable() {
            when(accountQueryService.getAccountById("user-123")).thenReturn(RpcResult.failure(AuthErrorCode.INVALID_ACCOUNT_REQUEST, "t-123"));

            assertThatThrownBy(() -> projection.getUserById("user-123"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
        }

        @Test
        @DisplayName("profile provider down on detail view -> PARTIAL marker on the VO")
        void getUserByIdProfileDownMarksPartial() {
            AuthAccountDTO account = createValidAccount();

            when(accountQueryService.getAccountById("user-123")).thenReturn(RpcResult.success(account, "t-123"));
            when(userProfileQueryService.getProfilesByAccountIds(any()))
                    .thenThrow(new RuntimeException("app provider down"));

            AdminUserVO vo = projection.getUserById("user-123");

            assertThat(vo.getUsername()).isEqualTo("testuser");
            assertThat(vo.getName()).isNull();
            assertThat(vo.getDegradationStatus()).isEqualTo(DegradationStatus.PARTIAL);
        }
    }
}
