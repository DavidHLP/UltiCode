package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AuthAccountQueryPort;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Dubbo provider implementing {@link AccountQueryService}.
 *
 * <p>Provides read-only RPC operations for Auth-owned account queries.
 */
@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class AccountQueryProvider implements AccountQueryService {

    private static final String DEFAULT_TRACE_ID = "t-system";
    private static final int MAX_ACCOUNT_ID_BATCH = 100;

    private final AuthAccountQueryPort queryPort;

    public AccountQueryProvider(AuthAccountQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Override
    public RpcResult<AuthAccountDTO> getAccountById(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, DEFAULT_TRACE_ID);
        }
        Optional<AuthAccountDTO> dto = queryPort.findById(accountId);
        return dto.map(authAccountDTO -> RpcResult.success(authAccountDTO, DEFAULT_TRACE_ID))
                .orElseGet(() -> RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, DEFAULT_TRACE_ID));
    }

    @Override
    public RpcResult<AuthAccountDTO> getAccountByUsername(String username) {
        if (username == null || username.isBlank()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, DEFAULT_TRACE_ID);
        }
        Optional<AuthAccountDTO> dto = queryPort.findByUsername(username);
        return dto.map(authAccountDTO -> RpcResult.success(authAccountDTO, DEFAULT_TRACE_ID))
                .orElseGet(() -> RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, DEFAULT_TRACE_ID));
    }

    @Override
    public RpcResult<AuthAccountDTO> getAccountByEmail(String email) {
        if (email == null || email.isBlank()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, DEFAULT_TRACE_ID);
        }
        Optional<AuthAccountDTO> dto = queryPort.findByEmail(email);
        return dto.map(authAccountDTO -> RpcResult.success(authAccountDTO, DEFAULT_TRACE_ID))
                .orElseGet(() -> RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, DEFAULT_TRACE_ID));
    }


    @Override
    public RpcResult<List<AuthAccountDTO>> getAccountsByIds(java.util.Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return RpcResult.success(List.of(), DEFAULT_TRACE_ID);
        }
        return RpcResult.success(queryPort.findByIds(accountIds), DEFAULT_TRACE_ID);
    }

    @Override
    public RpcResult<Long> countAccountsByIdsExcludingUsernameMatch(
            java.util.Set<String> accountIds, String usernameQuery) {
        if (usernameQuery == null || usernameQuery.isBlank()) {
            return RpcResult.failure(AuthErrorCode.INVALID_ACCOUNT_REQUEST, DEFAULT_TRACE_ID);
        }
        if (accountIds == null || accountIds.isEmpty()) {
            return RpcResult.success(0L, DEFAULT_TRACE_ID);
        }
        if (accountIds.size() > MAX_ACCOUNT_ID_BATCH
                || accountIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            return RpcResult.failure(AuthErrorCode.INVALID_ACCOUNT_REQUEST, DEFAULT_TRACE_ID);
        }
        return RpcResult.success(
                queryPort.countByIdsExcludingUsernameMatch(accountIds, usernameQuery), DEFAULT_TRACE_ID);
    }

    @Override
    public RpcResult<AuthAccountDTO> queryAccounts(AccountQueryDTO query) {
        if (query == null) {
            query = new AccountQueryDTO(null, null, null, null, 1, 10, "joinedAt", "desc");
        }
        int page = query.page();
        int limit = query.limit();
        int offset = (page - 1) * limit;

        long total = queryPort.countAccounts(query);
        if (total == 0) {
            return RpcResult.page(List.of(), 0L, page, limit, DEFAULT_TRACE_ID);
        }
        List<AuthAccountDTO> items = queryPort.queryAccounts(query, offset, limit);
        return RpcResult.page(items, total, page, limit, DEFAULT_TRACE_ID);
    }

    @Override
    public RpcResult<AccountQueryService.AccountStatsSummary> getDashboardStatsSummary() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return RpcResult.success(queryPort.dashboardStatsSummary(
                now.minusDays(1), now.minusWeeks(1), now.minusMonths(1)), DEFAULT_TRACE_ID);
    }
}
