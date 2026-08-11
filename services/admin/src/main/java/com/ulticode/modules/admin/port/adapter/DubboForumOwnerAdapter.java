package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.command.ForumPostModerationCommand;
import com.ulticode.app.api.dto.ForumPostModerationResultDTO;
import com.ulticode.app.api.service.ForumPostAdministrationService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Admin-side consumer for the App-owned forum-post command boundary.
 */
@Primary
@Component
public class DubboForumOwnerAdapter implements ForumPostAdministrationService {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ForumPostAdministrationService delegate;

    @Override
    public RpcResult<ForumPostModerationResultDTO> moderate(ForumPostModerationCommand command) {
        return delegate.moderate(command);
    }
}
