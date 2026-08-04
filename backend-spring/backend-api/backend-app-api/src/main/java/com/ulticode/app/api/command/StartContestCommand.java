package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import java.io.Serializable;

/**
 * Command to transition a contest from UPCOMING to RUNNING. Issued by
 * the Admin BFF against {@code backend-app}
 * {@code ContestAdministrationService.startContest}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 *
 * <p>The contest must be in UPCOMING and have at least one attached
 * problem. The provider returns {@code CONTENT_STATE_CONFLICT} when
 * the transition is not legal from the current state and
 * {@code CONTENT_NOT_FOUND} when the id is unknown.
 *
 * <p>{@code expectedVersion} is an <b>opaque state-machine fence token</b>,
 * not an optimistic-lock version column: the Contest entity has no
 * {@code @Version} column. The provider rejects the command when the
 * contest's current status does not match the fence (i.e. must be
 * UPCOMING to start).
 */
public record StartContestCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String contestId,
        Long expectedVersion,
        String rationale) implements Serializable, WriteCommand {

    public StartContestCommand {
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
