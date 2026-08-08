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
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.port.UserProvisioningPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production adapter for the admin module's {@link UserProvisioningPort}.
 *
 * <p>Uses Dubbo RPC contracts ({@link AccountQueryService} +
 * {@link AccountManagementService}) instead of direct {@code UserMapper} +
 * {@code AuthAccountPort} access. The adapter still owns password encoding,
 * id/timestamp stamping, and all command construction — so admin/bootstrap
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

    private final PasswordEncoder passwordEncoder;
    private final UuidGenerator uuidGenerator;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private AccountQueryService accountQueryService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 0, check = false)
    private AccountManagementService accountManagementService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 0, check = false)
    private AccountAdministrationService accountAdministrationService;

    @Override
    public long countActiveAdministrators() {
        if (accountQueryService == null) {
            return 0L;
        }
        try {
            RpcResult<AuthAccountDTO> result = accountQueryService.queryAccounts(
                    new AccountQueryDTO(null, null, true, false, 1, 1, "joinedAt", "desc"));
            if (result != null && result.page() != null && result.page().total() != null) {
                return result.page().total();
            }
        } catch (Exception e) {
            log.warn("AccountQueryService.queryAccounts failed for admin count: {}", e.getMessage());
        }
        return 0L;
    }

    @Override
    public boolean identityExists(String username, String email) {
        if (accountQueryService == null) {
            return false;
        }
        try {
            if (username != null && !username.isBlank()) {
                RpcResult<AuthAccountDTO> byUsername = accountQueryService.getAccountByUsername(username);
                if (byUsername != null && byUsername.success() && byUsername.data() != null) {
                    return true;
                }
            }
            if (email != null && !email.isBlank()) {
                RpcResult<AuthAccountDTO> byEmail = accountQueryService.getAccountByEmail(email);
                if (byEmail != null && byEmail.success() && byEmail.data() != null) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("AccountQueryService identity check failed: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public boolean emailConflicts(String email, String excludeId) {
        if (accountQueryService == null || email == null || email.isBlank()) {
            return false;
        }
        try {
            RpcResult<AuthAccountDTO> result = accountQueryService.getAccountByEmail(email);
            if (result != null && result.success() && result.data() != null) {
                return !result.data().accountId().equals(excludeId);
            }
        } catch (Exception e) {
            log.warn("AccountQueryService email conflict check failed: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public Optional<String> findIdByUsername(String username) {
        if (accountQueryService == null || username == null || username.isBlank()) {
            return Optional.empty();
        }
        try {
            RpcResult<AuthAccountDTO> result = accountQueryService.getAccountByUsername(username);
            if (result != null && result.success() && result.data() != null) {
                return Optional.of(result.data().accountId());
            }
        } catch (Exception e) {
            log.warn("AccountQueryService.findIdByUsername failed for {}: {}", username, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void createAdministrator(AdministratorSpec spec) {
        if (accountManagementService == null) {
            throw new IllegalStateException("AccountManagementService unavailable; cannot create administrator");
        }
        String commandId = UUID.randomUUID().toString();
        String idempotencyKey = uuidGenerator.newId();
        ActorDelegation actor = new ActorDelegation("ADMIN", "bootstrap", "bootstrap", "initial admin provisioning");
        CreateAccountCommand command = new CreateAccountCommand(
                commandId,
                IdMetadata.of(idempotencyKey, null),
                actor,
                TraceMetadata.EMPTY,
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

        // Verify the account exists
        AuthAccountDTO current = null;
        if (accountQueryService != null) {
            RpcResult<AuthAccountDTO> check = accountQueryService.getAccountById(id);
            if (check == null || !check.success() || check.data() == null) {
                throw new IllegalStateException("Cannot restore nonexistent administrator: " + id);
            }
            current = check.data();
        }

        String commandId = UUID.randomUUID().toString();
        String idempotencyKey = uuidGenerator.newId();
        ActorDelegation actor = new ActorDelegation("ADMIN", "bootstrap", "bootstrap", "restore admin");
        TraceMetadata trace = TraceMetadata.EMPTY;

        // Update credentials
        UpdateAccountCredentialsCommand credsCmd = new UpdateAccountCredentialsCommand(
                commandId, IdMetadata.of(idempotencyKey, null), actor, trace, id, spec.username(), spec.email());
        accountManagementService.updateCredentials(credsCmd);
        // Reset password (admin doesn't know current password)
        String pwCommandId = UUID.randomUUID().toString();
        String pwIdempotencyKey = uuidGenerator.newId();
        ResetPasswordCommand pwCmd = new ResetPasswordCommand(
                pwCommandId, IdMetadata.of(pwIdempotencyKey, null), actor, trace, id, spec.rawPassword(), "admin restore");
        accountManagementService.resetPassword(pwCmd);

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
        RpcResult<AuthAccountDTO> result = accountQueryService.getAccountById(id);
        if (result == null || !result.success() || result.data() == null) {
            throw new IllegalStateException("Cannot re-read administrator account after state change: " + id);
        }
        return result.data();
    }

    private void changeLifecycleState(String id, long expectedVersion,
            ChangeAccountStateCommand.AccountStateAction action) {
        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                UUID.randomUUID().toString(),
                IdMetadata.of(uuidGenerator.newId(), null),
                new ActorDelegation("ADMIN", "bootstrap", "bootstrap", "restore admin"),
                TraceMetadata.EMPTY,
                id, expectedVersion, action, "admin restore");
        RpcResult<?> result = accountAdministrationService.changeState(command);
        if (result == null || !result.success()) {
            throw new IllegalStateException("Failed to " + action + " administrator " + id
                    + (result != null && result.error() != null ? ": " + result.error().message() : ""));
        }
    }
}
