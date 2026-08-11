package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.SolutionOwnerPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Dubbo consumer adapter registering {@link SolutionOwnerPort} as a local
 * admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.SolutionOwnerProvider}).
 *
 * <p>Admin solution writes keep depending on the entity-free port contract
 * (ADMIN-006); this adapter is the only local bean of that type. Write
 * references use the write RPC policy (3 s, no auto-retry) per
 * {@link RpcPolicy}; the admin caller never wraps the remote write in a
 * local transaction.
 */
@Primary
@Component
public class DubboSolutionOwnerAdapter implements SolutionOwnerPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private SolutionOwnerPort solutionOwnerPort;

    @Override
    public FlagResult flagSolution(String id, String reason, LocalDateTime flaggedAt) {
        return solutionOwnerPort.flagSolution(id, reason, flaggedAt);
    }

    @Override
    public FlagResult unflagSolution(String id) {
        return solutionOwnerPort.unflagSolution(id);
    }

    @Override
    public DeleteResult deleteSolution(String id) {
        return solutionOwnerPort.deleteSolution(id);
    }

    @Override
    public void setPublished(String id, boolean published, LocalDateTime publishedAt) {
        solutionOwnerPort.setPublished(id, published, publishedAt);
    }

    @Override
    public Set<String> findExistingIds(List<String> ids) {
        return solutionOwnerPort.findExistingIds(ids);
    }

    @Override
    public String resolveAuthorId(String id) {
        return solutionOwnerPort.resolveAuthorId(id);
    }

    @Override
    public void updateVoteCounts(String id, int likes, int dislikes) {
        solutionOwnerPort.updateVoteCounts(id, likes, dislikes);
    }
}
