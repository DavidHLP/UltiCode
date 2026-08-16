package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * User account info for cross-module authentication/authorization checks.
 * Used by websocket auth after relocation from backend-legacy.
 */
public record AccountInfo(
    String id,
    String username,
    String role,
    boolean isActive,
    boolean isBanned) implements Serializable {
    private static final long serialVersionUID = 1L;

}
