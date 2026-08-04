package com.ulticode.modules.admin.service;

import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.impl.UserManagementServiceImpl;
import com.ulticode.modules.user.port.UserProfilePort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserManagementServiceImplTest {

    @Mock private AccountManagementService accountManagementService;
    @Mock private AccountQueryService accountQueryService;
    @Mock private AccountAdministrationService accountAdministrationService;
    @Mock private UserProfilePort userProfilePort;
    @Mock private AuditRecorder auditRecorder;
    @Mock private AdminUserProjection adminUserProjection;

    @InjectMocks
    private UserManagementServiceImpl service;

    private AuthAccountDTO sampleAccount;
    private AdminUserVO sampleVO;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "accountManagementService", accountManagementService);
        ReflectionTestUtils.setField(service, "accountQueryService", accountQueryService);
        ReflectionTestUtils.setField(service, "accountAdministrationService", accountAdministrationService);

        sampleAccount = new AuthAccountDTO(
                "user-100", "alice", "alice@example.com", "USER",
                true, false, null, null,
                LocalDateTime.now(), LocalDateTime.now(), 1L);

        sampleVO = new AdminUserVO();
        sampleVO.setId("user-100");
        sampleVO.setUsername("alice");
        sampleVO.setEmail("alice@example.com");
    }

    @AfterEach
    void clearAuditContext() {
        com.ulticode.common.util.AuditContext.clear();
    }

    @Test
    @DisplayName("createUser creates account on Auth provider and returns projected VO")
    void createUserSuccess() {
        AdminCreateUserDTO dto = new AdminCreateUserDTO();
        dto.setUsername("alice");
        dto.setEmail("alice@example.com");
        dto.setPassword("pass12345");
        dto.setRole("USER");

        when(accountQueryService.getAccountByUsername("alice"))
                .thenReturn(RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-123"));
        when(accountQueryService.getAccountByEmail("alice@example.com"))
                .thenReturn(RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, "t-123"));

        AccountMutationDTO mutationDTO = new AccountMutationDTO(
                "user-100", "alice", "alice@example.com", "USER", true, false, 0L, false);
        when(accountManagementService.createAccount(any())).thenReturn(RpcResult.success(mutationDTO, "t-123"));
        when(adminUserProjection.getUserById("user-100")).thenReturn(sampleVO);

        AdminUserVO result = service.createUser(dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("user-100");
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("bulkBan processes ban for all requested IDs")
    void bulkBanSuccess() {
        when(accountQueryService.getAccountById("user-100")).thenReturn(RpcResult.success(sampleAccount, "t-123"));
        AccountStateDTO stateDTO = new AccountStateDTO("user-100", true, true, 2L);
        when(accountAdministrationService.changeState(any())).thenReturn(RpcResult.success(stateDTO, "t-123"));
        when(adminUserProjection.getUserById("user-100")).thenReturn(sampleVO);

        List<UserManagementService.BanResult> results = service.bulkBan(List.of("user-100"), "test ban");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
    }
}
