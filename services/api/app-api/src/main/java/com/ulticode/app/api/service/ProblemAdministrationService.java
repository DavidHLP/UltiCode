package com.ulticode.app.api.service;

import com.ulticode.app.api.command.CreateProblemCommand;
import com.ulticode.app.api.command.DeleteProblemCommand;
import com.ulticode.app.api.command.PublishProblemCommand;
import com.ulticode.app.api.command.UpdateProblemCommand;
import com.ulticode.app.api.dto.ProblemAdminViewDTO;
import com.ulticode.common.rpc.RpcResult;

/**
 * App-owned administrative write provider for problems.
 *
 * <p>Listed in {@code docs/MICROSERVICE_MIGRATION_GUIDE.md} &sect;4.3
 * as one of {@code backend-app}'s Dubbo providers; per &sect;6.2 the
 * interface signature mirrors the migration guide example exactly.
 * The Admin BFF calls these to act on problem data while the
 * transaction stays inside App (per &sect;4.2 boundary ruling).
 *
 * <p>This interface is contract-only; no ServiceImpl lives in this
 * module. The provider implementation belongs to {@code backend-app}.
 */
public interface ProblemAdministrationService {

    /**
     * Create a new problem.
     *
     * @param command carries commandId, idempotency key, actor
     *                delegation, trace metadata, the slug / title /
     *                author and the optimistic-lock expected version
     * @return success with the freshly-created
     *         {@link ProblemAdminViewDTO}; failure codes as
     *         documented on
     *         {@code AppErrorCode}
     */
    RpcResult<ProblemAdminViewDTO> createProblem(CreateProblemCommand command);

    /**
     * Update an existing problem's editable fields.
     *
     * @param command carries commandId, idempotency key, actor
     *                delegation, trace metadata, the problem id,
     *                expected version, new title and the rationale
     * @return success with the post-update
     *         {@link ProblemAdminViewDTO}; failure with
     *         {@code VERSION_CONFLICT} on stale expected version or
     *         {@code CONTENT_NOT_FOUND} when the problem id is unknown
     */
    RpcResult<ProblemAdminViewDTO> updateProblem(UpdateProblemCommand command);

    /**
     * Publish or unpublish a problem.
     *
     * @param command carries commandId, idempotency key, actor
     *                delegation, trace metadata, the problem id,
     *                expected version and the boolean publish intent
     * @return success (no payload); failure codes as documented on
     *         {@link #updateProblem}
     */
    RpcResult<Void> publishProblem(PublishProblemCommand command);

    /**
     * Soft-delete a problem.
     *
     * @param command carries commandId, idempotency key, actor
     *                delegation, trace metadata, the problem id and
     *                the expected version
     * @return success (no payload); failure codes as documented on
     *         {@link #updateProblem}
     */
    RpcResult<Void> deleteProblem(DeleteProblemCommand command);
}