package com.ulticode.auth.api.dto;

import java.time.OffsetDateTime;

/**
 * Structured permission entry carried inside
 * {@link AuthorizationSnapshotDTO#permissionEntries()}.
 *
 * <p>Each entry records the granular detail the admin user-detail
 * projection needs but the flattened {@code Set<String>} permissions
 * cannot represent:
 * <ul>
 *   <li>{@code source} &mdash; whether the permission comes from the
 *       user's role template ({@code "role"}) or was granted directly
 *       ({@code "direct"});</li>
 *   <li>{@code expiresAt} &mdash; when a directly-granted permission
 *       expires (ISO-8601 offset date-time, or {@code null} for
 *       role-template permissions that never expire).</li>
 * </ul>
 *
 * <p>This record is transport-neutral: no Spring or MyBatis types. The
 * {@code expiresAt} uses {@link OffsetDateTime} so Dubbo Hessian2
 * serialisation is deterministic across JVMs in different time-zones.
 *
 * @param action    the permission action (CREATE, READ, UPDATE, ...)
 * @param resource  the permission resource (USER, PROBLEM, SYSTEM, ...)
 * @param source    "role" for role-template permissions, "direct" for
 *                  user-granted permissions; never null or blank
 * @param expiresAt expiry timestamp, or null when the permission does
 *                  not expire (role-template permissions)
 */
public record PermissionEntry(
        String action,
        String resource,
        String source,
        OffsetDateTime expiresAt) {

    public PermissionEntry {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("resource is required");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
    }
}
