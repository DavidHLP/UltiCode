package com.ulticode.auth.dubbo.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.account.AccountManagementPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.idempotency.CommandReceiptExecutor;
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import com.ulticode.auth.idempotency.mapper.AuthCommandReceiptMapper;
import com.ulticode.auth.util.UuidGenerator;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountManagementProviderTest {

    private AccountManagementPort accountPort;
    private PasswordEncoder passwordEncoder;
    private UuidGenerator uuidGenerator;
    private AuthCommandReceiptMapper receiptMapper;
    private ObjectMapper objectMapper;
    private AccountManagementProvider provider;

    private ActorDelegation actor;
    private TraceMetadata trace;

    @BeforeEach
    void setUp() {
        accountPort = mock(AccountManagementPort.class);
        passwordEncoder = mock(PasswordEncoder.class);
        uuidGenerator = mock(UuidGenerator.class);
        receiptMapper = mock(AuthCommandReceiptMapper.class);
        objectMapper = new ObjectMapper();
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneOffset.UTC);

        when(uuidGenerator.newId()).thenReturn("user-uuid-100");
        when(passwordEncoder.encode(any())).thenAnswer(
                invocation -> "hashed:" + invocation.getArgument(0));

        AccountManagementEngine engine =
                new AccountManagementEngine(accountPort, passwordEncoder, uuidGenerator, clock);
        CommandReceiptExecutor receiptExecutor =
                new CommandReceiptExecutor(receiptMapper, objectMapper, clock);
        provider = new AccountManagementProvider(engine, receiptExecutor);

        actor = new ActorDelegation("ADMIN", "admin-1", "admin-1", "test account mgmt");
        trace = new TraceMetadata("t-trace-1", null, null, null);
    }

    @Test
    @DisplayName("createAccount successfully hashes password and records receipt")
    void createAccountSuccess() {
        AuthAccountRecord createdRecord = new AuthAccountRecord(
                "user-uuid-100", "newuser", "new@example.com", "hashed:secret123",
                "USER", true, false, null, null, 0L);
        when(accountPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(accountPort.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(accountPort.create(any())).thenReturn(createdRecord);

        RpcResult<AccountMutationDTO> result = provider.createAccount(createCommand(
                "cmd-create-1", IdMetadata.mint(), "newuser", "new@example.com",
                "secret123", "USER"));

        assertThat(result.success()).isTrue();
        assertThat(result.data().accountId()).isEqualTo("user-uuid-100");
        assertThat(result.data().username()).isEqualTo("newuser");
        assertThat(result.data().email()).isEqualTo("new@example.com");
        verify(receiptMapper).insert(any(AuthCommandReceiptEntity.class));
    }

    @Test
    @DisplayName("createAccount rejects duplicate username")
    void createAccountDuplicateUsername() {
        AuthAccountRecord existing = new AuthAccountRecord(
                "user-existing", "newuser", "existing@example.com", "hash",
                "USER", true, false, null, null, 0L);
        when(accountPort.findByUsername("newuser")).thenReturn(Optional.of(existing));

        RpcResult<AccountMutationDTO> result = provider.createAccount(createCommand(
                "cmd-create-1", IdMetadata.mint(), "newuser", "new@example.com",
                "secret123", "USER"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.ACCOUNT_ALREADY_EXISTS.code());
        verify(receiptMapper, never()).insert(any(AuthCommandReceiptEntity.class));
    }

    @Test
    @DisplayName("changePassword fails with PASSWORD_MISMATCH when current password fails check")
    void changePasswordMismatch() {
        AuthAccountRecord current = account("user-1", "hashed:oldpass");
        when(accountPort.findById("user-1")).thenReturn(Optional.of(current));
        when(passwordEncoder.matches("wrongpass", "hashed:oldpass")).thenReturn(false);

        ChangePasswordCommand command = new ChangePasswordCommand(
                "cmd-cp-1", IdMetadata.mint(), actor, trace,
                "user-1", "wrongpass", "newpass123");

        RpcResult<AccountMutationDTO> result = provider.changePassword(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.PASSWORD_MISMATCH.code());
    }

    @Test
    @DisplayName("updateCredentials updates username and email successfully")
    void updateCredentialsSuccess() {
        AuthAccountRecord current = account("user-1", "hash");
        AuthAccountRecord updated = new AuthAccountRecord(
                "user-1", "newuser", "new@example.com", "hash",
                "USER", true, false, null, null, 1L);
        when(accountPort.findById("user-1"))
                .thenReturn(Optional.of(current))
                .thenReturn(Optional.of(updated));
        when(accountPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(accountPort.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(accountPort.updateCredentials("user-1", "newuser", "new@example.com", "admin-1"))
                .thenReturn(true);

        UpdateAccountCredentialsCommand command = new UpdateAccountCredentialsCommand(
                "cmd-uc-1", IdMetadata.mint(), actor, trace,
                "user-1", "newuser", "new@example.com");

        RpcResult<AccountMutationDTO> result = provider.updateCredentials(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().username()).isEqualTo("newuser");
        assertThat(result.data().email()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("resetPassword sets new password without old password check")
    void resetPasswordSuccess() {
        AuthAccountRecord current = account("user-1", "hashed:oldpass");
        AuthAccountRecord updated = new AuthAccountRecord(
                "user-1", "user1", "u1@example.com", "hashed:resetpass",
                "USER", true, false, null, null, 1L);
        when(accountPort.findById("user-1"))
                .thenReturn(Optional.of(current))
                .thenReturn(Optional.of(updated));
        when(passwordEncoder.encode("resetpass")).thenReturn("hashed:resetpass");
        when(accountPort.updatePassword("user-1", "hashed:resetpass", "admin-1"))
                .thenReturn(true);

        ResetPasswordCommand command = new ResetPasswordCommand(
                "cmd-rp-1", IdMetadata.mint(), actor, trace,
                "user-1", "resetpass", "admin reset password");

        RpcResult<AccountMutationDTO> result = provider.resetPassword(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().accountId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("deleteAccount soft-deletes account")
    void deleteAccountSuccess() {
        AuthAccountRecord current = account("user-1", "hash");
        when(accountPort.findById("user-1")).thenReturn(Optional.of(current));
        when(accountPort.softDelete("user-1", "admin-1")).thenReturn(true);

        DeleteAccountCommand command = new DeleteAccountCommand(
                "cmd-del-1", IdMetadata.mint(), actor, trace,
                "user-1", "soft delete account");

        RpcResult<AccountMutationDTO> result = provider.deleteAccount(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().deleted()).isTrue();
    }

    @Test
    @DisplayName("replay returns stored payload and skips engine on duplicate key")
    void idempotencyReplay() throws Exception {
        IdMetadata idempotency = IdMetadata.of("key-dup-1", null);
        CreateAccountCommand command = createCommand(
                "cmd-1", idempotency, "user1", "u1@example.com", "pass123", "USER");
        AccountMutationDTO cached = new AccountMutationDTO(
                "user-1", "user1", "u1@example.com", "USER", true, false, 0L, false);
        AuthCommandReceiptEntity receipt = new AuthCommandReceiptEntity();
        receipt.setStatus("SUCCESS");
        receipt.setRequestFingerprint(CommandReceiptExecutor.fingerprint(command));
        receipt.setResultPayload(objectMapper.writeValueAsString(cached));
        when(receiptMapper.findByReceiptKey(
                "AccountManagementService", "createAccount", "key-dup-1"))
                .thenReturn(receipt);

        RpcResult<AccountMutationDTO> result = provider.createAccount(command);

        assertThat(result.success()).isTrue();
        assertThat(result.data().accountId()).isEqualTo("user-1");
        verify(accountPort, never()).create(any());
    }

    @Test
    @DisplayName("same key with a different account payload returns conflict")
    void idempotencyConflict() {
        IdMetadata idempotency = IdMetadata.of("key-dup-1", null);
        CreateAccountCommand first = createCommand(
                "cmd-1", idempotency, "user1", "u1@example.com", "pass123", "USER");
        AuthCommandReceiptEntity receipt = new AuthCommandReceiptEntity();
        receipt.setStatus("SUCCESS");
        receipt.setRequestFingerprint(CommandReceiptExecutor.fingerprint(first));
        when(receiptMapper.findByReceiptKey(
                "AccountManagementService", "createAccount", "key-dup-1"))
                .thenReturn(receipt);

        CreateAccountCommand changed = createCommand(
                "cmd-2", idempotency, "user2", "u1@example.com", "pass123", "USER");

        RpcResult<AccountMutationDTO> result = provider.createAccount(changed);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT.code());
    }

    @Test
    @DisplayName("receipt insert failure returns an auth failure")
    void receiptInsertFailure() {
        when(accountPort.findByUsername("newuser")).thenReturn(Optional.empty());
        when(accountPort.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(accountPort.create(any())).thenReturn(account(
                "user-uuid-100", "hashed:secret123"));
        org.mockito.Mockito.doThrow(new IllegalStateException("receipt unavailable"))
                .when(receiptMapper).insert(any(AuthCommandReceiptEntity.class));

        RpcResult<AccountMutationDTO> result = provider.createAccount(createCommand(
                "cmd-receipt-failure", IdMetadata.mint(), "newuser",
                "new@example.com", "secret123", "USER"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.UNEXPECTED_AUTH_STATE.code());
    }

    private CreateAccountCommand createCommand(
            String commandId,
            IdMetadata idempotency,
            String username,
            String email,
            String password,
            String role) {
        return new CreateAccountCommand(
                commandId, idempotency, actor, trace, username, email, password, role);
    }

    private static AuthAccountRecord account(String id, String password) {
        return new AuthAccountRecord(
                id, "user1", "u1@example.com", password,
                "USER", true, false, null, null, 1L);
    }
}
