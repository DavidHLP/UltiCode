package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.SolutionCommentOwnerPort;
import com.ulticode.modules.solution.port.DefaultSolutionCommentOwnerPort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo provider for {@link SolutionCommentOwnerPort} exported by
 * {@code backend-app} so backend-admin flags/unflags/deletes solution
 * comments without importing the solution module (ADMIN-006).
 *
 * <p>Delegates the concrete {@link DefaultSolutionCommentOwnerPort} — never
 * the port interface itself — so the app bean graph keeps exactly one
 * primary local implementation plus the RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SolutionCommentOwnerProvider implements SolutionCommentOwnerPort {

    private final DefaultSolutionCommentOwnerPort delegate;

    @Override
    public FlagResult flagComment(String commentId, String reason) {
        return delegate.flagComment(commentId, reason);
    }

    @Override
    public FlagResult unflagComment(String commentId) {
        return delegate.unflagComment(commentId);
    }

    @Override
    public String resolveAuthorId(String commentId) {
        return delegate.resolveAuthorId(commentId);
    }

    @Override
    public String resolveSolutionId(String commentId) {
        return delegate.resolveSolutionId(commentId);
    }

    @Override
    public DeleteResult deleteComment(String commentId, String deletedBy) {
        return delegate.deleteComment(commentId, deletedBy);
    }
}
