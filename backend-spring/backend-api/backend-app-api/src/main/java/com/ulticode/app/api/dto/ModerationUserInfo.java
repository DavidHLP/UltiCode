package com.ulticode.app.api.dto;

/**
 * Minimal user info for moderation read operations.
 * Replaces direct User entity dependency after moderation relocated to backend-app.
 */
public record ModerationUserInfo(String id, String username) {
}
