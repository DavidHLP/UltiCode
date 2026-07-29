package com.ulticode.app.api.service;

import com.ulticode.app.api.command.BatchRejudgeCommand;
import com.ulticode.app.api.command.RejudgeCommand;
import com.ulticode.app.api.dto.BatchRejudgeResultDTO;
import com.ulticode.app.api.dto.RejudgeResultDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * App-owned administrative provider for submission lifecycle operations.
 *
 * <p>Listed in {@code docs/MICROSERVICE_MIGRATION_GUIDE.md} &sect;4.3
 * as one of {@code backend-app}'s Dubbo providers. Per &sect;6.3
 * "显式 rejudge command" is an RPC-suitable scenario. Submission read
 * paths (list, detail, statistics) are <b>not</b> on this contract:
 * they go through App's HTTP API or batch projections per the
 * "不应该 RPC" row in &sect;6.3.
 *
 * <p>The App provider owns the full rejudge state machine (generation
 * fence via {@code bumpGeneration} CAS, lease expiry, outbox
 * double-write) per the P3-OWNER-001-C {@code RejudgePolicy}; the
 * Admin BFF merely issues the command and receives the resulting
 * status. Fence enforcement is server-side &mdash; see
 * {@link RejudgeCommand} javadoc for rationale.
 *
 * <p>This interface is contract-only; no ServiceImpl lives in this
 * module. The provider implementation belongs to {@code backend-app}.
 */
public interface SubmissionAdministrationService {

    /**
     * Trigger an explicit rejudge of a submission. The provider applies
     * the full rejudge state machine atomically and returns the new
     * status.
     *
     * @param command carries commandId, idempotency, actor, trace,
     *                submission id and the notifyUser flag
     * @return success with the post-rejudge
     *         {@link RejudgeResultDTO}; failure with
     *         {@code CONTENT_NOT_FOUND} when the submission id is
     *         unknown
     */
    RpcResult<RejudgeResultDTO> rejudge(RejudgeCommand command);

    /**
     * Batch-rejudge multiple submissions (up to 50). The provider loops
     * over per-submission {@code RejudgePolicy.rejudgeFenced} CAS — there
     * is no batch-level fence; each submission is independently
     * generation-fenced.
     *
     * @param command carries commandId, idempotency, actor, trace,
     *                submission ids and the notifyUser flag
     * @return success with per-submission {@link BatchRejudgeResultDTO};
     *         never fails at the batch level (individual failures are
     *         captured per-submission in the result list)
     */
    RpcResult<BatchRejudgeResultDTO> batchRejudge(BatchRejudgeCommand command);
}
