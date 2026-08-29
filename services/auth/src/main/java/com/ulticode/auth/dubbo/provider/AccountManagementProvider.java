package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.command.WriteCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.idempotency.CommandReceiptExecutor;
import com.ulticode.auth.security.ProviderActorTrustGate;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.rpc.RpcResult;
import java.util.Set;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dubbo transport adapter for Auth-owned account-management mutations. */
@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class AccountManagementProvider implements AccountManagementService {

    private static final Logger log = LoggerFactory.getLogger(AccountManagementProvider.class);
    private static final String SERVICE_NAME = "AccountManagementService";
    /** Operations the one-shot BOOTSTRAP actor may invoke (provisioning only). */
    private static final Set<String> BOOTSTRAP_OPERATIONS =
            Set.of("createAccount", "updateCredentials", "resetPassword");

    private final AccountManagementEngine engine;
    private final CommandReceiptExecutor receiptExecutor;
    private final ProviderActorTrustGate trustGate;

    public AccountManagementProvider(
            AccountManagementEngine engine,
            CommandReceiptExecutor receiptExecutor,
            ProviderActorTrustGate trustGate) {
        this.engine = engine;
        this.receiptExecutor = receiptExecutor;
        this.trustGate = trustGate;
    }

    @Override
    public RpcResult<AccountMutationDTO> createAccount(CreateAccountCommand command) {
        return execute(
                "createAccount",
                command,
                AccountMutationDTO.class,
                traceId -> engine.create(command, traceId));
    }

    @Override
    public RpcResult<AccountMutationDTO> updateCredentials(
            UpdateAccountCredentialsCommand command) {
        return execute(
                "updateCredentials",
                command,
                AccountMutationDTO.class,
                traceId -> engine.updateCredentials(command, traceId));
    }

    @Override
    public RpcResult<AccountMutationDTO> changePassword(ChangePasswordCommand command) {
        return execute(
                "changePassword",
                command,
                AccountMutationDTO.class,
                traceId -> engine.changePassword(command, traceId));
    }

    @Override
    public RpcResult<AccountMutationDTO> resetPassword(ResetPasswordCommand command) {
        return execute(
                "resetPassword",
                command,
                AccountMutationDTO.class,
                traceId -> engine.resetPassword(command, traceId));
    }

    @Override
    public RpcResult<AccountMutationDTO> deleteAccount(DeleteAccountCommand command) {
        return execute(
                "deleteAccount",
                command,
                AccountMutationDTO.class,
                traceId -> engine.deleteAccount(command, traceId));
    }

    private <T> RpcResult<T> execute(
            String operation,
            WriteCommand command,
            Class<T> resultType,
            java.util.function.Function<String, RpcResult<T>> mutation) {
        String traceId = CommandReceiptExecutor.traceId(command);
        if (!isAuthorizedForOperation(operation, command)) {
            return RpcResult.failure(BaseErrorCode.FORBIDDEN, traceId);
        }
        try {
            return receiptExecutor.execute(
                    SERVICE_NAME, operation, command, resultType, mutation);
        } catch (RuntimeException e) {
            log.error("Account management operation failed: {}", operation, e);
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
    }

    /**
     * Authorization per operation:
     * <ul>
     *   <li>{@code changePassword} is self-service only — the actor must be
     *       the target account itself. A USER actor is the in-process
     *       {@code /auth/me/password} path (the engine verifies the current
     *       password); an ADMIN/SUPER_ADMIN self-change arrives over Dubbo
     *       and must carry a verified delegation assertion.</li>
     *   <li>Every other operation requires a trusted ADMIN/SUPER_ADMIN actor
     *       assertion, with the BOOTSTRAP actor restricted to
     *       {@link #BOOTSTRAP_OPERATIONS}.</li>
     * </ul>
     */
    private boolean isAuthorizedForOperation(String operation, WriteCommand command) {
        if ("changePassword".equals(operation)) {
            return isAuthorizedSelfServiceChange(command);
        }
        return trustGate.isTrustedForOperation(command, operation, BOOTSTRAP_OPERATIONS);
    }

    private boolean isAuthorizedSelfServiceChange(WriteCommand command) {
        if (!(command instanceof ChangePasswordCommand self)) {
            return false;
        }
        ActorDelegation actor = self.actor();
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()
                || !actor.actorId().equals(actor.delegatorId())
                || !actor.actorId().equals(self.accountId())) {
            return false;
        }
        String actorType = actor.actorType();
        if ("USER".equalsIgnoreCase(actorType)) {
            // In-process end-user self change; the engine verifies the
            // current password before any durable mutation.
            return true;
        }
        if ("ADMIN".equalsIgnoreCase(actorType) || "SUPER_ADMIN".equalsIgnoreCase(actorType)) {
            // Admin self change crosses the Dubbo boundary, so the signed
            // delegation assertion must verify.
            return trustGate.isTrusted(actor);
        }
        return false;
    }
}
