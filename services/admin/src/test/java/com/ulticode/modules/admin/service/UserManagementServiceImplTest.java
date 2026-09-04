package com.ulticode.modules.admin.service;

import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.RoleMutationService;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.query.AdminUserDetailQuery;
import com.ulticode.modules.admin.query.AdminUserDetailResult;
import com.ulticode.modules.admin.service.impl.UserManagementServiceImpl;
import com.ulticode.admin.port.UserProfilePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserManagementServiceImplTest {

    @Mock private AccountManagementService accountManagementService;
    @Mock private AccountQueryService accountQueryService;
    @Mock private AccountAdministrationService accountAdministrationService;
    @Mock private RoleMutationService roleMutationService;
    @Mock private UserProfilePort userProfilePort;
    @Mock private AuditRecorder auditRecorder;
    @Mock private AdminUserDetailQuery adminUserDetailQuery;
    @Mock private com.ulticode.common.auth.CurrentUserProvider currentUserProvider;

    @InjectMocks
    private UserManagementServiceImpl service;

    private AuthAccountDTO sampleAccount;
    private AdminUserVO sampleVO;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "accountManagementService", accountManagementService);
        ReflectionTestUtils.setField(service, "accountQueryService", accountQueryService);
        ReflectionTestUtils.setField(service, "accountAdministrationService", accountAdministrationService);
        ReflectionTestUtils.setField(service, "roleMutationService", roleMutationService);
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");

        sampleAccount = new AuthAccountDTO(
                "user-100", "alice", "alice@example.com", "USER",
                true, false, null, null,
                LocalDateTime.now(), LocalDateTime.now(), 1L);

        sampleVO = new AdminUserVO();
        sampleVO.setId("user-100");
        sampleVO.setUsername("alice");
        sampleVO.setEmail("alice@example.com");
    }

    private AdminUserDetailResult detail(AdminUserVO user) {
        AdminUserDetailResult.Section unavailable =
                AdminUserDetailResult.Section.unavailable("not requested");
        return AdminUserDetailResult.found(user, unavailable, unavailable, unavailable, null);
    }

    @AfterEach
    void clearAuditContext() {
        com.ulticode.common.util.AuditContext.clear();
    }
    @Test
    @DisplayName("createUser rejects a missing password instead of selecting a default credential")
    void createUserRequiresPassword() {
        AdminCreateUserDTO dto = new AdminCreateUserDTO();
        dto.setUsername("alice");
        dto.setEmail("alice@example.com");

        assertThatThrownBy(() -> service.createUser(dto))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .hasMessageContaining("Password is required");
        verify(accountQueryService, never()).getAccountByUsername(any());
        verify(accountManagementService, never()).createAccount(any());
    }

    @Test
    @DisplayName("createUser creates account on Auth provider and returns projected VO")
    void createUserSuccess() {
        AdminCreateUserDTO dto = new AdminCreateUserDTO();
        dto.setUsername("alice");
        dto.setEmail("alice@example.com");
        dto.setPassword("pass12345");
        dto.setRole("USER");
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(true);

        when(accountQueryService.getAccountByUsername("alice"))
                .thenReturn(RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-123"));
        when(accountQueryService.getAccountByEmail("alice@example.com"))
                .thenReturn(RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-123"));

        AccountMutationDTO mutationDTO = new AccountMutationDTO(
                "user-100", "alice", "alice@example.com", "USER", true, false, 0L, false);
        when(accountManagementService.createAccount(any())).thenReturn(RpcResult.success(mutationDTO, "t-123"));
        when(adminUserDetailQuery.loadUserDetail("user-100")).thenReturn(detail(sampleVO));

        AdminUserVO result = service.createUser(dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("user-100");
        verify(accountManagementService).createAccount(argThat(command ->
                "SUPER_ADMIN".equals(command.actor().actorType())));
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("createUser with name issues UpdateProfileCommand carrying created accountId")
    void createUserWithNameIssuesProfileCommand() {
        AdminCreateUserDTO dto = new AdminCreateUserDTO();
        dto.setUsername("bob");
        dto.setEmail("bob@example.com");
        dto.setPassword("pass12345");
        dto.setRole("USER");
        dto.setName("Bob Builder");

        when(accountQueryService.getAccountByUsername("bob"))
                .thenReturn(RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-123"));
        when(accountQueryService.getAccountByEmail("bob@example.com"))
                .thenReturn(RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-123"));
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-user-1");

        AccountMutationDTO mutationDTO = new AccountMutationDTO(
                "user-200", "bob", "bob@example.com", "USER", true, false, 0L, false);
        when(accountManagementService.createAccount(any())).thenReturn(RpcResult.success(mutationDTO, "t-123"));
        when(adminUserDetailQuery.loadUserDetail("user-200")).thenReturn(detail(sampleVO));

        service.createUser(dto);

        verify(userProfilePort).updateProfile(argThat(cmd ->
                "user-200".equals(cmd.accountId())
                        && "Bob Builder".equals(cmd.name())
                        && "admin-user-1".equals(cmd.actor().actorId())
                        && cmd.idempotency() != null
                        && cmd.commandId() != null
                        && cmd.trace() != null));
    }
    @Test
    @DisplayName("updateUser fails instead of reporting success when the role owner rejects the change")
    void updateUserPropagatesRoleMutationFailure() {
        when(accountQueryService.getAccountById("user-100"))
                .thenReturn(RpcResult.success(sampleAccount, "t-123"));
        when(accountManagementService.updateCredentials(any()))
                .thenReturn(RpcResult.success(
                        new AccountMutationDTO("user-100", "alice", "alice@example.com", "USER",
                                true, false, 1L, false), "t-123"));
        when(userProfilePort.updateProfile(any()))
                .thenReturn(new ProfileWriteResult("user-100", null, null, null, null,
                        null, null, null, null, null));
        when(roleMutationService.changeRole(any()))
                .thenReturn(RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, "t-123"));

        AdminUpdateUserDTO dto = new AdminUpdateUserDTO();
        dto.setRole("ADMIN");

        assertThatThrownBy(() -> service.updateUser("user-100", dto))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .hasMessageContaining("Account role update failed");
    }

    @Test
    @DisplayName("createUser fails closed when the Auth query provider cannot answer the username check")
    void createUserFailsClosedWhenUsernameCheckUnavailable() {
        AdminCreateUserDTO dto = new AdminCreateUserDTO();
        dto.setUsername("alice");
        dto.setEmail("alice@example.com");
        dto.setPassword("pass12345");
        dto.setRole("USER");

        when(accountQueryService.getAccountByUsername("alice")).thenReturn(null);

        assertThatThrownBy(() -> service.createUser(dto))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .hasMessageContaining("AccountQueryService unavailable");
        verify(accountManagementService, never()).createAccount(any());
    }

    @Test
    @DisplayName("updateUser fails closed when the email conflict check returns an unexpected error")
    void updateUserFailsClosedWhenEmailCheckUnavailable() {
        when(accountQueryService.getAccountById("user-100"))
                .thenReturn(RpcResult.success(sampleAccount, "t-123"));
        when(accountQueryService.getAccountByEmail("new@example.com"))
                .thenReturn(RpcResult.failure(AuthErrorCode.ACCOUNT_DISABLED, "t-123"));

        AdminUpdateUserDTO dto = new AdminUpdateUserDTO();
        dto.setEmail("new@example.com");

        assertThatThrownBy(() -> service.updateUser("user-100", dto))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .hasMessageContaining("AccountQueryService unavailable");
        verify(accountManagementService, never()).updateCredentials(any());
    }

    @Test
    @DisplayName("bulkBan processes ban for all requested IDs")
    void bulkBanSuccess() {
        when(accountQueryService.getAccountById("user-100")).thenReturn(RpcResult.success(sampleAccount, "t-123"));
        AccountStateDTO stateDTO = new AccountStateDTO("user-100", true, true, 2L);
        when(accountAdministrationService.changeState(any())).thenReturn(RpcResult.success(stateDTO, "t-123"));
        when(adminUserDetailQuery.loadUserDetail("user-100")).thenReturn(detail(sampleVO));

        List<UserManagementService.BanResult> results = service.bulkBan(List.of("user-100"), "test ban");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
    }
}
