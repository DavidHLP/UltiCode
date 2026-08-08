package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.PermissionEntry;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
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
            when(userProfileQueryService.getProfileByAccountId("user-123")).thenReturn(RpcResult.success(profile, "t-123"));

            AdminUserVO vo = projection.getUserById("user-123");

            assertThat(vo.getId()).isEqualTo("user-123");
            assertThat(vo.getUsername()).isEqualTo("testuser");
            assertThat(vo.getName()).isEqualTo("Test User");
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when account is absent")
        void getUserByIdNotFound() {
            when(accountQueryService.getAccountById("user-999")).thenReturn(RpcResult.failure(com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND, "t-123"));

            assertThatThrownBy(() -> projection.getUserById("user-999"))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
