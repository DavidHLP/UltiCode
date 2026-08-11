package com.ulticode.modules.reconciliation.port.adapter;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.app.api.service.AppReconciliationReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * Admin consumer adapter for App-owned reconciliation facts.
 */
@Component
public class DubboAppReconciliationReadAdapter implements AppReconciliationReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AppReconciliationReadPort appReconciliationReadPort;

    @Override
    public long countUserProfiles() {
        return appReconciliationReadPort.countUserProfiles();
    }

    @Override
    public ReconciliationOrphanCounts countOrphans() {
        return appReconciliationReadPort.countOrphans();
    }
}
