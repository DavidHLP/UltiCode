package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;


/**
 * Command to update a problem list (partial update; null fields are
 * unchanged, mirroring {@code ProblemListAdminService.adminUpdateProblemList}).
 * Issued by the Admin BFF against {@code backend-app}
 * {@code ProblemListAdministrationService.updateProblemList}.
 */
public record UpdateProblemListCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String listId,
        String name,
        String description,
        Boolean isPublic,
        Boolean isFeatured,
        String bannerTag,
        String bannerIcon,
        String bannerTheme,
        Integer bannerOrder) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public UpdateProblemListCommand {
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
