package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;


/** Owner command for removing one problem from a contest. */
public record RemoveContestProblemCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String contestId,
        Long problemId) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public RemoveContestProblemCommand {
        require(commandId, "commandId");
        require(contestId, "contestId");
        if (idempotency == null) {
            throw new IllegalArgumentException("idempotency is required");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        if (problemId == null) {
            throw new IllegalArgumentException("problemId is required");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
