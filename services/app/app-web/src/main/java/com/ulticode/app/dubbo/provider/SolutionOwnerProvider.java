package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.SolutionOwnerPort;
import com.ulticode.modules.solution.port.DefaultSolutionOwnerPort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Dubbo provider for {@link SolutionOwnerPort} exported by {@code backend-app}
 * so backend-admin flags/unflags/deletes/publishes solutions without
 * importing the solution module (ADMIN-006).
 *
 * <p>Delegates the concrete {@link DefaultSolutionOwnerPort} — never the
 * port interface itself — so the app bean graph keeps exactly one primary
 * local implementation plus the RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SolutionOwnerProvider implements SolutionOwnerPort {

    private final DefaultSolutionOwnerPort delegate;

    @Override
    public FlagResult flagSolution(String id, String reason, LocalDateTime flaggedAt) {
        return delegate.flagSolution(id, reason, flaggedAt);
    }

    @Override
    public FlagResult unflagSolution(String id) {
        return delegate.unflagSolution(id);
    }

    @Override
    public DeleteResult deleteSolution(String id) {
        return delegate.deleteSolution(id);
    }

    @Override
    public void setPublished(String id, boolean published, LocalDateTime publishedAt) {
        delegate.setPublished(id, published, publishedAt);
    }

    @Override
    public Set<String> findExistingIds(List<String> ids) {
        return delegate.findExistingIds(ids);
    }

    @Override
    public String resolveAuthorId(String id) {
        return delegate.resolveAuthorId(id);
    }

    @Override
    public void updateVoteCounts(String id, int likes, int dislikes) {
        delegate.updateVoteCounts(id, likes, dislikes);
    }
}
