package com.ulticode.auth.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HTTP-neutral query for the authenticated user's current session view.
 *
 * <p>The query returns a safe account projection rather than the
 * password-bearing persistence record. The inbound HTTP adapter maps the
 * projection to its existing response DTOs.</p>
 */
public interface CurrentSessionQuery {

    /**
     * Loads the authenticated user's safe identity projection. Browser CSRF
     * state stays at the HTTP boundary because it is carried by a cookie.
     *
     * @param accountId the authenticated principal's account id
     * @return safe user projection
     */
    CurrentUser currentUser(String accountId);

    /**
     * Loads the effective role/direct permission strings for an account.
     *
     * @param accountId the authenticated principal's account id
     * @return effective permission strings
     */
    List<String> permissions(String accountId);

    /** Safe current-user projection; it intentionally has no password field. */
    record CurrentUser(
            String accountId,
            String username,
            String email,
            String role,
            boolean active,
            boolean banned,
            LocalDateTime joinedAt) {
    }
}
