package com.ulticode.modules.admin.analytics;

import com.ulticode.modules.admin.config.AdminAnalyticsProperties;
import com.ulticode.modules.admin.dto.RevenueReportVO;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.modules.admin.port.SubscriptionSummary;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RevenueReporter}.
 *
 * <p>Pins the historical numbers exactly:
 * <ul>
 *   <li>{@code PREMIUM_MONTHLY} → 9.99 USD/mo</li>
 *   <li>{@code PREMIUM_YEARLY} → 79.99 / 12 USD/mo</li>
 *   <li>churn = 5.0, conversion = 2.5 (default properties)</li>
 *   <li>ARR = MRR × 12</li>
 *   <li>period revenue = MRR × (days / 30)</li>
 *   <li>ARPU = MRR / subscriber count</li>
 *   <li>daily trend = {@code min(days, 30)} rows of {@code mrr / 30}</li>
 * </ul>
 *
 * <p>This test is intentionally a regression guard for the historical
 * numbers — if anyone changes {@code AdminAnalyticsProperties} defaults
 * the assertion failures here point directly at the report contract
 * change.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevenueReporterTest {

    @Mock private AdminAnalyticsPort adminAnalyticsPort;

    private RevenueReporter reporter;
    private AdminAnalyticsProperties properties;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 6, 1, 12, 0, 0);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        properties = new AdminAnalyticsProperties(); // 9.99 / 79.99 / 5.0 / 2.5
        reporter = new RevenueReporter(adminAnalyticsPort, properties, FIXED_CLOCK);
    }

    @Test
    @DisplayName("empty subscriptions: zeros and trend, default churn/conversion applied")
    void emptySubscriptions() {
        when(adminAnalyticsPort.listActiveSubscriptions()).thenReturn(List.of());

        RevenueReportVO report = reporter.buildReport(30);

        assertNotNull(report.getByPlan());
        assertTrue(report.getByPlan().isEmpty());
        assertEquals(0.0, report.getMrr());
        assertEquals(0.0, report.getArr());
        assertEquals(Integer.valueOf(0), report.getSubscriberCount());
        assertEquals(0.0, report.getArpu());
        assertEquals(0.0, report.getTotalRevenue());
        assertEquals(5.0, report.getChurnRate());
        assertEquals(2.5, report.getConversionRate());
        // 30 trend rows
        assertEquals(30, report.getRevenueTrend().size());
    }

    @Test
    @DisplayName("plan revenue roll-up: PREM_MONTHLY=9.99, PREM_YEARLY=79.99/12")
    void planRevenueRollup() {
        when(adminAnalyticsPort.listActiveSubscriptions()).thenReturn(List.of(
                new SubscriptionSummary("PREMIUM_MONTHLY"),
                new SubscriptionSummary("PREMIUM_MONTHLY"),
                new SubscriptionSummary("PREMIUM_MONTHLY"),
                new SubscriptionSummary("PREMIUM_YEARLY")));

        RevenueReportVO report = reporter.buildReport(30);

        // Two plan buckets
        assertEquals(2, report.getByPlan().size());

        // Bucket for PREMIUM_MONTHLY: 3 subscribers × 9.99 = 29.97
        RevenueReportVO.PlanRevenue monthly = findPlan(report, "PREMIUM_MONTHLY");
        assertEquals(Integer.valueOf(3), monthly.getSubscribers());
        assertEquals(3 * 9.99, monthly.getRevenue());

        // Bucket for PREMIUM_YEARLY: 1 subscriber × 79.99/12
        RevenueReportVO.PlanRevenue yearly = findPlan(report, "PREMIUM_YEARLY");
        assertEquals(Integer.valueOf(1), yearly.getSubscribers());
        assertEquals(79.99 / 12.0, yearly.getRevenue());

        // MRR = sum of bucket revenues
        double mrr = 3 * 9.99 + 79.99 / 12.0;
        assertEquals(mrr, report.getMrr());
        // ARR = MRR × 12
        assertEquals(mrr * 12.0, report.getArr());
        // Subscriber count
        assertEquals(Integer.valueOf(4), report.getSubscriberCount());
        // ARPU = MRR / 4
        assertEquals(mrr / 4.0, report.getArpu());
    }

    @Test
    @DisplayName("unknown plan contributes 0 revenue, still counted as a subscriber")
    void unknownPlanZeroRevenue() {
        when(adminAnalyticsPort.listActiveSubscriptions()).thenReturn(List.of(
                new SubscriptionSummary("FREE"),
                new SubscriptionSummary("EXPERIMENTAL")));

        RevenueReportVO report = reporter.buildReport(30);

        assertEquals(2, report.getByPlan().size());
        for (RevenueReportVO.PlanRevenue pr : report.getByPlan()) {
            assertEquals(0.0, pr.getRevenue());
        }
        assertEquals(0.0, report.getMrr());
        assertEquals(Integer.valueOf(2), report.getSubscriberCount());
        // ARPU with MRR=0 stays at 0.0 (the inline guard prevents NPE/divide-by-zero)
        assertEquals(0.0, report.getArpu());
    }

    @Test
    @DisplayName("period total revenue = MRR * (days / 30)")
    void periodTotalRevenue() {
        when(adminAnalyticsPort.listActiveSubscriptions()).thenReturn(List.of(
                new SubscriptionSummary("PREMIUM_MONTHLY")));

        // 9.99 MRR, 60 days window → 9.99 * (60/30) = 19.98
        RevenueReportVO report = reporter.buildReport(60);

        assertEquals(9.99, report.getMrr());
        assertEquals(9.99 * (60.0 / 30.0), report.getTotalRevenue());
    }

    @Test
    @DisplayName("daily trend is bounded by min(days, 30) and rows use mrr/30")
    void dailyTrendRowsBounded() {
        when(adminAnalyticsPort.listActiveSubscriptions()).thenReturn(List.of(
                new SubscriptionSummary("PREMIUM_MONTHLY"),
                new SubscriptionSummary("PREMIUM_MONTHLY")));

        // 2 * 9.99 = 19.98 MRR
        double mrr = 2 * 9.99;

        RevenueReportVO report = reporter.buildReport(7);

        assertEquals(7, report.getRevenueTrend().size());
        for (RevenueReportVO.RevenueTrend row : report.getRevenueTrend()) {
            assertEquals(mrr / 30.0, row.getRevenue());
            assertEquals(Integer.valueOf(0), row.getNewSubscribers());
            assertEquals(Integer.valueOf(0), row.getChurned());
        }

        // 365-day window must still cap at 30
        RevenueReportVO longReport = reporter.buildReport(365);
        assertEquals(30, longReport.getRevenueTrend().size());
    }

    @Test
    @DisplayName("null/zero/negative days falls back to 30, period revenue equals MRR")
    void defaultDaysWindow() {
        when(adminAnalyticsPort.listActiveSubscriptions()).thenReturn(List.of(
                new SubscriptionSummary("PREMIUM_MONTHLY")));

        RevenueReportVO r = reporter.buildReport(null);
        assertEquals(9.99, r.getMrr());
        assertEquals(9.99, r.getTotalRevenue()); // days=30 → factor=1.0

        r = reporter.buildReport(0);
        assertEquals(9.99, r.getMrr());

        r = reporter.buildReport(-1);
        assertEquals(9.99, r.getMrr());
    }

    @Test
    @DisplayName("default churn and conversion rates are 5.0 / 2.5")
    void defaultChurnAndConversion() {
        when(adminAnalyticsPort.listActiveSubscriptions()).thenReturn(List.of());

        RevenueReportVO report = reporter.buildReport(30);

        assertEquals(5.0, report.getChurnRate());
        assertEquals(2.5, report.getConversionRate());
    }

    @Test
    @DisplayName("churn/conversion follow configured overrides, not hard-coded values")
    void configuredChurnAndConversionOverride() {
        properties.setDefaultChurnRate(7.5);
        properties.setDefaultConversionRate(3.0);
        when(adminAnalyticsPort.listActiveSubscriptions()).thenReturn(List.of());

        RevenueReportVO report = reporter.buildReport(30);

        assertEquals(7.5, report.getChurnRate());
        assertEquals(3.0, report.getConversionRate());
    }

    private static RevenueReportVO.PlanRevenue findPlan(RevenueReportVO report, String plan) {
        for (RevenueReportVO.PlanRevenue pr : report.getByPlan()) {
            if (plan.equals(pr.getPlan())) {
                return pr;
            }
        }
        throw new AssertionError("plan not found: " + plan);
    }
}