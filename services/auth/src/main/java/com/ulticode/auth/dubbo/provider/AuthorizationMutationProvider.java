package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AuthorizationMutationService;
import com.ulticode.auth.authorization.AuthorizationMutationWorkflow;
import com.ulticode.auth.idempotency.CommandReceiptExecutor;
import com.ulticode.auth.security.ProviderActorTrustGate;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Transport Adapter for the Auth-owned permission mutation Module. */
@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class AuthorizationMutationProvider implements AuthorizationMutationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationMutationProvider.class);
    private static final String SERVICE_NAME = "AuthorizationMutationService";

    private final AuthorizationMutationWorkflow workflow;
    private final ProviderActorTrustGate trustGate;
    private final CommandReceiptExecutor receiptExecutor;

    public AuthorizationMutationProvider(
            AuthorizationMutationWorkflow workflow,
            ProviderActorTrustGate trustGate,
            CommandReceiptExecutor receiptExecutor) {
        this.workflow = workflow;
        this.trustGate = trustGate;
        this.receiptExecutor = receiptExecutor;
    }

    @Override
    public RpcResult<AuthorizationMutationDTO> mutatePermission(
            PermissionMutationCommand command) {
        String traceId = CommandReceiptExecutor.traceId(command);
        if (!trustGate.isTrustedForOperation(command, "mutatePermission", Set.of())) {
            return RpcResult.failure(BaseErrorCode.FORBIDDEN, traceId);
        }
        try {
            return receiptExecutor.execute(
                    SERVICE_NAME,
                    "mutatePermission",
                    command,
                    AuthorizationMutationDTO.class,
                    ignored -> workflow.mutatePermission(command));
        } catch (AuthBusinessException exception) {
            log.warn("Authorization permission mutation rejected: {}", exception.getMessage());
            return RpcResult.failure(exception.getErrorCode(), traceId);
        } catch (RuntimeException exception) {
            log.error("Authorization permission mutation failed", exception);
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
    }
}
