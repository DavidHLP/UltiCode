package com.ulticode.auth.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import java.io.Serializable;

/**
 * Auth-owned command for creating an account.
 *
 * <p>The password is a transient plaintext input at the RPC boundary. The
 * auth provider hashes it before persistence and never returns it in a DTO.
 */
public record CreateAccountCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String username,
        String email,
        String password,
        String role) implements Serializable, WriteCommand {

    public CreateAccountCommand {
        requireNonBlank(commandId, "commandId");
        requireMetadata(idempotency, actor, trace);
        requireNonBlank(username, "username");
        requireNonBlank(password, "password");
        requireNonBlank(role, "role");
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
