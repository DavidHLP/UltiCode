package com.ulticode.modules.moderation.port.adapter;

import com.ulticode.app.api.dto.ModerationUserInfo;
import com.ulticode.modules.moderation.port.ModerationAccountPort;
import com.ulticode.app.user.port.UserFactView;
import com.ulticode.app.user.port.UserFactsProjection;
import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/** Auth-owner adapter for moderation identity reads and ban-state commands. */
@Component
public class ModerationAccountAdapter implements ModerationAccountPort {

    private final UserFactsProjection userFactsProjection;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private AccountAdministrationService accountAdministrationService;

    public ModerationAccountAdapter(UserFactsProjection userFactsProjection) {
        this.userFactsProjection = userFactsProjection;
    }

    void setAccountQueryService(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    void setAccountAdministrationService(AccountAdministrationService accountAdministrationService) {
        this.accountAdministrationService = accountAdministrationService;
    }

    @Override
    public Optional<ModerationUserInfo> findById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        UserFactView user = userFactsProjection.findById(userId);
        return user != null
                ? Optional.of(new ModerationUserInfo(user.id(), user.username()))
                : Optional.empty();
    }

    @Override
    public void updateBanStatus(
            String userId,
            boolean isBanned,
            String bannedReason,
            String actorId,
            String actionId) {
        if (userId == null || userId.isBlank() || actorId == null || actorId.isBlank()
                || actionId == null || actionId.isBlank()) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Ban command metadata is required");
        }
        if (accountQueryService == null || accountAdministrationService == null) {
            throw unavailable();
        }
        RpcResult<AuthAccountDTO> current;
        try {
            current = accountQueryService.getAccountById(userId);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
        if (current == null || !current.success()) {
            if (current != null && current.error() != null
                    && AuthErrorCode.NAMESPACE.equals(current.error().namespace())
                    && current.error().code() == AuthErrorCode.ACCOUNT_NOT_FOUND.code()) {
                throw new BusinessException(BaseErrorCode.NOT_FOUND, "Account not found");
            }
            throw unavailable();
        }
        if (current.data() == null) {
            throw unavailable();
        }
        String commandId = UUID.nameUUIDFromBytes(actionId.getBytes(StandardCharsets.UTF_8)).toString();
        String traceId = TraceIdUtil.current();
        if (traceId == null || traceId.isBlank()) {
            traceId = "t-" + UUID.randomUUID();
        }
        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                commandId,
                IdMetadata.of(actionId, null),
                new ActorDelegation("MODERATOR", actorId, actorId, bannedReason),
                new TraceMetadata(traceId, null, null, null),
                userId,
                current.data().authzVersion(),
                isBanned
                        ? ChangeAccountStateCommand.AccountStateAction.BAN
                        : ChangeAccountStateCommand.AccountStateAction.UNBAN,
                bannedReason);
        RpcResult<AccountStateDTO> result = accountAdministrationService.changeState(command);
        if (result == null || !result.success()) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Auth account owner unavailable");
    }
}
