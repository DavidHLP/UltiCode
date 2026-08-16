package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;


/**
 * Command to update the visibility (public / featured flags) of a
 * problem list. Issued by the Admin BFF against {@code backend-app}
 * {@code ProblemListAdministrationService.updateVisibility}.
 */
public record UpdateVisibilityCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String listId,
        Boolean isPublic,
        Boolean isFeatured) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public UpdateVisibilityCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId is required and must be a UUID String");
        }
        if (listId == null || listId.isBlank()) {
            throw new IllegalArgumentException("listId is required");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException("idempotency is required (use IdMetadata.mint())");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        if (trace == null) {
            trace = TraceMetadata.EMPTY;
        }
    }
}
