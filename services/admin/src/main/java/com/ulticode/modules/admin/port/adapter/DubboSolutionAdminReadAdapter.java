package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.SolutionAdminReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dubbo consumer adapter registering {@link SolutionAdminReadPort} as a
 * local admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.SolutionAdminReadProvider}).
 *
 * <p>Admin projections keep depending on the entity-free port contract
 * (ADMIN-006); this adapter is the only local bean of that type. Read
 * references use the query RPC policy (800 ms, one retry) per
 * {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboSolutionAdminReadAdapter implements SolutionAdminReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SolutionAdminReadPort solutionAdminReadPort;

    @Override
    public SolutionAdminPage page(SolutionAdminQuery query) {
        return solutionAdminReadPort.page(query);
    }

    @Override
    public SolutionAdminRow getById(String id) {
        return solutionAdminReadPort.getById(id);
    }
}
