package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.app.api.service.AppReconciliationReadPort;
import com.ulticode.modules.reconciliation.port.DefaultAppReconciliationReadPort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo provider for App-owned reconciliation facts.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class AppReconciliationReadProvider implements AppReconciliationReadPort {

    private final DefaultAppReconciliationReadPort delegate;

    @Override
    public long countUserProfiles() {
        return delegate.countUserProfiles();
    }

    @Override
    public ReconciliationOrphanCounts countOrphans() {
        return delegate.countOrphans();
    }
}
