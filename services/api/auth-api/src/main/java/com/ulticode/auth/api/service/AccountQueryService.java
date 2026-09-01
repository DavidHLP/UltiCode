package com.ulticode.auth.api.service;

import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AuthUserTrendAggregateQuery;
import com.ulticode.auth.api.dto.AuthUserTrendBucketDTO;
import com.ulticode.common.rpc.RpcResult;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provider-owned RPC query service for Auth-owned account data.
 *
 * <p>Offers single-row lookup by ID, username, or email, as well as paginated
 * filtering for governance projections. Distinct from {@link IdentityQueryService}
 * which provides minimal identity validation only.
 */
public interface AccountQueryService {

    RpcResult<AuthAccountDTO> getAccountById(String accountId);

    RpcResult<AuthAccountDTO> getAccountByUsername(String username);

    RpcResult<AuthAccountDTO> getAccountByEmail(String email);

    RpcResult<AuthAccountDTO> queryAccounts(AccountQueryDTO query);

    /** Return existing, non-deleted accounts for the supplied IDs only. */
    RpcResult<List<AuthAccountDTO>> getAccountsByIds(Set<String> accountIds);

    /** Count supplied, non-deleted accounts whose usernames do not match the database search predicate. */
    RpcResult<Long> countAccountsByIdsExcludingUsernameMatch(Set<String> accountIds, String usernameQuery);

    /**
     * Return one bounded summary for dashboard statistics owned by Auth.
     */
    RpcResult<AccountStatsSummary> getDashboardStatsSummary();

    /**
     * Return bounded, Auth-owned registration trend buckets for the dashboard.
     *
     * <p>The provider performs the date filtering and grouping; consumers must
     * not recreate this aggregate by paging through account rows.</p>
     */
    RpcResult<List<AuthUserTrendBucketDTO>> getUserTrend(AuthUserTrendAggregateQuery query);

    record AccountStatsSummary(
            long total,
            long active,
            long banned,
            long activeToday,
            long activeWeek,
            long activeMonth,
            Map<String, Long> byRole) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
