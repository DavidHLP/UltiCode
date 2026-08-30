package com.ulticode.submission.api.command;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;

/**
 * Command to trigger an explicit rejudge of a submission. Issued by the
 * Admin BFF against {@code backend-submission}
 * {@link com.ulticode.submission.api.service.SubmissionAdministrationService#rejudge}.
 *
 * <p>Per migration guide &sect;6.3 "显式 rejudge command" is an
 * RPC-suitable scenario. The Submission provider owns the full rejudge state
 * machine (generation fence, lease expiry, and durable outboxes) in
 * backend-submission; the Admin BFF merely issues the command and receives
 * the resulting status.
 *
 * <p>Per migration guide &sect;6.2 the command carries
 * {@code commandId}, {@link IdMetadata}, {@link ActorDelegation} and
 * {@link TraceMetadata} via the {@link WriteCommand} base contract.
 *
 * <p>Fence enforcement is <b>server-side</b> via the owner's atomic
 * generation CAS: the current generation is read from the database inside
 * the transition, not supplied by the caller. The Admin BFF therefore needs
 * no stale generation read before issuing this command.
 *
 * <p>If a future RPC version needs caller-supplied optimistic concurrency
 * (e.g. "only rejudge if the submission hasn't been rejudged since I
 * last looked"), that is a &sect;6.4 additive change &mdash; add a new
 * field, don't try to back-fit it now.
 *
 * @param submissionId the target submission (UUID String)
 * @param notifyUser   retained for command compatibility; notification delivery is
 *                     not part of this owner transition
 */
public record RejudgeCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        String submissionId,
        boolean notifyUser) implements WriteCommand {
    private static final long serialVersionUID = 1L;

    public RejudgeCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (submissionId == null || submissionId.isBlank()) {
            throw new IllegalArgumentException(
                    "submissionId is required and must be a UUID String");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException(
                    "idempotency is required (use IdMetadata.mint() when "
                            + "no client token is available)");
        }
        if (trace == null) {
            throw new IllegalArgumentException(
                    "trace is required (use TraceMetadata.EMPTY when unavailable)");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }
}
