package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.ForumTagMutationCommand;
import com.ulticode.app.api.dto.ForumTagDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ForumTagAdministrationService;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.forum.port.DefaultForumTagAdministrationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * ADMIN-007: Dubbo provider for {@link ForumTagAdministrationService}.
 *
 * <p>The provider verifies the delegated admin actor and claims the command
 * idempotency key before the App-owned tag mutation runs.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ForumTagAdministrationProvider implements ForumTagAdministrationService {

    private static final String SERVICE = "ForumTagAdministrationService";
    private final DefaultForumTagAdministrationAdapter delegate;
    private final CommandReceiptExecutor receiptExecutor;
    private final AdminActorAuthorizer actorAuthorizer;

    @Override
    public RpcResult<ForumTagDTO> mutate(ForumTagMutationCommand command) {
        RpcResult<ForumTagDTO> rejected = rejectIfNotAdmin(command);
        if (rejected != null) {
            return rejected;
        }
        log.info("ForumTagAdministrationProvider.mutate action={} tagId={} commandId={}",
                command.action(), command.tagId(), command.commandId());
        try {
            return receiptExecutor.execute(
                    SERVICE,
                    command.action().name(),
                    command,
                    ForumTagDTO.class,
                    traceId -> delegate.mutate(command));
        } catch (Exception e) {
            log.error("ForumTagAdministrationProvider.mutate failed tagId={} action={}",
                    command.tagId(), command.action(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE,
                    traceId(command));
        }
    }

    private <T> RpcResult<T> rejectIfNotAdmin(ForumTagMutationCommand command) {
        if (command == null) {
            return RpcResult.failure(AppErrorCode.BAD_REQUEST, null);
        }
        ActorDelegation actor = command.actor();
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()
                || actor.delegatorId() == null || actor.delegatorId().isBlank()
                || !actor.actorId().equals(actor.delegatorId())
                || (!"ADMIN".equalsIgnoreCase(actor.actorType())
                && !"SUPER_ADMIN".equalsIgnoreCase(actor.actorType()))) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId(command));
        }
        if (!actorAuthorizer.isAuthorized(actor)) {
            return RpcResult.failure(AppErrorCode.FORBIDDEN, traceId(command));
        }
        return null;
    }

    private static String traceId(ForumTagMutationCommand command) {
        return command == null || command.trace() == null ? null : command.trace().traceId();
    }
}
