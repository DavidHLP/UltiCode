package com.ulticode.submission.api.service;

import com.ulticode.submission.api.command.BatchRejudgeCommand;
import com.ulticode.submission.api.command.RejudgeCommand;
import com.ulticode.submission.api.dto.BatchRejudgeResultDTO;
import com.ulticode.submission.api.dto.RejudgeResultDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * Submission-owned administrative provider for submission lifecycle operations.
 *
 * <p>Listed in {@code docs/architecture/modules.md} as one of
 * {@code backend-submission}'s Dubbo providers. An explicit rejudge command is
 * an RPC-suitable scenario per {@code docs/architecture/data-flow.md}.
 * Submission read paths (list, detail, statistics) are not on this contract:
 * they use the bounded owner-facts projections described there.
 *
 * <p>The Submission provider owns the full rejudge state machine (generation
 * fence via generation-checked SQL CAS, lease expiry, and durable judge/result
 * outbox writes). The Admin BFF merely issues the command and receives the
 * resulting status. Fence enforcement is server-side; see
 * {@link com.ulticode.submission.api.command.RejudgeCommand} for the command
 * contract.
 *
 * <p>This interface is contract-only; its sole provider is
 * {@code backend-submission}. No App compatibility rejudge provider remains.
 */
public interface SubmissionAdministrationService {

    /**
     * Trigger an explicit rejudge of a submission. The provider applies
     * the full rejudge state machine atomically and returns the new
     * status.
     *
     * @param command carries commandId, idempotency, actor, trace,
     *                submission id and the compatibility notifyUser flag
     * @return success with the post-rejudge
     *         {@link RejudgeResultDTO}; failure with
     *         {@code CONTENT_NOT_FOUND} when the submission id is
     *         unknown
     */
    RpcResult<RejudgeResultDTO> rejudge(RejudgeCommand command);

    /**
     * Batch-rejudge multiple submissions (up to 50). The provider delegates
     * each item to the Submission owner's {@code SubmissionRejudgeService}
     * transition; there is no batch-level fence, so each submission is
     * independently generation-fenced.
     *
     * @param command carries commandId, idempotency, actor, trace,
     *                submission ids and the notifyUser flag
     * @return success with per-submission {@link BatchRejudgeResultDTO};
     *         never fails at the batch level (individual failures are
     *         captured per-submission in the result list)
     */
    RpcResult<BatchRejudgeResultDTO> batchRejudge(BatchRejudgeCommand command);
}
