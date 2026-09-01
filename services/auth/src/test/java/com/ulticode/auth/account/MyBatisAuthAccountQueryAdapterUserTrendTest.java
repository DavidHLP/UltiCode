package com.ulticode.auth.account;

import com.ulticode.auth.account.mapper.AuthAccountQueryMapper;
import com.ulticode.auth.api.dto.AuthUserTrendAggregateQuery;
import com.ulticode.auth.api.dto.AuthUserTrendBucketDTO;
import com.ulticode.common.dto.DashboardBucketCount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisAuthAccountQueryAdapterUserTrendTest {

    @Mock
    private AuthAccountQueryMapper mapper;

    @Test
    void groupsJoinedAccountsWithWhitelistedPeriodFormatAndLimit() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 3, 23, 59);
        AuthUserTrendAggregateQuery query =
                new AuthUserTrendAggregateQuery(start, end, "day", 4);
        when(mapper.aggregateUserTrend(start, end, "%Y-%m-%d", 4))
                .thenReturn(List.of(
                        new DashboardBucketCount("2026-08-01", 2L),
                        new DashboardBucketCount("2026-08-03", 1L)));

        List<AuthUserTrendBucketDTO> result =
                new MyBatisAuthAccountQueryAdapter(mapper).aggregateUserTrend(query);

        assertThat(result).containsExactly(
                new AuthUserTrendBucketDTO("2026-08-01", 2L),
                new AuthUserTrendBucketDTO("2026-08-03", 1L));
        verify(mapper).aggregateUserTrend(start, end, "%Y-%m-%d", 4);
    }

    @Test
    void keepsEmptyOwnerResultEmpty() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 1, 23, 59);
        AuthUserTrendAggregateQuery query =
                new AuthUserTrendAggregateQuery(start, end, "hour", 2);
        when(mapper.aggregateUserTrend(start, end, "%Y-%m-%d %H:00", 2))
                .thenReturn(List.of());

        List<AuthUserTrendBucketDTO> result =
                new MyBatisAuthAccountQueryAdapter(mapper).aggregateUserTrend(query);

        assertThat(result).isEmpty();
    }
}
