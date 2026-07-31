package com.ulticode.modules.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the admin analytics math that has no
 * database-backed source of truth today (plan pricing, churn, conversion).
 *
 * <p>Binds to {@code admin.analytics.*} in {@code application.yml}. All
 * defaults match the historical hard-coded values so the
 * {@code RevenueReportVO} contract is unchanged when this is added.
 *
 * <p>The premium plan prices are the canonical numbers used by the
 * billing/subscription UI; the churn and conversion rates are placeholder
 * estimates until a real finance source is wired in.
 *
 * @author ulticode
 */
@Data
@Component
@ConfigurationProperties(prefix = "admin.analytics")
public class AdminAnalyticsProperties {

    /**
     * Default monthly revenue assumption for a {@code PREMIUM_MONTHLY}
     * subscription (USD).
     */
    private double premiumMonthlyPrice = 9.99;

    /**
     * Default yearly revenue assumption for a {@code PREMIUM_YEARLY}
     * subscription (USD). The annual price is divided by 12 to derive a
     * monthly recurring revenue contribution.
     */
    private double premiumYearlyPrice = 79.99;

    /**
     * Default monthly churn rate estimate (%) used by the revenue report.
     */
    private double defaultChurnRate = 5.0;

    /**
     * Default free-to-paid conversion rate estimate (%) used by the revenue
     * report.
     */
    private double defaultConversionRate = 2.5;
}