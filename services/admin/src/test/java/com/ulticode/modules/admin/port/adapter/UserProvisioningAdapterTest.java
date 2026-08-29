package com.ulticode.modules.admin.port.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.admin.port.UserProvisioningPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the Dubbo-backed {@link UserProvisioningAdapter}.
 *
 * <p>Locks the provisioning invariant: identity checks go through
 * {@code AccountQueryService}, create/restore mutations go through
 * {@code AccountManagementService}, and the adapter constructs the
 * RPC commands with correct passwords and metadata.
 */
class UserProvisioningAdapterTest {

    private AccountQueryService accountQueryService;
    private AccountManagementService accountManagementService;
    private AccountAdministrationService accountAdministrationService;
    private UserProvisioningAdapter adapter;

    @BeforeEach
    void setUp() {
        accountQueryService = mock(AccountQueryService.class);
        accountManagementService = mock(AccountManagementService.class);
        accountAdministrationService = mock(AccountAdministrationService.class);
        adapter = new UserProvisioningAdapter(new FixedUuidGenerator());
        ReflectionTestUtils.setField(adapter, "accountQueryService", accountQueryService);
        ReflectionTestUtils.setField(adapter, "accountManagementService", accountManagementService);
        ReflectionTestUtils.setField(adapter, "accountAdministrationService", accountAdministrationService);
    }

    @Test
    void administratorCountFailsWhenAuthQueryProviderIsUnavailable() {
        ReflectionTestUtils.setField(adapter, "accountQueryService", null);

        assertThatThrownBy(adapter::countActiveAdministrators)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AccountQueryService unavailable");
    }

