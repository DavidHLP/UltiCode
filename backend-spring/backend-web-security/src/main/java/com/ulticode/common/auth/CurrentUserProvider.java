package com.ulticode.common.auth;

/**
 * Port for accessing the current authenticated user's identity and roles.
 *
 * <p>Replaces the old static {@code SecurityUtil} coupling. Production code
 * depends on this interface, making the security context injectable and
 * mockable in tests.
 *
 * <p>Adapters:
 * <ul>
 *   <li>{@link SecurityCurrentUserProvider} (prod, wraps SecurityContextHolder)</li>
 *   <li>any test double (inject via {@code @MockBean} or direct construction)</li>
 * </ul>
 *
 * <p>This interface owns the canonical five-method seam. Shell adapters that
 * read {@code SecurityContextHolder} are retained in their respective shells
 * ({@code backend-legacy}, {@code backend-auth}) and implement this interface.
 */
public interface CurrentUserProvider {

    /**
     * Get the current authenticated user's ID.
     *
     * @return the user ID, or {@code null} if not authenticated
     */
    String getCurrentUserId();

    /**
     * Get the current authenticated user's username.
     *
     * @return the username, or {@code null} if not authenticated or no details available
     */
    String getCurrentUsername();

    /**
     * Check if the current user is authenticated.
     *
     * @return {@code true} if authenticated, {@code false} otherwise
     */
    boolean isAuthenticated();

    /**
     * Check if the current user has a specific role.
     *
     * @param role the role to check (without {@code ROLE_} prefix)
     * @return {@code true} if the user has the role, {@code false} otherwise
     */
    boolean hasRole(String role);

    /**
     * Check if the current user has any of the specified roles.
     *
     * @param roles the roles to check (without {@code ROLE_} prefix)
     * @return {@code true} if the user has at least one of the roles, {@code false} otherwise
     */
    boolean hasAnyRole(String... roles);
}
