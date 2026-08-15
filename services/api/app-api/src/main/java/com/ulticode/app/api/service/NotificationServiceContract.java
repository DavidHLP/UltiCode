package com.ulticode.app.api.service;

/**
 * Stable service-discovery contract for the notification owner.
 *
 * <p>The provider may be hosted by {@code backend-app} during the reversible
 * extraction window, but the Dubbo identity is already the target owner
 * identity. This keeps Admin consumers and the eventual notification runtime
 * on one provider group without changing the Java contract shape again.
 */
public final class NotificationServiceContract {

    public static final String DUBBO_GROUP = "backend-notification";
    public static final String DUBBO_VERSION = "1.0.0";

    private NotificationServiceContract() {
    }
}
