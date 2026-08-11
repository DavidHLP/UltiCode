package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.ProblemListDetailDTO;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.service.ProblemListChainReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dubbo consumer adapter registering {@link ProblemListChainReadPort} as
 * a local admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.ProblemListChainReadProvider}).
 *
 * <p>Admin services keep depending on the entity-free port contract; this
 * adapter is the only local bean of that type. Read references use the
 * query RPC policy (800 ms, one retry) per {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboProblemListChainReadAdapter implements ProblemListChainReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemListChainReadPort problemListChainReadPort;

    @Override
    public ProblemListSummaryDTO findSummary(String listId) {
        return problemListChainReadPort.findSummary(listId);
    }

    @Override
    public ProblemListDetailDTO findAdminDetail(String listId) {
        return problemListChainReadPort.findAdminDetail(listId);
    }
}
