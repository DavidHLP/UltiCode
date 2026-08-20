package com.ulticode.auth.account;

import com.ulticode.auth.account.mapper.AuthAccountQueryMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisAuthAccountQueryAdapterTest {

    @Test
    void dashboardSummaryIncludesRoleCounts() {
        AuthAccountQueryMapper mapper = mock(AuthAccountQueryMapper.class);
        when(mapper.dashboardStatsSummary(
                LocalDateTime.of(2026, 8, 20, 0, 0),
                LocalDateTime.of(2026, 8, 13, 0, 0),
                LocalDateTime.of(2026, 7, 20, 0, 0)))
                .thenReturn(new AuthAccountQueryMapper.AccountStatsRow(3, 2, 1, 2, 3, 3));
        when(mapper.dashboardRoleCounts()).thenReturn(List.of(
                new AuthAccountQueryMapper.RoleCountRow("ADMIN", 1),
                new AuthAccountQueryMapper.RoleCountRow("USER", 2)));

        var summary = new MyBatisAuthAccountQueryAdapter(mapper).dashboardStatsSummary(
                LocalDateTime.of(2026, 8, 20, 0, 0),
                LocalDateTime.of(2026, 8, 13, 0, 0),
                LocalDateTime.of(2026, 7, 20, 0, 0));

        assertThat(summary.byRole()).containsEntry("ADMIN", 1L)
                .containsEntry("USER", 2L);
    }
}
