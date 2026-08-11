package com.ulticode.modules.admin.port.adapter;

import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Admin consumer adapter for Auth-owned account management.
 */
@Primary
@Component
public class DubboAccountManagementAdapter implements AccountManagementService {

    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private AccountManagementService accountManagementService;

    @Override
    public RpcResult<AccountMutationDTO> createAccount(CreateAccountCommand command) {
        return accountManagementService.createAccount(command);
    }

    @Override
    public RpcResult<AccountMutationDTO> updateCredentials(UpdateAccountCredentialsCommand command) {
        return accountManagementService.updateCredentials(command);
    }

    @Override
    public RpcResult<AccountMutationDTO> changePassword(ChangePasswordCommand command) {
        return accountManagementService.changePassword(command);
    }

    @Override
    public RpcResult<AccountMutationDTO> resetPassword(ResetPasswordCommand command) {
        return accountManagementService.resetPassword(command);
    }

    @Override
    public RpcResult<AccountMutationDTO> deleteAccount(DeleteAccountCommand command) {
        return accountManagementService.deleteAccount(command);
    }
}
