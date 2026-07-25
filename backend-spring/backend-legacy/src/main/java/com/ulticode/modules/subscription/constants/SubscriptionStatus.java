package com.ulticode.modules.subscription.constants;

/**
 * Subscription status types.
 */
public enum SubscriptionStatus {
    ACTIVE("ACTIVE"),
    EXPIRED("EXPIRED"),
    CANCELLED("CANCELLED"),
    PENDING("PENDING");

    private final String value;

    SubscriptionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse status from string value.
     *
     * @param value the string value
     * @return the SubscriptionStatus or null if not found
     */
    public static SubscriptionStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SubscriptionStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
}
