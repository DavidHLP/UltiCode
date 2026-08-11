package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.SolutionAdminReadPort;
import com.ulticode.modules.solution.port.DefaultSolutionAdminReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo provider for {@link SolutionAdminReadPort} exported by
 * {@code backend-app} so backend-admin reads solutions without importing the
 * solution module (ADMIN-006).
 *
 * <p>Delegates the concrete {@link DefaultSolutionAdminReadAdapter} — never
 * the port interface itself — so the app bean graph keeps exactly one
 * primary local implementation plus the RPC export.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SolutionAdminReadProvider implements SolutionAdminReadPort {

    private final DefaultSolutionAdminReadAdapter delegate;

    @Override
    public SolutionAdminPage page(SolutionAdminQuery query) {
        return delegate.page(query);
    }

    @Override
    public SolutionAdminRow getById(String id) {
        return delegate.getById(id);
    }
}