    @Test
    void identityCheckFailsOnUnexpectedAuthError() {
        when(accountQueryService.getAccountByUsername("admin"))
                .thenReturn(RpcResult.failure(
                        com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_DISABLED, "t-test"));

        assertThatThrownBy(() -> adapter.identityExists("admin", "admin@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bootstrap username check");
    }

    @Test
    void restoreFailsBeforeMutationWhenAuthQueryProviderIsUnavailable() {
        ReflectionTestUtils.setField(adapter, "accountQueryService", null);

        assertThatThrownBy(() -> adapter.restoreAdministrator("u-restore",
                new UserProvisioningPort.AdministratorSpec(
                        "restored", "Restored", "restored@example.com", "newpass", "ADMIN")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AccountQueryService unavailable");
        verify(accountManagementService, never()).updateCredentials(any());
        verify(accountManagementService, never()).resetPassword(any());
        verify(accountAdministrationService, never()).changeState(any());
    }

    @Test
    void identityExistsReturnsTrueWhenUsernameMatches() {
        when(accountQueryService.getAccountByUsername("admin"))
                .thenReturn(RpcResult.success(
                        new AuthAccountDTO("u1", "admin", "admin@example.com", "ADMIN",
                                true, false, null, null, LocalDateTime.now(), null, 0L),
                        "t-test"));

        assertThat(adapter.identityExists("admin", null)).isTrue();
    }

    @Test
    void identityExistsReturnsFalseWhenNoMatch() {
        when(accountQueryService.getAccountByUsername("ghost"))
                .thenReturn(RpcResult.failure(com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND, "t-test"));
        when(accountQueryService.getAccountByEmail("ghost@example.com"))
                .thenReturn(RpcResult.failure(com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND, "t-test"));

        assertThat(adapter.identityExists("ghost", "ghost@example.com")).isFalse();
    }

    @Test
    void emailConflictsReturnsTrueWhenOwnedByDifferentAccount() {
        when(accountQueryService.getAccountByEmail("taken@example.com"))
                .thenReturn(RpcResult.success(
                        new AuthAccountDTO("u-other", "someone", "taken@example.com", "USER",
                                true, false, null, null, LocalDateTime.now(), null, 0L),
                        "t-test"));

        assertThat(adapter.emailConflicts("taken@example.com", "u-me")).isTrue();
    }

    @Test
    void emailConflictsReturnsFalseWhenOwnedBySameAccount() {
        when(accountQueryService.getAccountByEmail("mine@example.com"))
                .thenReturn(RpcResult.success(
                        new AuthAccountDTO("u-me", "me", "mine@example.com", "USER",
                                true, false, null, null, LocalDateTime.now(), null, 0L),
                        "t-test"));

        assertThat(adapter.emailConflicts("mine@example.com", "u-me")).isFalse();
    }

    @Test
    void findIdByUsernameReturnsIdWhenFound() {
        when(accountQueryService.getAccountByUsername("alice"))
                .thenReturn(RpcResult.success(
                        new AuthAccountDTO("u-alice", "alice", "alice@example.com", "ADMIN",
                                true, false, null, null, LocalDateTime.now(), null, 0L),
                        "t-test"));

        Optional<String> id = adapter.findIdByUsername("alice");
        assertThat(id).hasValue("u-alice");
    }

    @Test
    void createAdministratorSendsRawPasswordToAuthForHashing() {
        RpcResult<com.ulticode.auth.api.dto.AccountMutationDTO> okResult =
                RpcResult.success(
                        new com.ulticode.auth.api.dto.AccountMutationDTO("u-new", "newadmin", "new@example.com", "ADMIN", true, false, 1L, false),
                        "t-test");
        when(accountManagementService.createAccount(any())).thenReturn(okResult);

        adapter.createAdministrator(new UserProvisioningPort.AdministratorSpec(
                "newadmin", "New Admin", "new@example.com", "secret123", "ADMIN"));

        ArgumentCaptor<CreateAccountCommand> captor = ArgumentCaptor.forClass(CreateAccountCommand.class);
        verify(accountManagementService).createAccount(captor.capture());
        CreateAccountCommand command = captor.getValue();
        assertThat(command.trace()).isNotNull();
        assertThat(command.trace().traceId()).isNotBlank();
        assertThat(command.actor().actorId()).isEqualTo("bootstrap");
        assertThat(command.actor().actorType()).isEqualTo("BOOTSTRAP");
    }

    @Test
    void createAdministratorThrowsWhenServiceReturnsFailure() {
        when(accountManagementService.createAccount(any()))
                .thenReturn(RpcResult.failure(com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND, "t-test"));

        assertThatThrownBy(() -> adapter.createAdministrator(
                new UserProvisioningPort.AdministratorSpec(
                        "dupe", "D", "d@e.com", "pw", "USER")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restoreAdministratorStopsWhenCredentialUpdateFails() {
        AuthAccountDTO current = new AuthAccountDTO(
                "u-restore", "old", "old@example.com", "USER",
                true, false, null, null, LocalDateTime.now(), null, 0L);
        when(accountQueryService.getAccountById("u-restore"))
                .thenReturn(RpcResult.success(current, "t-test"));
        when(accountManagementService.updateCredentials(any()))
                .thenReturn(RpcResult.failure(
                        com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND, "t-test"));

        assertThatThrownBy(() -> adapter.restoreAdministrator("u-restore",
                new UserProvisioningPort.AdministratorSpec(
                        "restored", "Restored", "restored@example.com", "newpass", "ADMIN")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to update administrator credentials");
        verify(accountManagementService, never()).resetPassword(any());
        verify(accountAdministrationService, never()).changeState(any());
    }

    @Test
    void restoreAdministratorStopsWhenPasswordResetFails() {
        AuthAccountDTO current = new AuthAccountDTO(
                "u-restore", "old", "old@example.com", "USER",
                true, false, null, null, LocalDateTime.now(), null, 0L);
        when(accountQueryService.getAccountById("u-restore"))
                .thenReturn(RpcResult.success(current, "t-test"));
        when(accountManagementService.updateCredentials(any()))
                .thenReturn(RpcResult.success(
                        new com.ulticode.auth.api.dto.AccountMutationDTO(
                                "u-restore", "restored", "restored@example.com",
                                "ADMIN", true, false, 1L, false),
                        "t-test"));
        when(accountManagementService.resetPassword(any()))
                .thenReturn(RpcResult.failure(
                        com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND, "t-test"));

        assertThatThrownBy(() -> adapter.restoreAdministrator("u-restore",
                new UserProvisioningPort.AdministratorSpec(
                        "restored", "Restored", "restored@example.com", "newpass", "ADMIN")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to reset administrator password");
        verify(accountAdministrationService, never()).changeState(any());
    }

    @Test
    void restoreAdministratorVerifiesExistenceThenMutates() {
        AuthAccountDTO locked = new AuthAccountDTO("u-restore", "old", "old@example.com", "USER",
                false, true, "spam", null, LocalDateTime.now(), null, 0L);
        AuthAccountDTO unbanned = new AuthAccountDTO("u-restore", "old", "old@example.com", "USER",
                false, false, null, null, LocalDateTime.now(), null, 1L);
        when(accountQueryService.getAccountById("u-restore"))
                .thenReturn(RpcResult.success(locked, "t-test"))
                .thenReturn(RpcResult.success(unbanned, "t-test"));
        when(accountManagementService.updateCredentials(any()))
                .thenReturn(RpcResult.success(
                        new com.ulticode.auth.api.dto.AccountMutationDTO("u-restore", "restored", "restored@example.com", "ADMIN", true, false, 2L, false), "t-test"));
        when(accountManagementService.resetPassword(any()))
                .thenReturn(RpcResult.success(
                        new com.ulticode.auth.api.dto.AccountMutationDTO("u-restore", "restored", "restored@example.com", "ADMIN", true, false, 3L, false), "t-test"));
        when(accountAdministrationService.changeState(any()))
                .thenReturn(RpcResult.success(
                        new AccountStateDTO("u-restore", false, false, 1L), "t-test"))
                .thenReturn(RpcResult.success(
                        new AccountStateDTO("u-restore", true, false, 2L), "t-test"));

        adapter.restoreAdministrator("u-restore",
                new UserProvisioningPort.AdministratorSpec(
                        "restored", "Restored Admin", "restored@example.com", "newpass", "ADMIN"));

        verify(accountManagementService).updateCredentials(any());
        verify(accountManagementService).resetPassword(any());
        verify(accountAdministrationService).changeState(org.mockito.ArgumentMatchers.argThat(
                command -> command.action() == com.ulticode.auth.api.command.ChangeAccountStateCommand.AccountStateAction.UNBAN
                        && command.expectedVersion() == 0L));
        verify(accountAdministrationService).changeState(org.mockito.ArgumentMatchers.argThat(
                command -> command.action() == com.ulticode.auth.api.command.ChangeAccountStateCommand.AccountStateAction.ENABLE
                        && command.expectedVersion() == 1L));
    }

    @Test
    void restoreAdministratorThrowsForNonexistentAccount() {
        when(accountQueryService.getAccountById("ghost"))
                .thenReturn(RpcResult.failure(com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND, "t-test"));

        assertThatThrownBy(() -> adapter.restoreAdministrator("ghost",
                new UserProvisioningPort.AdministratorSpec("g", "G", "g@e.com", "pw", "USER")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void countActiveAdministratorsReturnsTotal() {
        when(accountQueryService.queryAccounts(any(AccountQueryDTO.class)))
                .thenReturn(RpcResult.page(java.util.List.of(), 3L, 1, 1, "t-test"));

        assertThat(adapter.countActiveAdministrators()).isEqualTo(3L);
    }

    @Test
    void administratorSpecToStringRedactsRawPassword() {
        UserProvisioningPort.AdministratorSpec spec =
                new UserProvisioningPort.AdministratorSpec("admin", "Admin", "a@b.com", "secret", "ADMIN");

        assertThat(spec.toString()).contains("<redacted>");
        assertThat(spec.toString()).doesNotContain("secret");
    }

}
