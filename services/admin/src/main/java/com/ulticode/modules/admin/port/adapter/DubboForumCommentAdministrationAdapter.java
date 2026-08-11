package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.command.ForumCommentModerationCommand;
import com.ulticode.app.api.dto.ForumCommentModerationResultDTO;
import com.ulticode.app.api.service.ForumCommentAdministrationService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * ADMIN-007: Dubbo consumer adapter registering
 * {@link ForumCommentAdministrationService} as a local admin bean, backed
 * by the {@code backend-app} provider
 * ({@code ForumCommentAdministrationProvider}).
 *
 * <p>This adapter is the only local bean of that type. Mutating RPCs use
 * the write policy defaults (3 s timeout, no auto-retry) per
 * {@link RpcPolicy} — {@code RpcResult} failures are mapped onto
 * {@code AdminErrorCode} by the caller
 * ({@code ForumCommentModerator}), mirroring
 * {@code ContentModerationCutoverService}.
 */
@Primary
@Component
public class DubboForumCommentAdministrationAdapter implements ForumCommentAdministrationService {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ForumCommentAdministrationService forumCommentAdministrationService;

    @Override
    public RpcResult<ForumCommentModerationResultDTO> moderate(ForumCommentModerationCommand command) {
        return forumCommentAdministrationService.moderate(command);
    }
}
