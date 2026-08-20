package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.SolutionIndexDTO;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dubbo consumer adapter registering {@link SolutionReadPort} as a local
 * admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.SolutionReadProvider}).
 *
 * <p>Admin stats / comment enrichment keep depending on the entity-free
 * port contract (ADMIN-006); this adapter is the only local bean of that
 * type. Read references use the query RPC policy (800 ms, one retry) per
 * {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboSolutionReadAdapter implements SolutionReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SolutionReadPort solutionReadPort;

    @Override
    public List<SolutionIndexDTO> searchForIndex(String query, int limit) {
        return solutionReadPort.searchForIndex(query, limit);
    }

    @Override
    public List<SolutionIndexDTO> searchForIndex(String query, int offset, int limit) {
        return solutionReadPort.searchForIndex(query, offset, limit);
    }

    @Override
    public long countForIndex(String query) {
        return solutionReadPort.countForIndex(query);
    }

    @Override
    public long countByProblemId(Long problemId) {
        return solutionReadPort.countByProblemId(problemId);
    }

    @Override
    public long countByUserId(String userId) {
        return solutionReadPort.countByUserId(userId);
    }

    @Override
    public Map<String, String> findTitlesByIds(Set<String> solutionIds) {
        return solutionReadPort.findTitlesByIds(solutionIds);
    }
}
