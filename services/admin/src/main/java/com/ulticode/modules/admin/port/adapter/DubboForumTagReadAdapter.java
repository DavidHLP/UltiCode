package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.ForumTagReadPort;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagPage;
import com.ulticode.app.api.service.ForumTagReadPort.ForumTagRow;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * ADMIN-007: Dubbo consumer adapter registering {@link ForumTagReadPort}
 * as a local admin bean, backed by the {@code backend-app} provider
 * ({@code ForumTagReadProvider}).
 *
 * <p>This adapter is the only local bean of that type. Read references use
 * the query RPC policy (800 ms, one retry) per {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboForumTagReadAdapter implements ForumTagReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ForumTagReadPort forumTagReadPort;

    @Override
    public ForumTagPage page(String search, int pageNum, int pageSize, String sortBy, String sortOrder) {
        return forumTagReadPort.page(search, pageNum, pageSize, sortBy, sortOrder);
    }

    @Override
    public ForumTagRow getById(String id) {
        return forumTagReadPort.getById(id);
    }
}
