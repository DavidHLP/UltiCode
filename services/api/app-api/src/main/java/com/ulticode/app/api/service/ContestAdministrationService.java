package com.ulticode.app.api.service;

import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * App-owned administrative write provider for contest lifecycle
 * management.
 *
 * <p>Listed in {@code docs/MICROSERVICE_MIGRATION_GUIDE.md} &sect;4.3
 * as one of {@code backend-app}'s Dubbo providers. The Admin BFF calls
 * these to act on contest data while the transaction stays inside App
 * (per &sect;4.2 boundary ruling). Per &sect;6.3 "Admin 创建/发布比赛需
 * 当场返回结果" is a must-RPC scenario.
 *
 * <p>The five methods cover the contest row lifecycle only (create,
 * update, delete, start, end). Contest announcement management and
 * contest problem attachments are <b>not</b> on this contract:
 * announcements are content creation (not moderation &mdash;
 * {@link ContentModerationService} is moderation-only), so they go
 * through App's HTTP API. Problem attachments are likewise managed via
 * App HTTP API after the contest row exists.
 *
 * <p>Contest lifecycle transitions are state-machine driven (DRAFT →
 * UPCOMING → RUNNING → FINISHED / CANCELLED), not optimistic-lock
 * version columns: the Contest entity has no {@code @Version} field.
 * The {@code expectedVersion} on transition commands is an opaque
 * state-machine fence token (see individual command javadocs).
 *
 * <p>This interface is contract-only; no ServiceImpl lives in this
 * module. The provider implementation belongs to {@code backend-app}.
 */
public interface ContestAdministrationService {

    /**
     * Create a new contest.
     *
     * @param command carries commandId, idempotency, actor delegation,
     *                trace metadata, slug, title, creator, contest type,
     *                scoring mode, start time and duration
     * @return success with the freshly-created
     *         {@link ContestAdminViewDTO}; failure with
     *         {@code CONTENT_NOT_FOUND} when a referenced scoring rule
     *         id is unknown
     */
    RpcResult<ContestAdminViewDTO> createContest(CreateContestCommand command);

    /**
     * Partial-update a contest's editable fields. Nullable fields are
     * skipped by the provider (set-if-present semantics).
     *
     * @param command carries commandId, idempotency, actor, trace,
     *                contest id, expected-version fence token, and
     *                optional new title / start time / duration
     * @return success with the post-update
     *         {@link ContestAdminViewDTO}; failure with
     *         {@code CONTENT_STATE_CONFLICT} when the fence rejects
     *         the edit or {@code CONTENT_NOT_FOUND} when the id is
     *         unknown
     */
    RpcResult<ContestAdminViewDTO> updateContest(UpdateContestCommand command);

    /**
     * Soft-delete a contest. The contest must be in UPCOMING or
     * FINISHED.
     *
     * @return success (no payload); failure with
     *         {@code CONTENT_STATE_CONFLICT} when RUNNING, or
     *         {@code CONTENT_NOT_FOUND} when the id is unknown
     */
    RpcResult<Void> deleteContest(DeleteContestCommand command);

    /**
     * Transition UPCOMING → RUNNING. The contest must have at least
     * one attached problem.
     *
     * @return success with the post-transition
     *         {@link ContestAdminViewDTO}; failure codes as on
     *         {@link #updateContest}
     */
    RpcResult<ContestAdminViewDTO> startContest(StartContestCommand command);

    /**
     * Transition RUNNING → FINISHED.
     *
     * @return success with the post-transition
     *         {@link ContestAdminViewDTO}; failure codes as on
     *         {@link #updateContest}
     */
    RpcResult<ContestAdminViewDTO> endContest(EndContestCommand command);
}
