package com.ulticode.app.api.dto;

/**
 * Minimal user info for notification read operations (email lookup, broadcast fan-out).
 * Replaces direct User entity dependency after notification/email relocated to backend-app.
 */
public record NotificationUserInfo(String id, String username, String email) {
}
