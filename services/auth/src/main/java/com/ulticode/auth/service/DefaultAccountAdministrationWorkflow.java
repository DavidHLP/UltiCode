package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.common.rpc.RpcResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Default Auth-owned implementation of {@link AccountAdministrationWorkflow}. */
@Service
public class DefaultAccountAdministrationWorkflow implements AccountAdministrationWorkflow {

    private final AuthAccountPort authAccountPort;
    public DefaultAccountAdministrationWorkflow(AuthAccountPort authAccountPort) {
        this.authAccountPort = authAccountPort;
    }

    @Override
    @Transactional
    public RpcResult<AccountStateDTO> changeState(ChangeAccountStateCommand command) {
        String traceId = traceId(command);
        Optional<AuthAccountRecord> accountOpt = authAccountPort.findById(command.accountId());
        if (accountOpt.isEmpty()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, traceId);
        }
        AuthAccountRecord currentAccount = accountOpt.get();

        if (currentAccount.authzVersion() != command.expectedVersion()) {
            return RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, traceId);
        }

        boolean targetActive = Boolean.TRUE.equals(currentAccount.isActive());
        boolean targetBanned = Boolean.TRUE.equals(currentAccount.isBanned());
        switch (command.action()) {
            case DISABLE -> targetActive = false;
            case ENABLE -> targetActive = true;
            case BAN -> targetBanned = true;
            case UNBAN -> targetBanned = false;
        }

        boolean updated = authAccountPort.updateAccountIfVersion(
                command.accountId(),
                targetActive,
                targetBanned,
                currentAccount.role(),
                command.expectedVersion());
        if (!updated) {
            return RpcResult.failure(AuthErrorCode.AUTHORIZATION_VERSION_CONFLICT, traceId);
        }

        return RpcResult.success(
                new AccountStateDTO(
                        command.accountId(),
                        targetActive,
                        targetBanned,
                        command.expectedVersion() + 1),
                traceId);
    }


    private static String traceId(com.ulticode.auth.api.command.WriteCommand command) {
        if (command == null || command.trace() == null
                || command.trace().traceId() == null
                || command.trace().traceId().isBlank()) {
            return "t-system";
        }
        return command.trace().traceId();
    }
}
