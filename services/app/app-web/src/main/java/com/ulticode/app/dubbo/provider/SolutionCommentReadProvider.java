package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.SolutionCommentReadPort;
import com.ulticode.modules.solution.port.DefaultSolutionCommentReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo provider for {@link SolutionCommentReadPort} exported by
 * {@code backend-app} so backend-admin moderates solution comments without
 * importing the solution module (ADMIN-006).
 *
 * <p>Delegates the concrete {@link DefaultSolutionCommentReadAdapter} —
 * never the port interface itself — so the app bean graph keeps exactly one
 * primary local implementation plus the RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SolutionCommentReadProvider implements SolutionCommentReadPort {

    private final DefaultSolutionCommentReadAdapter delegate;

    @Override
    public SolutionCommentPage page(Boolean isFlagged, Boolean isDeleted, String search,
                                    String solutionId, String sortBy, String sortOrder,
                                    int page, int limit) {
        return delegate.page(isFlagged, isDeleted, search, solutionId, sortBy, sortOrder, page, limit);
    }

    @Override
    public SolutionCommentRow getById(String commentId) {
        return delegate.getById(commentId);
    }
}
