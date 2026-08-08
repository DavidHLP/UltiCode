package com.ulticode.modules.admin.analytics;

import com.ulticode.modules.admin.config.AdminAnalyticsProperties;
import com.ulticode.modules.admin.dto.RevenueReportVO;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.modules.admin.port.SubscriptionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@link RevenueReportVO} dashboard from
 * {@link AdminAnalyticsPort#listActiveSubscriptions()}.
 *
 * <p>Owns the revenue math that was previously inlined in
 * {@code AdminAnalyticsServiceImpl}: per-plan revenue roll-up, MRR/ARR,
 * ARPU, period total revenue, and the simplified daily revenue trend.
 * Plan prices and the placeholder churn/conversion rates come from
 * {@link AdminAnalyticsProperties} (no more magic numbers in this class).
 *
 * <p>The trend loop is bounded by
 * {@code min(daysToAnalyze, RevenueReporter.MAX_TREND_DAYS)} so a
 * 365-day window does not produce a 365-row trend (matches the
 * historical inline behaviour that capped at 30).
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class RevenueReporter {

    /**
     * Plan name that maps to the configured {@code premiumMonthlyPrice}.
     */
    static final String PLAN_PREMIUM_MONTHLY = "PREMIUM_MONTHLY";

    /**
     * Plan name that maps to the configured {@code premiumYearlyPrice}
     * (divided by 12 to contribute to MRR).
     */
    static final String PLAN_PREMIUM_YEARLY = "PREMIUM_YEARLY";

    /**
     * Upper bound on the number of daily revenue trend rows in the
     * report. Matches the historical inline cap of 30.
     */
    static final int MAX_TREND_DAYS = 30;

    /**
     * Default analysis window when {@code daysToAnalyze} is null or
     * non-positive.
     */
    static final int DEFAULT_DAYS = 30;

    /**
     * Approximate number of days in a month, used to scale MRR to a
     * "daily revenue" trend row and to scale MRR by the analysis window
     * for the period-total revenue field.
     */
    static final int DAYS_PER_MONTH = 30;

    /**
     * Months per year used to derive ARR from MRR.
     */
    static final int MONTHS_PER_YEAR = 12;

    private final AdminAnalyticsPort adminAnalyticsPort;
    private final AdminAnalyticsProperties properties;
    private final Clock clock;

    /**
     * Build the full revenue report for the analysis window starting
     * {@code daysToAnalyze} days before "now".
     *
     * @param daysToAnalyze window length in days; {@code null} or
     *                      non-positive values fall back to
     *                      {@link #DEFAULT_DAYS}
     * @return assembled report VO
     */
    public RevenueReportVO buildReport(Integer daysToAnalyze) {
        int resolvedDays = daysToAnalyze != null && daysToAnalyze > 0 ? daysToAnalyze : DEFAULT_DAYS;

        RevenueReportVO report = new RevenueReportVO();

        List<SubscriptionSummary> activeSubscriptions = adminAnalyticsPort.listActiveSubscriptions();

        Map<String, RevenueReportVO.PlanRevenue> planRevenueMap = new HashMap<>();
        for (SubscriptionSummary sub : activeSubscriptions) {
            String plan = sub.plan();
            double monthlyRevenue = estimateMonthlyRevenue(plan);
            planRevenueMap.merge(plan,
                    new RevenueReportVO.PlanRevenue(plan, 1, monthlyRevenue),
                    (existing, newValue) -> new RevenueReportVO.PlanRevenue(
                            plan,
                            existing.getSubscribers() + 1,
                            existing.getRevenue() + monthlyRevenue
                    )
            );
        }

        report.setByPlan(new ArrayList<>(planRevenueMap.values()));

        // Calculate MRR and ARR
        double mrr = planRevenueMap.values().stream()
                .mapToDouble(RevenueReportVO.PlanRevenue::getRevenue)
                .sum();
        report.setMrr(mrr);
        report.setArr(mrr * MONTHS_PER_YEAR);

        // Subscriber count
        report.setSubscriberCount(activeSubscriptions.size());

        // ARPU
        double arpu = activeSubscriptions.size() > 0 ? mrr / activeSubscriptions.size() : 0.0;
        report.setArpu(arpu);

        // Total revenue in period
        report.setTotalRevenue(mrr * (resolvedDays / (double) DAYS_PER_MONTH));

        // Placeholder values from configuration (no longer magic numbers)
        report.setChurnRate(properties.getDefaultChurnRate());
        report.setConversionRate(properties.getDefaultConversionRate());

        // Revenue trend (simplified)
        report.setRevenueTrend(buildDailyTrend(resolvedDays, mrr));

        return report;
    }

    /**
     * Estimate monthly revenue for a subscription plan.
     */
    double estimateMonthlyRevenue(String plan) {
        if (PLAN_PREMIUM_MONTHLY.equals(plan)) {
            return properties.getPremiumMonthlyPrice();
        }
        if (PLAN_PREMIUM_YEARLY.equals(plan)) {
            return properties.getPremiumYearlyPrice() / MONTHS_PER_YEAR;
        }
        return 0.0;
    }

    /**
     * Build the simplified daily revenue trend — one row per day for the
     * most recent {@code min(daysToAnalyze, MAX_TREND_DAYS)} days.
     * New-subscriber and churned columns are placeholders left at zero
     * until a real source is wired in.
     */
    private List<RevenueReportVO.RevenueTrend> buildDailyTrend(int daysToAnalyze, double mrr) {
        List<RevenueReportVO.RevenueTrend> revenueTrend = new ArrayList<>();
        int boundedDays = Math.min(daysToAnalyze, MAX_TREND_DAYS);
        for (int i = boundedDays - 1; i >= 0; i--) {
            LocalDateTime dayStart = LocalDateTime.now(clock).minusDays(i).withHour(0).withMinute(0).withSecond(0);
            revenueTrend.add(new RevenueReportVO.RevenueTrend(
                    dayStart.toLocalDate().toString(),
                    mrr / DAYS_PER_MONTH,
                    0,
                    0
            ));
        }
        revenueTrend.sort(Comparator.comparing(RevenueReportVO.RevenueTrend::getDate));
        return revenueTrend;
    }
}