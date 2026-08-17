package com.ulticode.notification.api.service;

/**
 * Stable service-discovery contract for the notification owner.
 *
 * <p>The provider is hosted by {@code backend-notification}. App-owned
 * recipient/fact seams remain separate contracts; they do not change the
 * Notification provider identity.
 */
public final class NotificationServiceContract {

    public static final String DUBBO_GROUP = "backend-notification";
    public static final String DUBBO_VERSION = "1.0.0";

    private NotificationServiceContract() {
    }
}
