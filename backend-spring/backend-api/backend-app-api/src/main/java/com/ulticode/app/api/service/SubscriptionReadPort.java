package com.ulticode.app.api.service;

import java.util.List;

/**
 * Read-side port for subscription queries owned by the App service.
 *
 * <p>Consumed by the Admin service's analytics adapter to read subscription
 * aggregates without importing the App module's internal mapper or entity.
 * This is the provider-owned contract pattern: the interface lives in
 * {@code backend-app-api}, the implementation in {@code backend-app}, and
 * the consumer ({@code backend-admin}) depends only on the contract module.
 *
 * <p>P7-APP-SUBSCRIPTION-001: extracted when the subscription family relocated
 * from backend-legacy to backend-app. The admin analytics adapter previously
 * injected {@code SubscriptionMapper} directly (via the legacy dependency
 * chain); it now injects this port and maps results to admin-owned
 * projections.
 *
 * @see com.ulticode.modules.admin.port.SubscriptionSummary
 */
public interface SubscriptionReadPort {

    /**
     * Count all subscriptions currently in ACTIVE status.
     *
     * @return total active subscriber count
     */
    long countActiveSubscriptions();

    /**
     * List the plan names of all currently-active subscriptions.
     *
     * <p>Returns raw plan strings (e.g. {@code "PREMIUM_MONTHLY"}) so the
     * consumer can aggregate without a shared DTO. The admin revenue
     * reporter maps these to its own {@code SubscriptionSummary} projection.
     *
     * @return list of plan names for active subscriptions
     */
    List<String> listActiveSubscriptionPlans();
}
