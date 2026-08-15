package com.ulticode.notification.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Reproduces App's stable NotificationIntentCreated event-id derivation. */
public final class NotificationEventIdentity {

    private static final int EVENT_ID_LENGTH = 40;
    private static final String PREFIX = "notification-";

    private NotificationEventIdentity() {
    }

    public static String eventId(String intentId) {
        if (intentId == null || intentId.isBlank()) {
            throw new IllegalArgumentException("Notification intent id must not be blank");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(intentId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return PREFIX + hex.substring(0, EVENT_ID_LENGTH - PREFIX.length());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
