package com.ulticode.app.api.dto;

/**
 * User account info for cross-module authentication/authorization checks.
 * Used by websocket auth after relocation from backend-legacy.
 */
public record AccountInfo(
    String id,
    String username,
    String role,
    boolean isActive,
    boolean isBanned) {
}
