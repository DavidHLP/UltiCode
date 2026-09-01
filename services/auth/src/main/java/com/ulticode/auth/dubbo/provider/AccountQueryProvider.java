package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AuthAccountQueryPort;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AuthUserTrendAggregateQuery;
import com.ulticode.auth.api.dto.AuthUserTrendBucketDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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
    private static final Set<String> TREND_PERIODS =
            Set.of("hour", "day", "week", "month", "year");

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
    @Override
    public RpcResult<List<AuthUserTrendBucketDTO>> getUserTrend(
            AuthUserTrendAggregateQuery query) {
        if (!isValidTrendQuery(query)) {
            return RpcResult.failure(AuthErrorCode.INVALID_ACCOUNT_REQUEST, DEFAULT_TRACE_ID);
        }
        try {
            List<AuthUserTrendBucketDTO> buckets = queryPort.aggregateUserTrend(query);
            if (!hasValidTrendBuckets(buckets, query.maxBuckets())) {
                return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, DEFAULT_TRACE_ID);
            }
            return RpcResult.success(List.copyOf(buckets), DEFAULT_TRACE_ID);
        } catch (RuntimeException exception) {
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, DEFAULT_TRACE_ID);
        }
    }

    private static boolean isValidTrendQuery(AuthUserTrendAggregateQuery query) {
        if (query == null || query.start() == null || query.end() == null
                || query.start().isAfter(query.end())
                || query.period() == null
                || !TREND_PERIODS.contains(query.period().toLowerCase(Locale.ROOT))
                || query.maxBuckets() < 1
                || query.maxBuckets() > AuthUserTrendAggregateQuery.MAX_BUCKETS) {
            return false;
        }
        return estimatedBucketCount(query) <= query.maxBuckets();
    }

    private static long estimatedBucketCount(AuthUserTrendAggregateQuery query) {
        LocalDateTime start = query.start();
        LocalDateTime end = query.end();
        return switch (query.period()) {
            case "hour" -> ChronoUnit.HOURS.between(
                    start.withMinute(0).withSecond(0).withNano(0),
                    end.withMinute(0).withSecond(0).withNano(0)) + 1L;
            case "day" -> ChronoUnit.DAYS.between(
                    start.toLocalDate(), end.toLocalDate()) + 1L;
            case "week" -> ChronoUnit.WEEKS.between(
                    start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    end.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) + 1L;
            case "month" -> ChronoUnit.MONTHS.between(
                    start.toLocalDate().withDayOfMonth(1),
                    end.toLocalDate().withDayOfMonth(1)) + 1L;
            case "year" -> ChronoUnit.YEARS.between(
                    start.toLocalDate().withDayOfYear(1),
                    end.toLocalDate().withDayOfYear(1)) + 1L;
            default -> Long.MAX_VALUE;
        };
    }

    private static boolean hasValidTrendBuckets(
            List<AuthUserTrendBucketDTO> buckets, int maxBuckets) {
        if (buckets == null || buckets.size() > maxBuckets) {
            return false;
        }
        String previousDate = null;
        for (AuthUserTrendBucketDTO bucket : buckets) {
            if (bucket == null || bucket.date() == null || bucket.date().isBlank()
                    || bucket.count() < 0
                    || (previousDate != null && previousDate.compareTo(bucket.date()) >= 0)) {
                return false;
            }
            previousDate = bucket.date();
        }
        return true;
    }
}
