package com.ulticode.modules.admin.port;

import java.util.Optional;

/**
 * Provisioning surface the admin bootstrap runners consume over the user domain.
 *
 * <p>Consumer-owned seam (declared in {@code admin.port}, per
 * {@code backend-spring/AGENTS.md}): the production and development bootstrap
 * runners previously reached into {@code user.mapper.UserMapper} and
 * {@code user.entity.User} directly for active-admin counting, identity-conflict
 * checks, and create/restore persistence. This port narrows that to six typed
 * methods; the adapter ({@code user.port.UserProvisioningAdapter}) owns
 * {@code UserMapper}, password encoding, id/timestamp stamping, and all
 * {@code User} entity construction — so admin/bootstrap no longer imports any
 * user-internal type.
 *
 * @author ulticode
 */
public interface UserProvisioningPort {

    /** Count active (isActive=true, isBanned=false) users holding an admin role. */
    long countActiveAdministrators();

    /** True if any user already owns {@code username} or {@code email}. */
    boolean identityExists(String username, String email);

    /** True if any user other than {@code excludeId} already owns {@code email}. */
    boolean emailConflicts(String email, String excludeId);

    /** The id of the user with {@code username}, or empty if none. */
    Optional<String> findIdByUsername(String username);

    /** Create and persist a brand-new active administrator. */
    void createAdministrator(AdministratorSpec spec);

    /** Re-enable an existing administrator (by id) with fresh credentials. */
    void restoreAdministrator(String id, AdministratorSpec spec);

    /** Administrator materialization inputs; {@code rawPassword} is cleartext, encoded by the adapter. */
    record AdministratorSpec(String username, String name, String email,
                             String rawPassword, String role) {

        /**
         * Redacts {@code rawPassword} so the cleartext credential is never emitted via
         * {@code toString()} (e.g. in Mockito or assertion failure messages routed to CI logs),
         * per the repo rule against printing credentials. Value equality and hash are
         * unaffected (still auto-generated across all components).
         */
        @Override
        public String toString() {
            return "AdministratorSpec[username=" + username
                + ", name=" + name
                + ", email=" + email
                + ", rawPassword=" + (rawPassword == null ? "null" : "<redacted>")
                + ", role=" + role + "]";
        }
    }
}
