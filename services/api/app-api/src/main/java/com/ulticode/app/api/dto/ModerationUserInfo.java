package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Minimal user info for moderation read operations.
 * Replaces direct User entity dependency after moderation relocated to backend-app.
 */
public record ModerationUserInfo(String id, String username) implements Serializable {
    private static final long serialVersionUID = 1L;

}
