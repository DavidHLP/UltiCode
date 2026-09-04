package com.ulticode.auth.api.service;

import com.ulticode.auth.api.command.ChangeRoleCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.common.rpc.RpcResult;

/** Auth-owned cross-process seam for role-only authorization changes. */
public interface RoleMutationService {

    /** Changes only the account role with CAS, audit, and idempotency. */
    RpcResult<AccountMutationDTO> changeRole(ChangeRoleCommand command);
}
