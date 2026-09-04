package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.api.command.ChangeRoleCommand;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.RoleMutationService;
import com.ulticode.auth.authorization.RoleMutationWorkflow;
import com.ulticode.auth.idempotency.CommandReceiptExecutor;
import com.ulticode.auth.security.ProviderActorTrustGate;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Transport Adapter for the Auth-owned role mutation Module. */
@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class RoleMutationProvider implements RoleMutationService {

    private static final Logger log = LoggerFactory.getLogger(RoleMutationProvider.class);
    private static final String SERVICE_NAME = "RoleMutationService";

    private final RoleMutationWorkflow workflow;
    private final ProviderActorTrustGate trustGate;
    private final CommandReceiptExecutor receiptExecutor;

    public RoleMutationProvider(
            RoleMutationWorkflow workflow,
            ProviderActorTrustGate trustGate,
            CommandReceiptExecutor receiptExecutor) {
        this.workflow = workflow;
        this.trustGate = trustGate;
        this.receiptExecutor = receiptExecutor;
    }

    @Override
    public RpcResult<AccountMutationDTO> changeRole(ChangeRoleCommand command) {
        String traceId = CommandReceiptExecutor.traceId(command);
        if (!trustGate.isTrustedForOperation(command, "changeRole", Set.of())) {
            return RpcResult.failure(BaseErrorCode.FORBIDDEN, traceId);
        }
        try {
            return receiptExecutor.execute(
                    SERVICE_NAME,
                    "changeRole",
                    command,
                    AccountMutationDTO.class,
                    ignored -> workflow.changeRole(command));
        } catch (AuthBusinessException exception) {
            log.warn("Authorization role mutation rejected: {}", exception.getMessage());
            return RpcResult.failure(exception.getErrorCode(), traceId);
        } catch (RuntimeException exception) {
            log.error("Authorization role mutation failed", exception);
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
    }
}
