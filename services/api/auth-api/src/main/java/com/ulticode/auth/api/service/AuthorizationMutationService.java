package com.ulticode.auth.api.service;

import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.common.rpc.RpcResult;

/** Auth-owned cross-process seam for one direct permission delta. */
public interface AuthorizationMutationService {

    /**
     * Applies one direct grant or revoke atomically in Auth.
     *
     * <p>The provider validates the delegated actor, deduplicates by
     * idempotency key, checks {@code expectedVersion}, mutates only
     * {@code user_permissions}, records the audit/outbox event, and returns
     * the new authorization version. Role-derived permissions are never
     * accepted as a replacement set and are never deleted by revoke.</p>
     */
    RpcResult<AuthorizationMutationDTO> mutatePermission(PermissionMutationCommand command);
}
