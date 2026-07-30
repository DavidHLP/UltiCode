package com.ulticode.auth.api.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Authorization snapshot returned by
 * {@link com.ulticode.auth.api.service.AccountAdministrationService#changeAuthorization}
 * and
 * {@link com.ulticode.auth.api.service.AuthorizationSnapshotService#getSnapshot}.
 *
 * <p>The snapshot is the post-write authoritative role + permission
 * set the auth provider commits to; downstream services can cache it
 * verbatim for the configured TTL.
 *
 * <p>{@link #version()} is monotonically increasing per account; the
 * Admin caller passes it back as {@code expectedVersion} on the next
 * write so concurrent changes cannot silently overwrite each other.
 *
 * <p>{@link #permissions()} is captured as an immutable
 * {@link Set#copyOf(java.util.Collection)} snapshot at construction
 * time so subsequent caller mutations cannot leak into the response
 * after the wire has serialised it.
 *
 * <p>{@link #permissionEntries()} carries the same permissions in a
 * structured form with {@code source} ({@code "role"} or
 * {@code "direct"}) and {@code expiresAt} metadata. This is the form
 * the admin user-detail projection needs to render expiry/source in
 * the UI. Callers that only need the flat action:resource set can
 * ignore this field and use {@link #permissions()}.
 *
 * <p>When {@code permissionEntries} is {@code null} or empty (legacy
 * providers), only the flat {@link #permissions()} set is populated.
 */
public record AuthorizationSnapshotDTO(
        String accountId,
        String role,
        Set<String> permissions,
        long version,
        List<PermissionEntry> permissionEntries) {

    /**
     * Convenience constructor for callers that do not have structured
     * permission entries (flat-set only). Equivalent to passing
     * {@code null} for {@code permissionEntries}.
     */
    public AuthorizationSnapshotDTO(
            String accountId,
            String role,
            Set<String> permissions,
            long version) {
        this(accountId, role, permissions, version, null);
    }

    public AuthorizationSnapshotDTO {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException(
                    "accountId is required and must be a UUID String");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        if (permissions == null) {
            throw new IllegalArgumentException("permissions must be non-null");
        }
        for (String p : permissions) {
            if (p == null || p.isBlank()) {
                throw new IllegalArgumentException(
                        "permissions must not contain null or blank elements");
            }
        }
        permissions = Set.copyOf(permissions);
        if (permissionEntries != null) {
            permissionEntries = List.copyOf(permissionEntries);
        } else {
            permissionEntries = List.of();
        }
    }
}
