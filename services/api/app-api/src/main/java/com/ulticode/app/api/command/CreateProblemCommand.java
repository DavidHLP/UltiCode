package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * Command to create a new problem. Issued by the Admin BFF against
 * {@code backend-app} {@code ProblemAdministrationService.createProblem}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 *
 * <p>{@link #authorAccountId()} is the App-side author attribution
 * (UUID String), not the Admin BFF caller &mdash; the Admin actor
 * stays on the {@link #actor()} delegation and the problem owner is a
 * separate field.
 */
public record CreateProblemCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String slug,
        String title,
        String authorAccountId) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public CreateProblemCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (authorAccountId == null || authorAccountId.isBlank()) {
            throw new IllegalArgumentException(
                    "authorAccountId is required and must be a UUID String");
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