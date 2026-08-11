package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.ForumCommentReadPort;
import com.ulticode.app.api.service.ForumCommentReadPort.ForumCommentPage;
import com.ulticode.app.api.service.ForumCommentReadPort.ForumCommentRow;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * ADMIN-007: Dubbo consumer adapter registering {@link ForumCommentReadPort}
 * as a local admin bean, backed by the {@code backend-app} provider
 * ({@code ForumCommentReadProvider}).
 *
 * <p>This adapter is the only local bean of that type. Read references use
 * the query RPC policy (800 ms, one retry) per {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboForumCommentReadAdapter implements ForumCommentReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ForumCommentReadPort forumCommentReadPort;

    @Override
    public ForumCommentPage page(Boolean isFlagged, Boolean isDeleted, String search, String postId,
                                 String sortBy, String sortOrder, int page, int limit) {
        return forumCommentReadPort.page(isFlagged, isDeleted, search, postId, sortBy, sortOrder, page, limit);
    }

    @Override
    public ForumCommentRow getById(String commentId) {
        return forumCommentReadPort.getById(commentId);
    }
}
