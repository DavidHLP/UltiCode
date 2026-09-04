package com.ulticode.auth.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/** Auth-owned role mutation with optimistic concurrency and idempotency. */
public record ChangeRoleCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String accountId,
        String role,
        Long expectedVersion,
        String rationale) implements WriteCommand {

    private static final long serialVersionUID = 1L;

    public ChangeRoleCommand {
        requireText(commandId, "commandId");
        requireText(accountId, "accountId");
        requireText(role, "role");
        if (idempotency == null || !idempotency.hasKey()) {
            throw new IllegalArgumentException("idempotency key is required");
        }
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()) {
            throw new IllegalArgumentException("authenticated actor is required");
        }
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
