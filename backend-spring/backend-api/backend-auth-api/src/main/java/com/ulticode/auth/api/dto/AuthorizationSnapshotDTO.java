package com.ulticode.auth.api.dto;

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
 */
public record AuthorizationSnapshotDTO(
        String accountId,
        String role,
        Set<String> permissions,
        long version) {

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
    }
}