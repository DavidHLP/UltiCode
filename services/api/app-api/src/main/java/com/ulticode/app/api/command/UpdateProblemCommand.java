package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * Command to update an existing problem. Issued by the Admin BFF
 * against {@code backend-app}
 * {@code ProblemAdministrationService.updateProblem}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 *
 * <p>{@link #expectedVersion()} is the optimistic-lock token read from
 * the prior {@link com.ulticode.app.api.dto.ProblemAdminViewDTO}; the
 * provider returns {@code VERSION_CONFLICT} when it does not match.
 */
public record UpdateProblemCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String problemId,
        Long expectedVersion,
        String title,
        String rationale) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public UpdateProblemCommand {
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
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
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