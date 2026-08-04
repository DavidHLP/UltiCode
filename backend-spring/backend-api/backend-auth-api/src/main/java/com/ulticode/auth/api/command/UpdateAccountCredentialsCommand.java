package com.ulticode.auth.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import java.io.Serializable;

/** Auth-owned command for changing an account's username and email. */
public record UpdateAccountCredentialsCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String accountId,
        String username,
        String email) implements Serializable, WriteCommand {

    public UpdateAccountCredentialsCommand {
        requireNonBlank(commandId, "commandId");
        requireMetadata(idempotency, actor, trace);
        requireNonBlank(accountId, "accountId");
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");
    }

    private static void requireMetadata(IdMetadata idempotency,
                                        ActorDelegation actor,
                                        TraceMetadata trace) {
        if (idempotency == null || !idempotency.hasKey()) {
            throw new IllegalArgumentException(
                    "idempotency with a non-blank key is required");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        if (trace == null) {
            throw new IllegalArgumentException("trace is required");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required and must be non-blank");
        }
    }
}
