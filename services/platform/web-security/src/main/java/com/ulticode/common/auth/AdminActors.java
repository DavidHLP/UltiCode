package com.ulticode.common.auth;

/**
 * Shared derivation of the delegated admin actor type used on write
 * commands ({@code ActorDelegation.actorType()}).
 *
 * <p>Callers must already sit behind an admin authorization check; this
 * helper only projects the authenticated principal's strongest admin role.
 * Static on purpose so {@link CurrentUserProvider} test doubles keep their
 * {@code hasRole} stub semantics.
 */
public final class AdminActors {

    private AdminActors() {
    }

    /**
     * The delegated admin actor type for the current principal:
     * {@code SUPER_ADMIN} when held, otherwise {@code ADMIN}.
     *
     * @param user the current authenticated principal provider
     * @return the admin actor type, never {@code null}
     */
    public static String typeOf(CurrentUserProvider user) {
        return user.hasRole("SUPER_ADMIN") ? "SUPER_ADMIN" : "ADMIN";
    }
}
