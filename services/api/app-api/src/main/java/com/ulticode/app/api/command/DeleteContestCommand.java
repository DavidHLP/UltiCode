package com.ulticode.app.api.command;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * Command to soft-delete a contest. Issued by the Admin BFF against
 * {@code backend-app} {@code ContestAdministrationService.deleteContest}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 *
 * <p>The contest must be in a state that allows deletion (UPCOMING or
 * FINISHED). The provider returns {@code CONTENT_STATE_CONFLICT} when
 * the contest is RUNNING and {@code CONTENT_NOT_FOUND} when the id is
 * unknown.
 *
 * <p>{@code expectedVersion} is an <b>opaque state-machine fence token</b>,
 * not an optimistic-lock version column: the Contest entity has no
 * {@code @Version} column. The provider rejects the command when the
 * contest's current status does not match the fence.
 */
public record DeleteContestCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String contestId,
        Long expectedVersion,
        String rationale) implements WriteCommand {
    private static final long serialVersionUID = 1L;


    public DeleteContestCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (contestId == null || contestId.isBlank()) {
            throw new IllegalArgumentException(
                    "contestId is required and must be a UUID String");
        }
        if (expectedVersion == null) {
            throw new IllegalArgumentException(
                    "expectedVersion is required (opaque state-machine "
                            + "fence token)");
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
