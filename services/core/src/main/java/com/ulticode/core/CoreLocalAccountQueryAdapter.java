package com.ulticode.core;

import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AuthUserTrendAggregateQuery;
import com.ulticode.auth.api.dto.AuthUserTrendBucketDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Core local Adapter for the Auth read-only {@link AccountQueryService} seam.
 *
 * <p>Child contexts run with {@code dubbo.enabled=false}, so the
 * {@code @DubboReference} fields in Admin consumers cannot fire. This
 * adapter exposes the same contract by delegating in-process to the Auth
 * child context's {@code AccountQueryProvider} bean, mirroring
 * {@link CoreLocalIdentityQueryAdapter}.
 *
 * <p>The full contract is delegated because the Admin child injects this
 * contract into more than the permission-mutation path:
 * {@code DefaultAdminAnalyticsPortAdapter}, {@code DefaultAdminDashboardReadAdapter},
 * {@code UserProvisioningAdapter}, {@code AdminUserEnricher} and
 * {@code UserManagementServiceImpl} also consume it. Registering a partial
 * implementation would turn their runtime calls into
 * {@link UnsupportedOperationException} 500s instead of typed results.
 *
 * <p>No sibling Entity, Mapper or transaction manager crosses this seam; the
 * Auth child executes every call against its own Owner persistence through
 * the contract type only.
 */
@Component
public final class CoreLocalAccountQueryAdapter implements AccountQueryService {

    private final CoreOwnerContextManager ownerContexts;

    public CoreLocalAccountQueryAdapter(CoreOwnerContextManager ownerContexts) {
        this.ownerContexts = ownerContexts;
    }

    @Override
    public RpcResult<AuthAccountDTO> getAccountById(String accountId) {
        return auth().getAccountById(accountId);
    }

    @Override
    public RpcResult<AuthAccountDTO> getAccountByUsername(String username) {
        return auth().getAccountByUsername(username);
    }

    @Override
    public RpcResult<AuthAccountDTO> getAccountByEmail(String email) {
        return auth().getAccountByEmail(email);
    }

    @Override
    public RpcResult<AuthAccountDTO> queryAccounts(AccountQueryDTO query) {
        return auth().queryAccounts(query);
    }

    @Override
    public RpcResult<List<AuthAccountDTO>> getAccountsByIds(Set<String> accountIds) {
        return auth().getAccountsByIds(accountIds);
    }

    @Override
    public RpcResult<Long> countAccountsByIdsExcludingUsernameMatch(
            Set<String> accountIds, String usernameQuery) {
        return auth().countAccountsByIdsExcludingUsernameMatch(accountIds, usernameQuery);
    }

    @Override
    public RpcResult<AccountStatsSummary> getDashboardStatsSummary() {
        return auth().getDashboardStatsSummary();
    }

    @Override
    public RpcResult<List<AuthUserTrendBucketDTO>> getUserTrend(AuthUserTrendAggregateQuery query) {
        return auth().getUserTrend(query);
    }

    private AccountQueryService auth() {
        return ownerContexts.bean("auth", AccountQueryService.class);
    }
}
