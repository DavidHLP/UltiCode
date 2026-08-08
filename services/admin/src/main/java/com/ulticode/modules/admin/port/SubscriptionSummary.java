package com.ulticode.modules.admin.port;

/**
 * Admin-owned projection of a subscription row containing only the field
 * the revenue reporter actually consumes.
 *
 * <p>Replaces the {@code List<Subscription>} leak in
 * {@link AdminAnalyticsPort#listActiveSubscriptions}. The subscription
 * module stops sharing its full entity with the admin module; only the
 * plan name flows through.
 *
 * @author ulticode
 */
public record SubscriptionSummary(
        String plan
) {}