package com.ulticode.app.api.command;

import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import java.io.Serializable;

import java.util.List;

/**
 * Command to batch-rejudge multiple submissions. Issued by the Admin
 * BFF against {@code backend-app}
 * {@code SubmissionAdministrationService.batchRejudge}.
 *
 * <p>Mirrors {@code BatchRejudgeRequest}: up to 50 submission IDs per
 * batch. The provider loops over per-submission
 * {@code RejudgePolicy.rejudgeFenced} CAS — there is no batch-level
 * fence; each submission is independently generation-fenced.
 *
 * @param submissionIds list of submission IDs (non-empty, max 50)
 * @param notifyUsers   whether to notify affected users after rejudge
 */
public record BatchRejudgeCommand(
        String commandId,
        IdMetadata idempotency,
        ActorDelegation actor,
        TraceMetadata trace,
        List<String> submissionIds,
        boolean notifyUsers) implements Serializable, WriteCommand {

    public BatchRejudgeCommand {
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException(
                    "commandId is required and must be a UUID String");
        }
        if (submissionIds == null || submissionIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "submissionIds must not be empty");
        }
        if (submissionIds.size() > 50) {
            throw new IllegalArgumentException(
                    "submissionIds size must not exceed 50");
        }
        if (submissionIds.stream().distinct().count() != submissionIds.size()) {
            throw new IllegalArgumentException(
                    "submissionIds must not contain duplicates");
        }
        if (submissionIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException(
                    "submissionIds must contain only non-blank IDs");
        }
        if (trace == null) {
            throw new IllegalArgumentException(
                    "trace is required (use TraceMetadata.EMPTY when unavailable)");
        }
        submissionIds = List.copyOf(submissionIds);
        if (idempotency == null) {
            throw new IllegalArgumentException(
                    "idempotency is required (use IdMetadata.mint())");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required");
        }
    }
}
