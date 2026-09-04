package com.ulticode.auth.authorization;

import com.ulticode.auth.api.command.ChangeRoleCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.common.rpc.RpcResult;

/** Auth-owned business seam for role-only changes. */
public interface RoleMutationWorkflow {

    RpcResult<AccountMutationDTO> changeRole(ChangeRoleCommand command);
}
