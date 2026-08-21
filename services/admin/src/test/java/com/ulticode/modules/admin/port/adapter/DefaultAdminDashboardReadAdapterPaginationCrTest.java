package com.ulticode.modules.admin.port.adapter;

import com.ulticode.auth.api.dto.AuthAccountDTO;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAdminDashboardReadAdapterPaginationCrTest {

    @Mock
    AccountQueryService accountQueryService;

    @Mock
    SubmissionAdminReadPort submissionAdminReadPort;

    @Mock
    com.ulticode.app.api.service.DashboardAdminReadPort appDashboardReadPort;

    @Test
    void rejectsNonEmptyOutOfRangePage() {
        DefaultAdminDashboardReadAdapter adapter =
                new DefaultAdminDashboardReadAdapter(submissionAdminReadPort,
                        new CancellableQueryExecutor("test", 3));
        ReflectionTestUtils.setField(adapter, "accountQueryService", accountQueryService);
        ReflectionTestUtils.setField(adapter, "appDashboardReadPort", appDashboardReadPort);

        AuthAccountDTO a1 = new AuthAccountDTO("u-1", "u1", "u1@test.com", "USER", true, false,
                null, null, LocalDateTime.now().minusDays(1), LocalDateTime.now(), 1L);
        AuthAccountDTO a2 = new AuthAccountDTO("u-2", "u2", "u2@test.com", "USER", true, false,
                null, null, LocalDateTime.now().minusDays(1), LocalDateTime.now(), 1L);

        // Page 1 is valid and indicates a second page; page 2 is non-empty but
        // outside the reported totalPages, exercising the new guard directly.
        RpcResult.Page firstPage = new RpcResult.Page(List.of(a1, a2), 3L, 1, 2, 2);
        RpcResult<AuthAccountDTO> validFirstPage =
                new RpcResult<>(true, null, firstPage, null, "t-1", null, null);
        RpcResult.Page malformedPage = new RpcResult.Page(List.of(a1), 1L, 2, 2, 1);
        RpcResult<AuthAccountDTO> malformedSecondPage =
                new RpcResult<>(true, null, malformedPage, null, "t-2", null, null);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(validFirstPage, malformedSecondPage);

        assertThatThrownBy(() -> adapter.loadChartData("users",
                LocalDateTime.now().minusDays(2), LocalDateTime.now(), "day"))
                .isInstanceOf(BusinessException.class);
        verify(accountQueryService, org.mockito.Mockito.times(2)).queryAccounts(any());

        adapter.shutdownQueryExecutor();
    }

}
