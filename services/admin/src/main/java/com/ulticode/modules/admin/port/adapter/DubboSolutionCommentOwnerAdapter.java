package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dubbo consumer adapter registering {@link SolutionCommentOwnerPort} as a
 * local admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.SolutionCommentOwnerProvider}).
 *
 * <p>Admin comment moderation keeps depending on the entity-free port
 * contract (ADMIN-006); this adapter is the only local bean of that type.
 * Write references use the write RPC policy (3 s, no auto-retry — the
 * provider enforces idempotent per-id semantics) per {@link RpcPolicy};
 * the admin caller never wraps the remote write in a local transaction.
 */
@Primary
@Component
public class DubboSolutionCommentOwnerAdapter implements SolutionCommentOwnerPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private SolutionCommentOwnerPort solutionCommentOwnerPort;

    @Override
    public FlagResult flagComment(String commentId, String reason) {
        return solutionCommentOwnerPort.flagComment(commentId, reason);
    }

    @Override
    public FlagResult unflagComment(String commentId) {
        return solutionCommentOwnerPort.unflagComment(commentId);
    }

    @Override
    public String resolveAuthorId(String commentId) {
        return solutionCommentOwnerPort.resolveAuthorId(commentId);
    }

    @Override
    public String resolveSolutionId(String commentId) {
        return solutionCommentOwnerPort.resolveSolutionId(commentId);
    }

    @Override
    public DeleteResult deleteComment(String commentId, String deletedBy) {
        return solutionCommentOwnerPort.deleteComment(commentId, deletedBy);
    }
}
