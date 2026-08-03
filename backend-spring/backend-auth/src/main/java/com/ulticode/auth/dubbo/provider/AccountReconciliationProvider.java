package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.api.dto.AuthReconciliationOrphanCounts;
import com.ulticode.auth.api.service.ReconciliationQueryService;
import com.ulticode.auth.reconciliation.ReconciliationQueryMapper;
import com.ulticode.common.rpc.RpcResult;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

/**
 * Dubbo provider for {@link ReconciliationQueryService}.
 *
 * <p>Backs the nightly reconciliation aggregator with Auth-owner facts
 * (ADR-P7-OWNER-BOUNDARY-RECONCILIATION-20260802 Decision 4): the
 * count/existence reads stay on the Auth side and cross the owner
 * boundary as RPC — no cross-owner DB grants.
 */
@Component
@DubboService(version = "1.0.0")
public class AccountReconciliationProvider implements ReconciliationQueryService {

    private final ReconciliationQueryMapper reconciliationQueryMapper;

    public AccountReconciliationProvider(ReconciliationQueryMapper reconciliationQueryMapper) {
        this.reconciliationQueryMapper = reconciliationQueryMapper;
    }

    @Override
    public RpcResult<Long> countActiveUsers() {
        return RpcResult.success(reconciliationQueryMapper.countActiveUsers(), "t-system");
    }

    @Override
    public RpcResult<Set<String>> existingUserIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return RpcResult.success(Collections.emptySet(), "t-system");
        }
        Set<String> valid = new HashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                valid.add(id);
            }
        }
        if (valid.isEmpty()) {
            return RpcResult.success(Collections.emptySet(), "t-system");
        }
        Set<String> existing = reconciliationQueryMapper.selectExistingIds(valid);
        if (existing == null) {
            return RpcResult.success(Collections.emptySet(), "t-system");
        }
        return RpcResult.success(existing, "t-system");
    }

    @Override
    public RpcResult<AuthReconciliationOrphanCounts> countAuthOrphans() {
        return RpcResult.success(new AuthReconciliationOrphanCounts(
                reconciliationQueryMapper.countOrphanRefreshTokens(),
                reconciliationQueryMapper.countOrphanPasswordResets(),
                reconciliationQueryMapper.countOrphanOauthProviderIdentities(),
                reconciliationQueryMapper.countOrphanUserPermissions()),
                "t-system");
    }
}
