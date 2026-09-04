package com.ulticode.auth.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

import java.time.OffsetDateTime;
import java.util.Locale;
/** Auth-owned delta mutation for one direct permission assignment. Expiry
 * values carry an explicit offset and are normalized to UTC by Auth. */
public record PermissionMutationCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String accountId,
        Operation operation,
        String action,
        String resource,
        OffsetDateTime expiresAt,
        Long expectedVersion,
        String rationale) implements WriteCommand {

    private static final long serialVersionUID = 1L;

    public enum Operation {
        GRANT,
        REVOKE
    }

    public PermissionMutationCommand {
        requireText(commandId, "commandId");
        requireText(accountId, "accountId");
        requireText(action, "action");
        requireText(resource, "resource");
        action = action.trim().toUpperCase(Locale.ROOT);
        resource = resource.trim().toUpperCase(Locale.ROOT);
        if (idempotency == null || !idempotency.hasKey()) {
            throw new IllegalArgumentException("idempotency key is required");
        }
        if (actor == null || actor.actorId() == null || actor.actorId().isBlank()) {
            throw new IllegalArgumentException("authenticated actor is required");
        }
        if (operation == null) {
            throw new IllegalArgumentException("operation is required");
        }
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
        if (operation == Operation.REVOKE && expiresAt != null) {
            throw new IllegalArgumentException("revoke must not carry expiresAt");
        }
    }

    public String actorId() {
        return actor.actorId();
    }

    public String idempotencyKey() {
        return idempotency.idempotencyKey();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
