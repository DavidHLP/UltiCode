package com.ulticode.auth.api.service;

import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * Auth-owned account lifecycle and credential-management RPC contract.
 *
 * <p>The provider owns account credentials and soft-delete state. Consumers
 * must not reach the auth {@code users} table or import its persistence types.
 */
public interface AccountManagementService {

    RpcResult<AccountMutationDTO> createAccount(CreateAccountCommand command);

    RpcResult<AccountMutationDTO> updateCredentials(
            UpdateAccountCredentialsCommand command);

    RpcResult<AccountMutationDTO> changePassword(ChangePasswordCommand command);

    RpcResult<AccountMutationDTO> resetPassword(ResetPasswordCommand command);

    RpcResult<AccountMutationDTO> deleteAccount(DeleteAccountCommand command);
}
