package com.ulticode.auth.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

import java.util.Set;

/**
 * Command to mutate an account's authoritative role / permission
 * assignment. Issued by the Admin BFF against {@code backend-auth}
 * {@code AccountAdministrationService.changeAuthorization}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 *
 * <p>{@link #expectedVersion()} is the optimistic-lock token read
 * from the prior
 * {@link com.ulticode.auth.api.dto.AuthorizationSnapshotDTO}; the
 * provider returns
 * {@code AUTHORIZATION_VERSION_CONFLICT} when it does not match.
 *
 * <p>{@link #permissions()} is captured as an immutable
 * {@link Set#copyOf(java.util.Collection)} snapshot at construction
 * time so subsequent caller mutations cannot leak into the command
 * after the wire has serialised it. Empty elements are rejected so
 * the contract's wire shape never carries a permission name that
 * round-trips to {@code null}.
 */
public record ChangeAuthorizationCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String accountId,
        Long expectedVersion,
        String role,
        Set<String> permissions,
        String rationale) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public ChangeAuthorizationCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException(
                    "accountId is required and must be a UUID String");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException(
                    "idempotency is required (use IdMetadata.mint() when "
                            + "no client token is available)");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        if (expectedVersion == null) {
            throw new IllegalArgumentException(
                    "expectedVersion is required for optimistic locking");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        // permissions may legitimately be empty (role-only grants);
        // null is rejected so the contract is unambiguous, and each
        // element must be a non-blank permission name.
        if (permissions == null) {
            throw new IllegalArgumentException("permissions must be non-null");
        }
        for (String p : permissions) {
            if (p == null || p.isBlank()) {
                throw new IllegalArgumentException(
                        "permissions must not contain null or blank elements");
            }
        }
        // copyOf preserves the caller-supplied iteration order; the
        // resulting Set is unmodifiable so post-construction mutation
        // attempts will throw UnsupportedOperationException.
        permissions = Set.copyOf(permissions);
    }
}