package com.ulticode.auth.api.dto;

/**
 * Identity projection returned by
 * {@code backend-auth} {@code IdentityQueryService}.
 *
 * <p>Per migration guide &sect;4.1, identity queries expose only the
 * minimum fields needed for cross-service authentication / display.
 * Specifically:
 * <ul>
 *   <li>{@link #accountId} &mdash; the canonical UUID String the auth
 *       provider uses to key the account (matches JWT {@code sub});</li>
 *   <li>{@link #username} &mdash; the public username displayed to
 *       other modules when they need to label a user;</li>
 *   <li>{@link #role} &mdash; the single authoritative role the auth
 *       service commits to (today the enum
 *       {@code USER / MODERATOR / ADMIN / SUPER_ADMIN});</li>
 *   <li>{@link #active} / {@link #banned} &mdash; governance flags
 *       required by the App/Admin services so they can refuse
 *       commands locally without an extra round-trip.</li>
 * </ul>
 *
 * <p>No profile columns (avatar, bio, github, etc.) are exposed here on
 * purpose &mdash; those are App-owned in the target topology.
 */
public record UserIdentityDTO(
        String accountId,
        String username,
        String role,
        boolean active,
        boolean banned) {
}