package com.ulticode.auth.service;

import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * Auth-owned account administration business seam.
 *
 * <p>The Dubbo provider supplies the transport/idempotency boundary. This
 * interface owns the state CAS, role update, and direct-permission
 * reconciliation behavior behind a small local contract.</p>
 */
public interface AccountAdministrationWorkflow {

    RpcResult<AccountStateDTO> changeState(ChangeAccountStateCommand command);

    RpcResult<AuthorizationSnapshotDTO> changeAuthorization(ChangeAuthorizationCommand command);
}
