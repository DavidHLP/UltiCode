package com.ulticode.modules.admin.query;

import com.ulticode.modules.admin.dto.AdminUserVO;

import java.util.Objects;
import java.util.Set;

/**
 * Result of the admin user-detail use case.
 *
 * <p>{@link Availability#UNAVAILABLE} on a found user means every requested
 * section failed. A missing account is represented separately by
 * {@link Failure#NOT_FOUND}; it must not be confused with an owner outage.
 */
public record AdminUserDetailResult(
        AdminUserVO user,
        Availability availability,
        Section profile,
        Section stats,
        Section permissions,
        Failure failure,
        PermissionSnapshot authorizationSnapshot) {

    public AdminUserDetailResult {
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(permissions, "permissions");
        if (failure == null && user == null) {
            throw new IllegalArgumentException("found detail must include a user");
        }
        if (failure != null && user != null) {
            throw new IllegalArgumentException("failed detail must not include a user");
        }
        if (permissions.status() == Availability.OK && authorizationSnapshot == null) {
            throw new IllegalArgumentException(
                    "successful permissions section requires an authorization snapshot");
        }
        if (permissions.status() != Availability.OK && authorizationSnapshot != null) {
            throw new IllegalArgumentException(
                    "unavailable permissions section must not include an authorization snapshot");
        }
    }

    /** Overall section availability. */
    public enum Availability {
        OK,
        PARTIAL,
        UNAVAILABLE
    }

    /** Failure semantics for the authoritative account lookup. */
    public enum Failure {
        NOT_FOUND,
        TRANSPORT_UNAVAILABLE
    }

    /** Availability and safe, provider-neutral reason for one section. */
    public record Section(Availability status, String reason) {
        public Section {
            Objects.requireNonNull(status, "status");
        }

        /** Alias for callers that use availability terminology. */
        public Availability availability() {
            return status;
        }

        public static Section ok() {
            return new Section(Availability.OK, null);
        }

        public static Section unavailable(String reason) {
            return new Section(Availability.UNAVAILABLE, reason);
        }
    }

    /**
     * Complete Auth authorization state used as the precondition for a
     * replacement write. The source and version make provenance explicit.
     */
    public record PermissionSnapshot(
            String source,
            String role,
            Set<String> permissions,
            long version) {
        public PermissionSnapshot {
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("source is required");
            }
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("role is required");
            }
            Objects.requireNonNull(permissions, "permissions");
            permissions = Set.copyOf(permissions);
            if (version < 0) {
                throw new IllegalArgumentException("version must be non-negative");
            }
        }
    }

    /** Alias retained for readable call sites. */
    public PermissionSnapshot permissionSnapshot() {
        return authorizationSnapshot;
    }

    public static AdminUserDetailResult notFound() {
        Section unavailable = Section.unavailable("account not found");
        return new AdminUserDetailResult(
                null,
                Availability.UNAVAILABLE,
                unavailable,
                unavailable,
                unavailable,
                Failure.NOT_FOUND,
                null);
    }

    public static AdminUserDetailResult unavailable(String reason) {
        Section unavailable = Section.unavailable(reason);
        return new AdminUserDetailResult(
                null,
                Availability.UNAVAILABLE,
                unavailable,
                unavailable,
                unavailable,
                Failure.TRANSPORT_UNAVAILABLE,
                null);
    }

    public static AdminUserDetailResult found(
            AdminUserVO user,
            Section profile,
            Section stats,
            Section permissions,
            PermissionSnapshot authorizationSnapshot) {
        return new AdminUserDetailResult(
                user,
                overall(profile, stats, permissions),
                profile,
                stats,
                permissions,
                null,
                authorizationSnapshot);
    }

    private static Availability overall(Section... sections) {
        boolean allOk = true;
        boolean allUnavailable = true;
        for (Section section : sections) {
            allOk &= section.status() == Availability.OK;
            allUnavailable &= section.status() == Availability.UNAVAILABLE;
        }
        if (allOk) {
            return Availability.OK;
        }
        return allUnavailable ? Availability.UNAVAILABLE : Availability.PARTIAL;
    }
}
