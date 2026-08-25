package com.ulticode.modules.admin.service;

import com.ulticode.common.command.ActorDelegation;
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
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.problem.AdminProblemMapper;
import com.ulticode.modules.admin.dto.problem.CreateProblemDTO;
import com.ulticode.modules.admin.dto.problem.ProblemAdminVO;
import com.ulticode.modules.admin.dto.problem.UpdateProblemDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.UUID;
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
        String actorId = currentActorId();
        RpcResult<com.ulticode.app.api.dto.ProblemAdminViewDTO> result = dubboProvider.createProblem(
                new CreateProblemCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new ActorDelegation("ADMIN", actorId, actorId, "cutover create"),
                        currentTrace(),
                        createDTO.getSlug(),
                        createDTO.getTitle(),
                        actorId));
        if (!result.success()) {
            throw mapError(result);
        }
        ProblemAdminRowDTO row = problemReadPort.findBySlug(createDTO.getSlug());
        return mapper.toAdminVO(row);
    }

    public ProblemAdminVO updateProblem(Long id, UpdateProblemDTO updateDTO) {
        String idStr = String.valueOf(id);
        String actorId = currentActorId();
        ProblemAdminRowDTO current = requireProblemWithVersion(id);
        RpcResult<com.ulticode.app.api.dto.ProblemAdminViewDTO> result = dubboProvider.updateProblem(
                new UpdateProblemCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new ActorDelegation("ADMIN", actorId, actorId, "cutover update"),
                        currentTrace(),
                        idStr,
                        current.version(),
                        updateDTO.getTitle(),
                        "cutover update"));
        if (!result.success()) {
            throw mapError(result);
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
        String actorId = currentActorId();
        ProblemAdminRowDTO current = requireProblemWithVersion(id);
        RpcResult<Void> result = dubboProvider.deleteProblem(
                new DeleteProblemCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new ActorDelegation("ADMIN", actorId, actorId, "cutover delete"),
                        currentTrace(),
                        idStr,
                        current.version(),
                        "cutover delete"));
        if (!result.success()) {
            throw mapError(result);
        }
    }

    // ── helpers ────────────────────────────────────────────────

    private ProblemAdminVO doPublish(Long id, boolean publish) {
        String idStr = String.valueOf(id);
        String actorId = currentActorId();
        ProblemAdminRowDTO current = requireProblemWithVersion(id);
        RpcResult<Void> result = dubboProvider.publishProblem(
                new PublishProblemCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new ActorDelegation("ADMIN", actorId, actorId, publish ? "cutover publish" : "cutover unpublish"),
                        currentTrace(),
                        idStr,
                        current.version(),
                        publish,
                        publish ? "cutover publish" : "cutover unpublish"));
        if (!result.success()) {
            throw mapError(result);
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

    private String currentActorId() {
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        return actorId;
    }

    private static TraceMetadata currentTrace() {
        String reqId = TraceIdUtil.current();
        if (reqId == null || reqId.isBlank()) {
            reqId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(reqId, null, null, null);
    }

    private static BusinessException mapError(RpcResult<?> result) {
        var err = result.error();
        if (err == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "RPC failed without error payload");
        }
        int code = err.code();
        if (code == 40401) {
            return new BusinessException(AdminErrorCode.PROBLEM_NOT_FOUND, err.message());
        }
        if (code == 40901 || code == 40902) {
            return new BusinessException(AdminErrorCode.CONFLICT, err.message());
        }
        return new BusinessException(AdminErrorCode.UNKNOWN_ERROR, err.message());
    }
}
