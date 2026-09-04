package com.ulticode.auth.authorization;

import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.dto.AuthorizationMutationDTO;
import com.ulticode.common.rpc.RpcResult;

/** Auth-owned business seam behind the permission mutation provider. */
public interface AuthorizationMutationWorkflow {

    RpcResult<AuthorizationMutationDTO> mutatePermission(PermissionMutationCommand command);
}
