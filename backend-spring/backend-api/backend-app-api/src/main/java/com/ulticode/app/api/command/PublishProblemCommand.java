package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * Command to publish / unpublish a problem. Issued by the Admin BFF
 * against {@code backend-app}
 * {@code ProblemAdministrationService.publishProblem}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 */
public record PublishProblemCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String problemId,
        Long expectedVersion,
        boolean publish,
        String rationale) implements WriteCommand {

    public PublishProblemCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (problemId == null || problemId.isBlank()) {
            throw new IllegalArgumentException(
                    "problemId is required and must be a UUID String");
        }
        if (expectedVersion == null) {
            throw new IllegalArgumentException(
                    "expectedVersion is required for optimistic locking");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException(
                    "idempotency is required (use IdMetadata.mint() when "
                            + "no client token is available)");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }
}