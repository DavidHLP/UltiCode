package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * Command to partial-update a contest's editable fields. Issued by
 * the Admin BFF against {@code backend-app}
 * {@code ContestAdministrationService.updateContest}.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 *
 * <p>Nullable fields ({@code title}, {@code startEpochMs},
 * {@code durationMinutes}) are skipped by the provider when null,
 * matching the set-if-present semantics of the existing
 * {@link UpdateProblemCommand} pattern.
 *
 * <p>{@code expectedVersion} is an <b>opaque state-machine fence token</b>,
 * not an optimistic-lock version column: the Contest entity has no
 * {@code @Version} column. The provider interprets this token as a
 * status-level fence &mdash; rejecting the command when the contest's
 * current status does not match the fence. The fence interpretation
 * may be relaxed for partial edits where the contest is in DRAFT
 * (any edit is legal in DRAFT, so the fence is effectively a no-op).
 */
public record UpdateContestCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String contestId,
        Long expectedVersion,
        String title,
        Long startEpochMs,
        Integer durationMinutes,
        String rationale) implements WriteCommand {

    public UpdateContestCommand {
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
                            + "fence token; may be no-op in DRAFT)");
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
