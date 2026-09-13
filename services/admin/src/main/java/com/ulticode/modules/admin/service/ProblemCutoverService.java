package com.ulticode.modules.admin.service;

import com.ulticode.app.api.command.CreateProblemCommand;
import com.ulticode.app.api.command.DeleteProblemCommand;
import com.ulticode.app.api.command.PublishProblemCommand;
import com.ulticode.app.api.command.UpdateProblemCommand;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.service.ProblemAdministrationService;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.problem.AdminProblemMapper;
import com.ulticode.modules.admin.dto.problem.CreateProblemDTO;
import com.ulticode.modules.admin.dto.problem.ProblemAdminVO;
import com.ulticode.modules.admin.dto.problem.UpdateProblemDTO;
import com.ulticode.modules.admin.write.AdminOwnerErrorMapper;
import com.ulticode.modules.admin.write.AdminWriteEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import com.ulticode.common.rpc.RpcPolicy;

/**
 * P4-CUTOVER-001: problem lifecycle write seam (create/update/publish/
 * unpublish/delete) backed entirely by the {@code backend-app}
 * {@link ProblemAdministrationService} Dubbo Provider.
 *
 * <p>ADMIN-003: the seam is now remote-only — the App-private
 * {@code ProblemService} fallback is gone, so the admin module carries no
 * problem service/DTO/entity imports. The write transaction and state
 * machine live in App; the read-back after a successful write re-fetches
 * the full row through the public {@link ProblemAdminReadPort} and shapes
 * the admin-owned {@link ProblemAdminVO}, so the HTTP response shape is
 * unchanged.
 *
 * <p>Error mapping mirrors the pre-cutover semantics: RPC failures map to
 * the closest admin error code (NOT_FOUND / CONFLICT / UNKNOWN_ERROR).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemCutoverService {

    private final ProblemAdminReadPort problemReadPort;
    private final AdminProblemMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ProblemAdministrationService dubboProvider;

    public ProblemAdminVO createProblem(CreateProblemDTO createDTO) {
        String actorId = currentUserProvider.getCurrentUserId();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "problem-create", null, actorId, currentUserProvider, "cutover create");
        RpcResult<com.ulticode.app.api.dto.ProblemAdminViewDTO> result = dubboProvider.createProblem(
                new CreateProblemCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                        createDTO.getSlug(),
                        createDTO.getTitle(),
                        actorId));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.PROBLEM, result);
        }
        ProblemAdminRowDTO row = problemReadPort.findBySlug(createDTO.getSlug());
        return mapper.toAdminVO(row);
    }

    public ProblemAdminVO updateProblem(Long id, UpdateProblemDTO updateDTO) {
        String idStr = String.valueOf(id);
        String actorId = currentUserProvider.getCurrentUserId();
        ProblemAdminRowDTO current = requireProblemWithVersion(id);
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "problem-update", null, actorId, currentUserProvider, "cutover update");
        RpcResult<com.ulticode.app.api.dto.ProblemAdminViewDTO> result = dubboProvider.updateProblem(
                new UpdateProblemCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                        idStr,
                        current.version(),
                        updateDTO.getTitle(),
                        "cutover update"));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.PROBLEM, result);
        }
        return mapper.toAdminVO(problemReadPort.findProblem(id));
    }

    public ProblemAdminVO publishProblem(Long id) {
        return doPublish(id, true);
    }

    public ProblemAdminVO unpublishProblem(Long id) {
        return doPublish(id, false);
    }

    public void deleteProblem(Long id) {
        String idStr = String.valueOf(id);
        String actorId = currentUserProvider.getCurrentUserId();
        ProblemAdminRowDTO current = requireProblemWithVersion(id);
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "problem-delete", null, actorId, currentUserProvider, "cutover delete");
        RpcResult<Void> result = dubboProvider.deleteProblem(
                new DeleteProblemCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                        idStr,
                        current.version(),
                        "cutover delete"));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.PROBLEM, result);
        }
    }

    // ── helpers ────────────────────────────────────────────────

    private ProblemAdminVO doPublish(Long id, boolean publish) {
        String idStr = String.valueOf(id);
        String actorId = currentUserProvider.getCurrentUserId();
        ProblemAdminRowDTO current = requireProblemWithVersion(id);
        String rationale = publish ? "cutover publish" : "cutover unpublish";
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                publish ? "problem-publish" : "problem-unpublish",
                null, actorId, currentUserProvider, rationale);
        RpcResult<Void> result = dubboProvider.publishProblem(
                new PublishProblemCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                        idStr,
                        current.version(),
                        publish,
                        publish ? "cutover publish" : "cutover unpublish"));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.PROBLEM, result);
        }
        return mapper.toAdminVO(problemReadPort.findProblem(id));
    }

    private ProblemAdminRowDTO requireProblemWithVersion(Long id) {
        ProblemAdminRowDTO row = problemReadPort.findProblem(id);
        if (row == null) {
            throw new BusinessException(AdminErrorCode.PROBLEM_NOT_FOUND, "Problem not found");
        }
        if (row.version() == null) {
            throw new BusinessException(AdminErrorCode.CONFLICT, "Problem version is unavailable");
        }
        return row;
    }

}
