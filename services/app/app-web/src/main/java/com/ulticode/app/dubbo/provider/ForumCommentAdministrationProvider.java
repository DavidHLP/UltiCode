package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ForumCommentModerationCommand;
import com.ulticode.app.api.dto.ForumCommentModerationResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ForumCommentAdministrationService;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.forum.port.DefaultForumCommentAdministrationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import static com.ulticode.app.dubbo.provider.AdminCommandValidation.rejectIfNotAdmin;
import static com.ulticode.app.dubbo.provider.AdminCommandValidation.traceId;

/**
 * ADMIN-007: Dubbo provider for {@link ForumCommentAdministrationService}.
 *
 * <p>The provider verifies the delegated admin actor and claims the command
 * idempotency key before the App-owned comment mutation runs.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ForumCommentAdministrationProvider implements ForumCommentAdministrationService {

    private static final String SERVICE = "ForumCommentAdministrationService";
    private final DefaultForumCommentAdministrationAdapter delegate;
    private final CommandReceiptExecutor receiptExecutor;
    private final AdminActorAuthorizer actorAuthorizer;

    @Override
    public RpcResult<ForumCommentModerationResultDTO> moderate(ForumCommentModerationCommand command) {
        RpcResult<ForumCommentModerationResultDTO> rejected = rejectIfNotAdmin(command, actorAuthorizer);
        if (rejected != null) {
            return rejected;
        }
        log.info("ForumCommentAdministrationProvider.moderate commentId={} action={} commandId={}",
                command.commentId(), command.action(), command.commandId());
        try {
            return receiptExecutor.execute(
                    SERVICE,
                    command.action().name(),
                    command,
                    ForumCommentModerationResultDTO.class,
                    traceId -> delegate.moderate(command));
        } catch (Exception e) {
            log.error("ForumCommentAdministrationProvider.moderate failed commentId={} action={}",
                    command.commentId(), command.action(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE,
                    traceId(command));
        }
    }
}
