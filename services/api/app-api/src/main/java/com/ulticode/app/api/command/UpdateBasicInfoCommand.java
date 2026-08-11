package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

import java.io.Serializable;

/**
 * Command to update the basic info (name / description) of a problem
 * list. Issued by the Admin BFF against {@code backend-app}
 * {@code ProblemListAdministrationService.updateBasicInfo}.
 */
public record UpdateBasicInfoCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String listId,
        String name,
        String description) implements Serializable, WriteCommand {

    public UpdateBasicInfoCommand {
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
