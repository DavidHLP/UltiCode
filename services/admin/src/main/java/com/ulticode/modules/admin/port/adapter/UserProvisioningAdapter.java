package com.ulticode.modules.admin.port.adapter;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.port.UserProvisioningPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.ulticode.common.rpc.RpcPolicy;

/**
 * Production adapter for the admin module's {@link UserProvisioningPort}.
 *
 * <p>Uses Dubbo RPC contracts ({@link AccountQueryService} +
 * {@link AccountManagementService}) instead of direct {@code UserMapper} +
 * {@code AuthAccountPort} access. Auth owns password hashing; this adapter
 * owns id/timestamp stamping and command construction — so admin/bootstrap
 * no longer imports any user-internal Legacy type.
 *
 * <p>Dubbo references use {@code check=false} and {@code required=false} so
 * the admin context loads even when Auth providers are down; provisioning
 * calls fail at invocation time, not wiring time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProvisioningAdapter implements UserProvisioningPort {

    /** Roles that count as an active administrator for bootstrap gating. */
    private static final List<String> ADMIN_ROLES = List.of("ADMIN", "SUPER_ADMIN");

    private final UuidGenerator uuidGenerator;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private AccountManagementService accountManagementService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private AccountAdministrationService accountAdministrationService;

    @Override
    public long countActiveAdministrators() {
        AccountQueryService queries = requireQueryService();
        // Count only actual administrators (ADMIN + SUPER_ADMIN); counting
        // every active account would block bootstrap whenever any regular
        // user exists.
        try {
            long total = 0L;
            for (String role : ADMIN_ROLES) {
                RpcResult<AuthAccountDTO> result = queries.queryAccounts(
                        new AccountQueryDTO(null, role, true, false, 1, 1, "joinedAt", "desc"));
                if (result == null || !result.success()
                        || result.page() == null || result.page().total() == null) {
                    throw queryUnavailable("administrator count", result, null);
                }
                total += result.page().total();
            }
            return total;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw queryUnavailable("administrator count", null, exception);
        }
    }

    @Override
    public boolean identityExists(String username, String email) {
        if (username != null && !username.isBlank()
                && queryAccountByUsername(username, "bootstrap username check").isPresent()) {
            return true;
        }
        return email != null && !email.isBlank()
                && queryAccountByEmail(email, "bootstrap email check").isPresent();
    }

    @Override
    public boolean emailConflicts(String email, String excludeId) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return queryAccountByEmail(email, "administrator email conflict check")
                .map(account -> !account.accountId().equals(excludeId))
                .orElse(false);
    }

    @Override
    public Optional<String> findIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return queryAccountByUsername(username, "administrator username lookup")
                .map(AuthAccountDTO::accountId);
    }

    private Optional<AuthAccountDTO> queryAccountByUsername(String username, String operation) {
        AccountQueryService queries = requireQueryService();
        return resolveAccountQuery(() -> queries.getAccountByUsername(username), operation);
    }

    private Optional<AuthAccountDTO> queryAccountByEmail(String email, String operation) {
        AccountQueryService queries = requireQueryService();
        return resolveAccountQuery(() -> queries.getAccountByEmail(email), operation);
    }

    private Optional<AuthAccountDTO> queryAccountById(String id, String operation) {
        AccountQueryService queries = requireQueryService();
        return resolveAccountQuery(() -> queries.getAccountById(id), operation);
    }

    private Optional<AuthAccountDTO> resolveAccountQuery(
            java.util.function.Supplier<RpcResult<AuthAccountDTO>> query, String operation) {
        try {
            RpcResult<AuthAccountDTO> result = query.get();
            if (result != null && result.success() && result.data() != null) {
                return Optional.of(result.data());
            }
            if (isAccountNotFound(result)) {
                return Optional.empty();
            }
            throw queryUnavailable(operation, result, null);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw queryUnavailable(operation, null, exception);
        }
    }

    private AccountQueryService requireQueryService() {
        if (accountQueryService == null) {
            throw queryUnavailable("account query", null, null);
        }
        return accountQueryService;
    }

    private static boolean isAccountNotFound(RpcResult<?> result) {
        return result != null && !result.success() && result.error() != null
                && result.error().code() == com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND.code();
    }

    private static IllegalStateException queryUnavailable(
            String operation, RpcResult<?> result, RuntimeException cause) {
        String detail = result != null && result.error() != null
                ? ": " + result.error().message() : "";
        String message = "AccountQueryService unavailable during " + operation + detail;
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }

    @Override
    public void createAdministrator(AdministratorSpec spec) {
        if (accountManagementService == null) {
            throw new IllegalStateException("AccountManagementService unavailable; cannot create administrator");
        }
        String commandId = UUID.randomUUID().toString();
        String idempotencyKey = uuidGenerator.newId();
        ActorDelegation actor = new ActorDelegation("BOOTSTRAP", "bootstrap", "bootstrap", "initial admin provisioning");
        CreateAccountCommand command = new CreateAccountCommand(
                commandId,
                IdMetadata.of(idempotencyKey, null),
                actor,
                currentTrace(),
                spec.username(),
                spec.email(),
                spec.rawPassword(),
                spec.role()
        );
        RpcResult<AccountMutationDTO> result = accountManagementService.createAccount(command);
        if (result == null || !result.success()) {
            throw new IllegalStateException("Failed to create administrator account: " +
                    (result != null && result.error() != null ? result.error().message() : "unknown"));
        }
    }

    @Override
    public void restoreAdministrator(String id, AdministratorSpec spec) {
        if (accountManagementService == null) {
            throw new IllegalStateException("AccountManagementService unavailable; cannot restore administrator");
        }

        // Bootstrap may only proceed after Auth confirms the account exists.
        AuthAccountDTO current = queryAccountById(id, "administrator restore preflight")
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot restore nonexistent administrator: " + id));
        String commandId = UUID.randomUUID().toString();
        String idempotencyKey = uuidGenerator.newId();
        ActorDelegation actor = new ActorDelegation("BOOTSTRAP", "bootstrap", "bootstrap", "restore admin");
        TraceMetadata trace = currentTrace();

        // Update credentials
        UpdateAccountCredentialsCommand credsCmd = new UpdateAccountCredentialsCommand(
                commandId, IdMetadata.of(idempotencyKey, null), actor, trace, id, spec.username(), spec.email());
        requireSuccessful(accountManagementService.updateCredentials(credsCmd),
                "Failed to update administrator credentials");
        // Reset password (admin doesn't know current password)
        String pwCommandId = UUID.randomUUID().toString();
        String pwIdempotencyKey = uuidGenerator.newId();
        ResetPasswordCommand pwCmd = new ResetPasswordCommand(
                pwCommandId, IdMetadata.of(pwIdempotencyKey, null), actor, trace, id, spec.rawPassword(), "admin restore");
        requireSuccessful(accountManagementService.resetPassword(pwCmd),
                "Failed to reset administrator password");

        // Restore lifecycle state: the seed-account lockdown migration
        // (V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts) may
        // have left the account banned/disabled; the documented development
        // administrator must be able to log in after a restore.
        if (current != null && (current.banned() || !current.active())) {
            if (accountAdministrationService == null) {
                throw new IllegalStateException(
                        "AccountAdministrationService unavailable; cannot restore administrator state");
            }
            if (current.banned()) {
                changeLifecycleState(id, current.authzVersion(),
                        ChangeAccountStateCommand.AccountStateAction.UNBAN);
                // The optimistic-lock version moved under us; re-read.
                current = queryAccountOrThrow(id);
            }
            if (!current.active()) {
                changeLifecycleState(id, current.authzVersion(),
                        ChangeAccountStateCommand.AccountStateAction.ENABLE);
            }
        }
    }

    private AuthAccountDTO queryAccountOrThrow(String id) {
        return queryAccountById(id, "administrator state-change re-read")
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot re-read administrator account after state change: " + id));
    }

    private void changeLifecycleState(String id, long expectedVersion,
            ChangeAccountStateCommand.AccountStateAction action) {
        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                UUID.randomUUID().toString(),
                IdMetadata.of(uuidGenerator.newId(), null),
                new ActorDelegation("BOOTSTRAP", "bootstrap", "bootstrap", "restore admin"),
                currentTrace(),
                id, expectedVersion, action, "admin restore");
        RpcResult<?> result = accountAdministrationService.changeState(command);
        if (result == null || !result.success()) {
            throw new IllegalStateException("Failed to " + action + " administrator " + id
                    + (result != null && result.error() != null ? ": " + result.error().message() : ""));
        }
    }

    private static void requireSuccessful(RpcResult<?> result, String message) {
        if (result == null || !result.success()) {
            throw new IllegalStateException(message +
                    (result != null && result.error() != null
                            ? ": " + result.error().message() : ""));
        }
    }

    private static TraceMetadata currentTrace() {
        String reqId = TraceIdUtil.current();
        if (reqId == null || reqId.isBlank()) {
            reqId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(reqId, null, null, null);
    }
}
