package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AccountManagementPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.auth.util.UuidGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * Domain-facing account-management engine used by the Auth RPC provider.
 *
 * <p>The engine contains no RPC receipt concerns. The provider owns the
 * transaction that includes both this engine and receipt finalization.
 */
@Component
public class AccountManagementEngine {

    private static final Set<String> VALID_ROLES = Set.of(
            "USER", "MODERATOR", "ADMIN", "SUPER_ADMIN");

    private final AccountManagementPort accountPort;
    private final PasswordEncoder passwordEncoder;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public AccountManagementEngine(AccountManagementPort accountPort,
                                   PasswordEncoder passwordEncoder,
                                   UuidGenerator uuidGenerator,
                                   Clock clock) {
        this.accountPort = accountPort;
        this.passwordEncoder = passwordEncoder;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    public RpcResult<AccountMutationDTO> create(CreateAccountCommand command,
                                                String traceId) {
        String role = command.role().trim().toUpperCase(Locale.ROOT);
        if (!VALID_ROLES.contains(role)) {
            return RpcResult.failure(AuthErrorCode.ROLE_NOT_FOUND, traceId);
        }
        String username = command.username().trim();
        String email = normalizeEmail(command.email());
        if (accountPort.findByUsername(username).isPresent()
                || (email != null && accountPort.findByEmail(email).isPresent())) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_ALREADY_EXISTS, traceId);
        }

        AuthAccountRecord created = accountPort.create(new AuthAccountRecord(
                uuidGenerator.newId(),
                username,
                email,
                passwordEncoder.encode(command.password()),
                role,
                true,
                false,
                null,
                LocalDateTime.now(clock),
                0L));
        return RpcResult.success(toDto(created, false), traceId);
    }

    public RpcResult<AccountMutationDTO> updateCredentials(
            UpdateAccountCredentialsCommand command, String traceId) {
        AuthAccountRecord current = accountPort.findById(command.accountId()).orElse(null);
        if (current == null) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        String username = command.username().trim();
        String email = normalizeEmail(command.email());
        if (accountPort.findByUsername(username)
                .filter(existing -> !existing.id().equals(current.id())).isPresent()
                || accountPort.findByEmail(email)
                .filter(existing -> !existing.id().equals(current.id())).isPresent()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_ALREADY_EXISTS, traceId);
        }
        if (!accountPort.updateCredentials(
                current.id(), username, email, command.actor().actorId())) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        AuthAccountRecord updated = accountPort.findById(current.id()).orElse(null);
        if (updated == null) {
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
        return RpcResult.success(toDto(updated, false), traceId);
    }

    public RpcResult<AccountMutationDTO> changePassword(
            ChangePasswordCommand command, String traceId) {
        AuthAccountRecord current = accountPort.findById(command.accountId()).orElse(null);
        if (current == null) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        if (current.password() == null
                || !passwordEncoder.matches(command.currentPassword(), current.password())) {
            return RpcResult.failure(AuthErrorCode.PASSWORD_MISMATCH, traceId);
        }
        if (!accountPort.updatePassword(
                current.id(), passwordEncoder.encode(command.newPassword()), command.actor().actorId())) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        AuthAccountRecord updated = accountPort.findById(current.id()).orElse(null);
        if (updated == null) {
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
        return RpcResult.success(toDto(updated, false), traceId);
    }

    public RpcResult<AccountMutationDTO> resetPassword(
            ResetPasswordCommand command, String traceId) {
        AuthAccountRecord current = accountPort.findById(command.accountId()).orElse(null);
        if (current == null) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        if (!accountPort.updatePassword(
                current.id(), passwordEncoder.encode(command.newPassword()), command.actor().actorId())) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        AuthAccountRecord updated = accountPort.findById(current.id()).orElse(null);
        if (updated == null) {
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
        return RpcResult.success(toDto(updated, false), traceId);
    }

    public RpcResult<AccountMutationDTO> deleteAccount(
            DeleteAccountCommand command, String traceId) {
        AuthAccountRecord current = accountPort.findById(command.accountId()).orElse(null);
        if (current == null) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        if (!accountPort.softDelete(current.id(), command.actor().actorId())) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        return RpcResult.success(toDto(current, true), traceId);
    }

    private AccountMutationDTO toDto(AuthAccountRecord account, boolean deleted) {
        return new AccountMutationDTO(
                account.id(),
                account.username(),
                account.email(),
                account.role(),
                Boolean.TRUE.equals(account.isActive()),
                Boolean.TRUE.equals(account.isBanned()),
                account.authzVersion(),
                deleted);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim();
    }
}
