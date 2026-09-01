package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.account.AuthAccountQueryPort;
import com.ulticode.auth.api.dto.AuthUserTrendAggregateQuery;
import com.ulticode.auth.api.dto.AuthUserTrendBucketDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountQueryProviderUserTrendTest {

    @Mock
    private AuthAccountQueryPort queryPort;

    @Test
    void returnsOwnerAggregatedBucketsInOrder() {
        AuthUserTrendAggregateQuery query = query(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 3, 23, 59),
                "day", 4);
        List<AuthUserTrendBucketDTO> buckets = List.of(
                new AuthUserTrendBucketDTO("2026-08-01", 2),
                new AuthUserTrendBucketDTO("2026-08-03", 1));
        when(queryPort.aggregateUserTrend(query)).thenReturn(buckets);

        RpcResult<List<AuthUserTrendBucketDTO>> result = provider().getUserTrend(query);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsExactlyElementsOf(buckets);
        verify(queryPort).aggregateUserTrend(query);
    }

    @Test
    void rejectsBucketLimitThatExceedsContractBound() {
        AuthUserTrendAggregateQuery query = query(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                "day", AuthUserTrendAggregateQuery.MAX_BUCKETS + 1);

        RpcResult<List<AuthUserTrendBucketDTO>> result = provider().getUserTrend(query);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.INVALID_ACCOUNT_REQUEST.code());
        verifyNoInteractions(queryPort);
    }

    @Test
    void rejectsDateRangeThatCanExceedRequestedBucketLimit() {
        AuthUserTrendAggregateQuery query = query(
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 2, 15, 0, 0),
                "day", 10);

        RpcResult<List<AuthUserTrendBucketDTO>> result = provider().getUserTrend(query);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.INVALID_ACCOUNT_REQUEST.code());
        verifyNoInteractions(queryPort);
    }

    @Test
    void mapsOwnerFailureToTypedAuthFailure() {
        AuthUserTrendAggregateQuery query = query(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 23, 59),
                "day", 2);
        when(queryPort.aggregateUserTrend(query))
                .thenThrow(new IllegalStateException("database offline"));

        RpcResult<List<AuthUserTrendBucketDTO>> result = provider().getUserTrend(query);

        assertThat(result.success()).isFalse();
        assertThat(result.data()).isNull();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.UNEXPECTED_AUTH_STATE.code());
    }

    @Test
    void mapsNullOwnerPayloadToTypedAuthFailureInsteadOfZeroSuccess() {
        AuthUserTrendAggregateQuery query = query(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 23, 59),
                "day", 2);
        when(queryPort.aggregateUserTrend(any())).thenReturn(null);

        RpcResult<List<AuthUserTrendBucketDTO>> result = provider().getUserTrend(query);

        assertThat(result.success()).isFalse();
        assertThat(result.data()).isNull();
        assertThat(result.error().code()).isEqualTo(AuthErrorCode.UNEXPECTED_AUTH_STATE.code());
    }

    private AccountQueryService provider() {
        return new AccountQueryProvider(queryPort);
    }

    private AuthUserTrendAggregateQuery query(
            LocalDateTime start, LocalDateTime end, String period, int maxBuckets) {
        return new AuthUserTrendAggregateQuery(start, end, period, maxBuckets);
    }
}
