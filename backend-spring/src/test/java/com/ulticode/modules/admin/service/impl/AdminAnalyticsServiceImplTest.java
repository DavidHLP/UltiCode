package com.ulticode.modules.admin.service.impl;

import com.ulticode.modules.admin.analytics.ContestParticipationReporter;
import com.ulticode.modules.admin.analytics.RevenueReporter;
import com.ulticode.modules.admin.analytics.SystemResourceReporter;
import com.ulticode.modules.admin.dto.AnalyticsOverviewVO;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.modules.problem.projection.ProblemAnalyticsProjection;
import com.ulticode.modules.user.projection.UserActivityAnalyticsProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the typed analytics overview snapshot. Locks the
 * {@link AnalyticsOverviewVO} field mapping and the acceptance-rate rounding
 * that replaced the historical untyped {@code Map<String, Object>} response.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAnalyticsServiceImplTest {

    @Mock
    private UserActivityAnalyticsProjection userActivityAnalyticsProjection;
    @Mock
    private ProblemAnalyticsProjection problemAnalyticsProjection;
    @Mock
    private ContestParticipationReporter contestParticipationReporter;
    @Mock
    private RevenueReporter revenueReporter;
    @Mock
    private SystemResourceReporter systemResourceReporter;
    @Mock
    private AdminAnalyticsPort adminAnalyticsPort;

    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 17, 12, 0).toInstant(ZoneOffset.UTC),
            ZoneId.of("UTC"));

    private AdminAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAnalyticsServiceImpl(
                userActivityAnalyticsProjection,
                problemAnalyticsProjection,
                contestParticipationReporter,
                revenueReporter,
                systemResourceReporter,
                adminAnalyticsPort,
                clock);
        when(systemResourceReporter.sampleSystemMetrics())
                .thenReturn(new SystemResourceReporter.SystemMetrics(3600L, 42.5));
    }

    @Test
    @DisplayName("overview maps every port aggregate into the typed VO")
    void overviewMapsAggregatesToTypedVo() {
        when(adminAnalyticsPort.countAllUsers()).thenReturn(100L);
        when(adminAnalyticsPort.countDistinctSubmittersInRange(any(), any())).thenReturn(30L);
        when(adminAnalyticsPort.countSubmissionsInRange(any())).thenReturn(200L);
        when(adminAnalyticsPort.countAcceptedSubmissionsInRange(any())).thenReturn(50L);
        when(adminAnalyticsPort.countContestsInRange(any())).thenReturn(5L);
        when(adminAnalyticsPort.countActiveSubscriptions()).thenReturn(7L);

        AnalyticsOverviewVO overview = service.getAnalyticsOverview(30);

        assertThat(overview.getTotalUsers()).isEqualTo(100L);
        assertThat(overview.getActiveUsers()).isEqualTo(30L);
        assertThat(overview.getTotalSubmissions()).isEqualTo(200L);
        assertThat(overview.getAcceptedSubmissions()).isEqualTo(50L);
        assertThat(overview.getAcceptanceRate()).isEqualTo(25.0);
        assertThat(overview.getTotalContests()).isEqualTo(5L);
        assertThat(overview.getActiveSubscriptions()).isEqualTo(7L);
        assertThat(overview.getSystemUptimeSeconds()).isEqualTo(3600L);
        assertThat(overview.getMemoryUsagePercent()).isEqualTo(42.5);
        assertThat(overview.getPeriodDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("acceptance rate is zero when there are no submissions")
    void acceptanceRateZeroWhenNoSubmissions() {
        when(adminAnalyticsPort.countSubmissionsInRange(any())).thenReturn(0L);
        when(adminAnalyticsPort.countAcceptedSubmissionsInRange(any())).thenReturn(0L);

        AnalyticsOverviewVO overview = service.getAnalyticsOverview(7);

        assertThat(overview.getAcceptanceRate()).isEqualTo(0.0);
        assertThat(overview.getPeriodDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("null or non-positive days falls back to the 30-day window")
    void nullDaysFallsBackToDefault() {
        AnalyticsOverviewVO fromNull = service.getAnalyticsOverview(null);
        AnalyticsOverviewVO fromZero = service.getAnalyticsOverview(0);

        assertThat(fromNull.getPeriodDays()).isEqualTo(30);
        assertThat(fromZero.getPeriodDays()).isEqualTo(30);
    }
}
