package com.ulticode.auth.dubbo.provider;

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
import com.ulticode.auth.security.InternalDelegationAssertionVerifier;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/** Dubbo transport adapter for Auth-owned account-management mutations. */
@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class AccountManagementProvider implements AccountManagementService {

    private static final Logger log = LoggerFactory.getLogger(AccountManagementProvider.class);
    private static final String SERVICE_NAME = "AccountManagementService";

    private final AccountManagementEngine engine;
    private final CommandReceiptExecutor receiptExecutor;
    private final InternalDelegationAssertionVerifier delegationVerifier;

    public AccountManagementProvider(
            AccountManagementEngine engine,
            CommandReceiptExecutor receiptExecutor,
            InternalDelegationAssertionVerifier delegationVerifier) {
        this.engine = engine;
        this.receiptExecutor = receiptExecutor;
        this.delegationVerifier = delegationVerifier;
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
            Function<String, RpcResult<T>> mutation) {
        String traceId = CommandReceiptExecutor.traceId(command);
        if (!isAllowedBootstrapOperation(command, operation)) {
            return RpcResult.failure(BaseErrorCode.FORBIDDEN, traceId);
        }
        if (!trustedActor(command == null ? null : command.actor())) {
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
    private static boolean isAllowedBootstrapOperation(WriteCommand command, String operation) {
        if (command == null || command.actor() == null
                || !"BOOTSTRAP".equalsIgnoreCase(command.actor().actorType())) {
            return true;
        }
        return "bootstrap".equals(command.actor().actorId())
                && "bootstrap".equals(command.actor().delegatorId())
                && switch (operation) {
                    case "createAccount", "updateCredentials", "resetPassword" -> true;
                    default -> false;
                };
    }

    private boolean trustedActor(com.ulticode.auth.api.command.ActorDelegation actor) {
        try {
            return delegationVerifier.isTrusted(actor);
        } catch (RuntimeException exception) {
            log.warn("Account management actor authorization failed", exception);
            return false;
        }
    }
}
