package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.SolutionIndexDTO;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.modules.solution.port.DefaultSolutionReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dubbo provider for {@link SolutionReadPort} exported by {@code backend-app}
 * so backend-admin reads solution counts/titles without importing the
 * solution module (ADMIN-006).
 *
 * <p>Delegates the concrete {@link DefaultSolutionReadAdapter} — never the
 * port interface itself — so the app bean graph keeps exactly one primary
 * local implementation plus the RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SolutionReadProvider implements SolutionReadPort {

    private final DefaultSolutionReadAdapter delegate;

    @Override
    public List<SolutionIndexDTO> searchForIndex(String query, int limit) {
        return delegate.searchForIndex(query, limit);
    }

    @Override
    public long countByProblemId(Long problemId) {
        return delegate.countByProblemId(problemId);
    }

    @Override
    public long countByUserId(String userId) {
        return delegate.countByUserId(userId);
    }

    @Override
    public Map<String, String> findTitlesByIds(Set<String> solutionIds) {
        return delegate.findTitlesByIds(solutionIds);
    }
}
