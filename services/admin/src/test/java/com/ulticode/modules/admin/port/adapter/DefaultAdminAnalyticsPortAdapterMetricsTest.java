package com.ulticode.modules.admin.port.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.app.api.service.ContestParticipantReadPort;
import com.ulticode.app.api.service.SubscriptionReadPort;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.metrics.AdminUseCaseMetrics;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultAdminAnalyticsPortAdapterMetricsTest {

    @Mock
    private ContestAdminReadPort contestAdminReadPort;
    @Mock
    private ContestParticipantReadPort contestParticipantReadPort;
    @Mock
    private SubscriptionReadPort subscriptionReadPort;
    @Mock
    private SubmissionAdminReadPort submissionAdminReadPort;
    @Mock
    private AccountQueryService accountQueryService;

    private DefaultAdminAnalyticsPortAdapter adapter;

    @AfterEach
    void closeAdapter() {
        if (adapter != null) {
            adapter.shutdownQueryExecutor();
        }
    }

    @Test
    void overviewMetricsExposeOwnerFanoutAndOneParallelRound() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(accountQueryService.queryAccounts(any()))
                .thenReturn(RpcResult.page(List.of(), 0, 1, 0, "trace"));
        when(submissionAdminReadPort.countDistinctUsersInRange(from, to)).thenReturn(30L);
        when(submissionAdminReadPort.countSubmissionsInRange(from)).thenReturn(200L);
        when(submissionAdminReadPort.countAcceptedSubmissionsInRange(from)).thenReturn(50L);
        when(contestAdminReadPort.selectByStartTimeAfter(from)).thenReturn(List.of(contest()));
        when(subscriptionReadPort.countActiveSubscriptions()).thenReturn(7L);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        adapter = adapter(new AdminUseCaseMetrics(registry, new com.ulticode.common.time.FakeTimeSource()));

        AdminAnalyticsPort.AnalyticsOverviewData result = adapter.loadOverviewData(from, to);

        assertThat(result.totalUsers()).isZero();
        assertThat(registry.find(AdminUseCaseMetrics.LOGICAL_CALLS)
                .tags("use_case", "I-ANALYTICS-OVERVIEW", "owner", "AUTH")
                .summary().max()).isEqualTo(1D);
        assertThat(registry.find(AdminUseCaseMetrics.LOGICAL_CALLS)
                .tags("use_case", "I-ANALYTICS-OVERVIEW", "owner", "APP")
                .summary().max()).isEqualTo(2D);
        assertThat(registry.find(AdminUseCaseMetrics.LOGICAL_CALLS)
                .tags("use_case", "I-ANALYTICS-OVERVIEW", "owner", "SUBMISSION")
                .summary().max()).isEqualTo(3D);
        assertThat(registry.find(AdminUseCaseMetrics.SERIAL_ROUNDS)
                .tags("use_case", "I-ANALYTICS-OVERVIEW", "owner", "all")
                .summary().max()).isEqualTo(1D);
        assertThat(registry.find(AdminUseCaseMetrics.FRESHNESS)
                .tags("use_case", "I-ANALYTICS-OVERVIEW", "owner", "all",
                        "freshness", "REQ")
                .counter().count()).isEqualTo(1D);
        assertThat(registry.find(AdminUseCaseMetrics.DEGRADATION)
                .tags("use_case", "I-ANALYTICS-OVERVIEW", "owner", "all",
                        "degradation", DegradationStatus.OK.name())
                .counter().count()).isEqualTo(1D);
    }

    @Test
    void failedOverviewKeepsOwnerErrorAndRecordsUnavailableOnlyInMetrics() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(accountQueryService.queryAccounts(any())).thenReturn(null);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        adapter = adapter(new AdminUseCaseMetrics(registry, new com.ulticode.common.time.FakeTimeSource()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.loadOverviewData(from, to))
                .hasMessageContaining("Analytics owner query unavailable");

        assertThat(registry.find(AdminUseCaseMetrics.DEGRADATION)
                .tags("use_case", "I-ANALYTICS-OVERVIEW", "owner", "all",
                        "degradation", DegradationStatus.UNAVAILABLE.name())
                .counter().count()).isEqualTo(1D);
    }

    @Test
    void contestAndRevenueHooksUseBoundedUseCaseLabels() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
        when(contestAdminReadPort.selectByStartTimeAfter(from)).thenReturn(List.of(contest()));
        when(contestParticipantReadPort.findByContestIds(List.of("contest-1")))
                .thenReturn(List.of(new ContestParticipantReadPort.ParticipantInfo("contest-1", "user-1")));
        when(subscriptionReadPort.listActiveSubscriptionPlans()).thenReturn(List.of());

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        adapter = adapter(new AdminUseCaseMetrics(registry, new com.ulticode.common.time.FakeTimeSource()));

        adapter.loadContestData(from);
        adapter.listActiveSubscriptions();

        assertThat(registry.find(AdminUseCaseMetrics.LOGICAL_CALLS)
                .tags("use_case", "I-ANALYTICS-CONTEST", "owner", "APP")
                .summary().max()).isEqualTo(2D);
        assertThat(registry.find(AdminUseCaseMetrics.LOGICAL_CALLS)
                .tags("use_case", "I-ANALYTICS-REVENUE", "owner", "APP")
                .summary().max()).isEqualTo(1D);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getValue()).doesNotContain("contest-1", "user-1")));
    }

    private DefaultAdminAnalyticsPortAdapter adapter(AdminUseCaseMetrics metrics) {
        adapter = new DefaultAdminAnalyticsPortAdapter(
                contestAdminReadPort,
                contestParticipantReadPort,
                subscriptionReadPort,
                submissionAdminReadPort);
        ReflectionTestUtils.setField(adapter, "accountQueryService", accountQueryService);
        ReflectionTestUtils.setField(adapter, "useCaseMetrics", metrics);
        return adapter;
    }

    private static ContestAdminDTO contest() {
        ContestAdminDTO contest = new ContestAdminDTO();
        contest.setId("contest-1");
        contest.setTitle("Contest");
        contest.setContestType("ACM");
        contest.setStartTime(LocalDateTime.of(2026, 7, 2, 0, 0));
        return contest;
    }
}
