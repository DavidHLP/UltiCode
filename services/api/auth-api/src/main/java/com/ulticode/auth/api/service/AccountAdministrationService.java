package com.ulticode.auth.api.service;

import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * Auth-owned administrative write provider.
 *
 * <p>Listed in {@code PROJECT_DOCUMENTATION.md} &sect;4.1
 * as one of {@code backend-auth}'s three Dubbo providers; per &sect;6.2
 * the interface signature mirrors the migration guide example
 * exactly. Both methods are mutations that go through the auth
 * provider's local transaction; Provider must never synchronously
 * chain another RPC to complete the command (see &sect;6.5).
 *
 * <p>This interface is contract-only; no ServiceImpl lives in this
 * module. The provider implementation belongs to {@code backend-auth}.
 */
public interface AccountAdministrationService {

    /**
     * Change an account's lifecycle state (active / banned / disabled).
     *
     * @param command carries commandId, idempotency key, actor
     *                delegation, trace metadata and the lifecycle
     *                action with optimistic-lock expected version
     * @return success with the new {@link AccountStateDTO}; failure
     *         with {@code AUTHORIZATION_VERSION_CONFLICT} on stale
     *         expected version or {@code ACCOUNT_NOT_FOUND} when the
     *         target id is unknown
     */
    RpcResult<AccountStateDTO> changeState(ChangeAccountStateCommand command);

    /**
     * Change an account's authoritative role / permission assignment.
     *
     * @param command carries commandId, idempotency key, actor
     *                delegation, trace metadata, the new role +
     *                permissions set and the optimistic-lock expected
     *                version
     * @return success with the post-write
     *         {@link AuthorizationSnapshotDTO}; failure codes as
     *         documented on {@link #changeState}
     */
    RpcResult<AuthorizationSnapshotDTO> changeAuthorization(
            ChangeAuthorizationCommand command);
}