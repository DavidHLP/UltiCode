package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.SolutionCommentReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dubbo consumer adapter registering {@link SolutionCommentReadPort} as a
 * local admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.SolutionCommentReadProvider}).
 *
 * <p>Admin comment moderation keeps depending on the entity-free port
 * contract (ADMIN-006); this adapter is the only local bean of that type.
 * Read references use the query RPC policy (800 ms, one retry) per
 * {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboSolutionCommentReadAdapter implements SolutionCommentReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SolutionCommentReadPort solutionCommentReadPort;

    @Override
    public SolutionCommentPage page(Boolean isFlagged, Boolean isDeleted, String search,
                                    String solutionId, String sortBy, String sortOrder,
                                    int page, int limit) {
        return solutionCommentReadPort.page(isFlagged, isDeleted, search, solutionId,
                sortBy, sortOrder, page, limit);
    }

    @Override
    public SolutionCommentRow getById(String commentId) {
        return solutionCommentReadPort.getById(commentId);
    }
}
