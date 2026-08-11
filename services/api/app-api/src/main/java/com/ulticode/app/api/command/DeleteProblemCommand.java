package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

import java.io.Serializable;

/**
 * Command to soft-delete a problem. Issued by the Admin BFF against
 * {@code backend-app} {@code ProblemAdministrationService.deleteProblem}.
 *
 * <p>Per migration guide &sect;6.2 the command carries {@code commandId},
 * {@link IdMetadata}, {@link ActorDelegation} and {@link TraceMetadata} via
 * the {@link WriteCommand} base contract.
 */
public record DeleteProblemCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String problemId,
        Long expectedVersion,
        String rationale) implements Serializable, WriteCommand {

    public DeleteProblemCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId is required must be a UUID String");
        }
        if (problemId == null || problemId.isBlank()) {
            throw new IllegalArgumentException("problemId is required must be a UUID String");
        }
        if (expectedVersion == null) {
            throw new IllegalArgumentException("expectedVersion required optimistic locking");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException("idempotency is required (use IdMetadata.mint() when "
                    + "no client token is available)");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }
}
