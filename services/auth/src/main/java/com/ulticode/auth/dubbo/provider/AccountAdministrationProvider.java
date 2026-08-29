package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.idempotency.CommandReceiptExecutor;
import com.ulticode.auth.security.ProviderActorTrustGate;
import com.ulticode.auth.service.AccountAdministrationWorkflow;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.rpc.RpcResult;
import java.util.Set;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dubbo transport adapter for the Auth-owned administration workflow. */
@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class AccountAdministrationProvider implements AccountAdministrationService {

    private static final Logger log = LoggerFactory.getLogger(AccountAdministrationProvider.class);
    private static final String SERVICE_NAME = "AccountAdministrationService";
    /** Operations the one-shot BOOTSTRAP actor may invoke (restore state only). */
    private static final Set<String> BOOTSTRAP_OPERATIONS = Set.of("changeState");

    private final AccountAdministrationWorkflow workflow;
    private final ProviderActorTrustGate trustGate;
    private final CommandReceiptExecutor receiptExecutor;

    public AccountAdministrationProvider(
            AccountAdministrationWorkflow workflow,
            CommandReceiptExecutor receiptExecutor,
            ProviderActorTrustGate trustGate) {
        this.workflow = workflow;
        this.receiptExecutor = receiptExecutor;
        this.trustGate = trustGate;
    }

    @Override
    public RpcResult<AccountStateDTO> changeState(ChangeAccountStateCommand command) {
        return execute(
                "changeState",
                command,
                AccountStateDTO.class,
                traceId -> workflow.changeState(command));
    }

    @Override
    public RpcResult<AuthorizationSnapshotDTO> changeAuthorization(
            ChangeAuthorizationCommand command) {
        return execute(
                "changeAuthorization",
                command,
                AuthorizationSnapshotDTO.class,
                traceId -> workflow.changeAuthorization(command));
    }

    private <T> RpcResult<T> execute(
            String operation,
            com.ulticode.auth.api.command.WriteCommand command,
            Class<T> resultType,
            java.util.function.Function<String, RpcResult<T>> mutation) {
        String traceId = CommandReceiptExecutor.traceId(command);
        if (!trustGate.isTrustedForOperation(command, operation, BOOTSTRAP_OPERATIONS)) {
            return RpcResult.failure(BaseErrorCode.FORBIDDEN, traceId);
        }
        try {
            return receiptExecutor.execute(
                    SERVICE_NAME, operation, command, resultType, mutation);
        } catch (RuntimeException e) {
            log.error("Account administration operation failed: {}", operation, e);
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
    }
}
