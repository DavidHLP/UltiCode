package com.ulticode.modules.subscription.constants;

/**
 * Subscription plan types.
 */
public enum SubscriptionPlan {
    FREE("FREE"),
    PREMIUM_MONTHLY("PREMIUM_MONTHLY"),
    PREMIUM_YEARLY("PREMIUM_YEARLY");

    private final String value;

    SubscriptionPlan(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse plan from string value.
     *
     * @param value the string value
     * @return the SubscriptionPlan or null if not found
     */
    public static SubscriptionPlan fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SubscriptionPlan plan : values()) {
            if (plan.value.equalsIgnoreCase(value)) {
                return plan;
            }
        }
        return null;
    }

    /**
     * Check if this plan is a premium plan.
     *
     * @return true if this is a premium plan
     */
    public boolean isPremium() {
        return this == PREMIUM_MONTHLY || this == PREMIUM_YEARLY;
    }
}
