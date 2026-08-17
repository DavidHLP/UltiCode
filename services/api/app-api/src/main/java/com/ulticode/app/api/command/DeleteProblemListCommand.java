package com.ulticode.app.api.command;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;


/**
 * Command to delete a problem list and its problem relations. Issued by
 * the Admin BFF against {@code backend-app}
 * {@code ProblemListAdministrationService.deleteProblemList}.
 */
public record DeleteProblemListCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String listId) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public DeleteProblemListCommand {
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
