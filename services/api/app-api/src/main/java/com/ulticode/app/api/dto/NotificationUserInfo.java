package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Minimal user info for cross-module read operations (notification
 * fan-out, problem-list author lookup, etc.). Replaces direct
 * {@code User} entity dependency after notification/email and
 * problem-list modules relocated to backend-app.
 *
 * <p>{@code name} added in P7-RELOCATE-PROBLEMLIST-001: the problem-list
 * projection needs the author's display name in addition to username.
 * Existing consumers that do not read {@code name} are unaffected
 * (additive field).
 *
 * @param id       user id
 * @param username unique login handle
 * @param email    email address (may be null when the consumer does
 *                 not need it)
 * @param name     display name (may be null when the source has none)
 */
public record NotificationUserInfo(String id, String username, String email, String name) implements Serializable {

    /**
     * Backwards-compatible convenience constructor for consumers that
     * only need id / username / email (name defaults to null).
     */
    public NotificationUserInfo(String id, String username, String email) {
        this(id, username, email, null);
    }
}
