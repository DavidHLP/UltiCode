package com.ulticode.app.api.dto;

/**
 * Lightweight DTO for user search index results.
 *
 * <p>Carries only the fields the search module needs — never exposes
 * the internal {@code User} entity. The account fields (username) are
 * owned by Auth; name/avatar live in the App-owned profile row
 * (migration state: App Q-reads the shared users table).
 *
 * <p>P7-LEAF-PLAN-001: added for {@link com.ulticode.app.api.service.UserSearchReadPort}.
 *
 * @param accountId user account id (users.id)
 * @param username  account username (Auth-owned column)
 * @param name      display name (may be {@code null})
 * @param avatar    avatar URL (may be {@code null})
 */
public record UserIndexDTO(
        String accountId,
        String username,
        String name,
        String avatar
) {}
