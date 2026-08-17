package com.ulticode.app.api.command;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

import java.util.List;
import java.io.Serializable;

/**
 * Command to fully replace the problem set of a problem list. Issued by
 * the Admin BFF against {@code backend-app}
 * {@code ProblemListAdministrationService.replaceListProblems}.
 *
 * @param problems ordered problem entries; an empty list clears the list
 */
public record ReplaceListProblemsCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String listId,
        List<ProblemEntry> problems) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public ReplaceListProblemsCommand {
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
        if (problems == null) {
            problems = List.of();
        } else {
            problems = List.copyOf(problems);
        }
    }

    /** One problem in the replacement set, with its sort order. */
    public record ProblemEntry(Long problemId, Integer sortOrder) implements Serializable {
        private static final long serialVersionUID = 1L;
}
}
