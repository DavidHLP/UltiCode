package com.ulticode.core;

import com.ulticode.admin.security.DelegationAssertionSigner;
import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.auth.api.service.AuthorizationMutationService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.security.LocalDelegationAssertionContext;
import org.springframework.stereotype.Component;

/** Core local Adapter for the Auth mutation Seam; no Mapper or Entity leakage. */
@Component
public final class CoreLocalAuthorizationMutationAdapter implements AuthorizationMutationService {

    private final CoreOwnerContextManager ownerContexts;

    public CoreLocalAuthorizationMutationAdapter(CoreOwnerContextManager ownerContexts) {
        this.ownerContexts = ownerContexts;
    }

    @Override
    public RpcResult<AuthorizationMutationDTO> mutatePermission(PermissionMutationCommand command) {
        String traceId = command == null || command.trace() == null
                ? null : command.trace().traceId();
        if (command == null) {
            return RpcResult.failure(BaseErrorCode.BAD_REQUEST, traceId);
        }
        String assertion;
        try {
            assertion = ownerContexts.bean("admin", DelegationAssertionSigner.class)
                    .issueForTarget("backend-auth");
        } catch (RuntimeException ignored) {
            assertion = null;
        }
        if (assertion == null) {
            return RpcResult.failure(BaseErrorCode.UNAUTHORIZED, traceId);
        }
        try (LocalDelegationAssertionContext.Scope ignored =
                     LocalDelegationAssertionContext.install(assertion)) {
            return ownerContexts.bean("auth", AuthorizationMutationService.class)
                    .mutatePermission(command);
        }
    }
}
