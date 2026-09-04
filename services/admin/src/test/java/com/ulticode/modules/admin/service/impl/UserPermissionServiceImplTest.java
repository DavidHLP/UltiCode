package com.ulticode.modules.admin.service.impl;

import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.AuthorizationMutationService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPermissionServiceImplTest {

    @Mock
    private AuthorizationMutationService authorizationMutationService;
    @Mock
    private AccountQueryService accountQueryService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private UserPermissionServiceImpl userPermissionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userPermissionService = new UserPermissionServiceImpl(currentUserProvider);
        ReflectionTestUtils.setField(
                userPermissionService, "authorizationMutationService", authorizationMutationService);
        ReflectionTestUtils.setField(
                userPermissionService, "accountQueryService", accountQueryService);
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-001");
        when(accountQueryService.getAccountById("user-123"))
                .thenReturn(RpcResult.success(account(7L), "t-test"));
        when(authorizationMutationService.mutatePermission(any()))
                .thenReturn(RpcResult.success(
                        new AuthorizationMutationDTO(
                                "user-123", "GRANT", "READ", "PROBLEM",
                                "direct", null, 8L, true),
                        "t-test"));
    }

    @Test
    void grantUsesNarrowAuthDeltaAndCarriesExpiryAndActor() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 31, 23, 59);
        OffsetDateTime expiresAtWire = expiresAt.atOffset(ZoneOffset.UTC);
        AuthorizationMutationDTO result = userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", expiresAt);

        assertThat(result.accountId()).isEqualTo("user-123");
        ArgumentCaptor<PermissionMutationCommand> captor =
                ArgumentCaptor.forClass(PermissionMutationCommand.class);
        verify(authorizationMutationService).mutatePermission(captor.capture());
        PermissionMutationCommand command = captor.getValue();
        assertThat(command.operation()).isEqualTo(PermissionMutationCommand.Operation.GRANT);
        assertThat(command.expiresAt()).isEqualTo(expiresAtWire);
        assertThat(command.expectedVersion()).isEqualTo(7L);
        assertThat(command.actorId()).isEqualTo("admin-001");
    }

    @Test
    void revokeUsesDeltaAndDoesNotReadFullUserDetail() {
        when(authorizationMutationService.mutatePermission(any()))
                .thenReturn(RpcResult.success(
                        new AuthorizationMutationDTO(
                                "user-123", "REVOKE", "READ", "PROBLEM",
                                "direct", null, 8L, true),
                        "t-test"));

        userPermissionService.revokeUserPermission("user-123", "READ", "PROBLEM");

        ArgumentCaptor<PermissionMutationCommand> captor =
                ArgumentCaptor.forClass(PermissionMutationCommand.class);
        verify(authorizationMutationService).mutatePermission(captor.capture());
        assertThat(captor.getValue().operation())
                .isEqualTo(PermissionMutationCommand.Operation.REVOKE);
    }

    @Test
    void authMutationFailureIsMappedAndDoesNotReportSuccess() {
        when(authorizationMutationService.mutatePermission(any()))
                .thenReturn(RpcResult.failure(
                        com.ulticode.auth.api.error.AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT,
                        "t-test"));

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(AdminErrorCode.CONFLICT));
    }

    @Test
    void authValidationAndIdempotencyFailuresMapToClientErrors() {
        when(authorizationMutationService.mutatePermission(any()))
                .thenReturn(
                        RpcResult.failure(
                                com.ulticode.common.error.BaseErrorCode.VALIDATION_FAILED,
                                "t-test"),
                        RpcResult.failure(
                                com.ulticode.auth.api.error.AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                                "t-test"));

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(AdminErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> userPermissionService.revokeUserPermission(
                "user-123", "READ", "PROBLEM"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(AdminErrorCode.CONFLICT));
    }

    @Test
    void missingAuthMutationProviderFailsClosed() {
        ReflectionTestUtils.setField(userPermissionService, "authorizationMutationService", null);

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
    }

    @Test
    void authAccountUnavailableFailsClosedWithoutMutation() {
        when(accountQueryService.getAccountById("user-123"))
                .thenReturn(RpcResult.failure(
                        com.ulticode.auth.api.error.AuthErrorCode.UNEXPECTED_AUTH_STATE,
                        "t-test"));

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
        verify(authorizationMutationService, never()).mutatePermission(any());
    }
    @Test
    void authAccountTransportFailureFailsClosed() {
        when(accountQueryService.getAccountById("user-123"))
                .thenThrow(new RpcException("offline"));

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", "READ", "PROBLEM", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE));
        verify(authorizationMutationService, never()).mutatePermission(any());
    }

    @Test
    void systemPermissionStillRequiresSuperAdmin() {
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> userPermissionService.assignUserPermission(
                "user-123", " MANAGE_PERMISSIONS ", " SYSTEM ", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(AdminErrorCode.FORBIDDEN));
        verify(authorizationMutationService, never()).mutatePermission(any());
    }

    private static AuthAccountDTO account(long version) {
        return new AuthAccountDTO(
                "user-123", "alice", "alice@example.com", "USER", true, false,
                null, null, LocalDateTime.of(2025, 1, 1, 0, 0), null, version);
    }
}
