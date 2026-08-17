package com.ulticode.app.api.command;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.app.api.dto.ContestProblemInputDTO;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;


/** Owner command for adding one problem to a contest. */
public record AddContestProblemCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String contestId,
        ContestProblemInputDTO problem) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public AddContestProblemCommand {
        require(commandId, "commandId");
        require(contestId, "contestId");
        if (idempotency == null) {
            throw new IllegalArgumentException("idempotency is required");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
        if (problem == null) {
            throw new IllegalArgumentException("problem is required");
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
