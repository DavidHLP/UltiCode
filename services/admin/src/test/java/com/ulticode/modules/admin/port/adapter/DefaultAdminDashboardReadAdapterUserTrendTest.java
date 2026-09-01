package com.ulticode.modules.admin.port.adapter;

import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAdminDashboardReadAdapterUserTrendTest {

    @Mock
    AccountQueryService accountQueryService;

    @Mock
    SubmissionAdminReadPort submissionAdminReadPort;

    @Mock
    com.ulticode.app.api.service.DashboardAdminReadPort appDashboardReadPort;

    @Test
    void mapsAuthTrendFailureToUnavailableWithoutFallbackPaging() {
        DefaultAdminDashboardReadAdapter adapter =
                new DefaultAdminDashboardReadAdapter(submissionAdminReadPort,
                        new CancellableQueryExecutor("test", 3));
        ReflectionTestUtils.setField(adapter, "accountQueryService", accountQueryService);
        ReflectionTestUtils.setField(adapter, "appDashboardReadPort", appDashboardReadPort);
        when(accountQueryService.getUserTrend(any())).thenReturn(
                RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, "t-1"));

        assertThatThrownBy(() -> adapter.loadChartData(
                "users",
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 3, 23, 59),
                "day"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Dashboard owner query unavailable");
        verify(accountQueryService, times(1)).getUserTrend(any());
        verify(accountQueryService, never()).queryAccounts(any());

        adapter.shutdownQueryExecutor();
    }
}
