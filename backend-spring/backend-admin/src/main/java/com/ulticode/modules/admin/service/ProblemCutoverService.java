package com.ulticode.modules.admin.service;

import com.ulticode.app.api.command.CreateProblemCommand;
import com.ulticode.app.api.command.PublishProblemCommand;
import com.ulticode.app.api.command.UpdateProblemCommand;
import com.ulticode.app.api.dto.ProblemAdminViewDTO;
import com.ulticode.app.api.service.ProblemAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P4-CUTOVER-001: feature-flagged routing adapter for problem
 * create/update/publish/unpublish.
 *
 * <p>When {@code app.features.problem-dubbo-cutover=false} (default), the
 * adapter delegates directly to the local {@link ProblemService} — zero
 * behavioral change from Phase 3. When the flag is flipped to {@code true},
 * the write goes through the Dubbo {@link ProblemAdministrationService}
 * Provider; the read-back (returning {@link ProblemVO}) still uses the local
 * {@link ProblemService} so the HTTP response shape is unchanged.
 *
 * <p>Per &sect;Phase-4 "先只读后写": the write path is cutover first; read
 * paths (list, detail, export) stay local until a later cutover wave.
 *
 * <p>Rationale for read-back after Dubbo write: the Dubbo contract returns
 * a narrow {@link ProblemAdminViewDTO} (id/slug/title/version/status), but
 * the HTTP endpoint returns a deep {@link ProblemVO}. After a successful
 * Dubbo write, the adapter re-fetches the full VO from the local
 * {@link ProblemService} so the admin UI sees no difference.
 *
 * <p>Per &sect;6.4: write calls use the global consumer default
 * (timeout=3000ms, retries=0) from the YAML configuration. This is a
 * write reference, so no query override is needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemCutoverService {

    private final ProblemService problemService;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 3000, retries = 0, check = false)
    private ProblemAdministrationService dubboProvider;

    @Value("${app.features.problem-dubbo-cutover:false}")
    private boolean dubboEnabled;

    @Transactional
    public ProblemVO createProblem(CreateProblemDTO createDTO) {
        if (!dubboEnabled) {
            return problemService.createProblem(createDTO);
        }
        // Dubbo path: write via Provider, read-back via local service
        String actorId = "admin"; // @CurrentUser provides the real id at controller layer
        RpcResult<ProblemAdminViewDTO> result = dubboProvider.createProblem(
                new CreateProblemCommand(
                        java.util.UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new com.ulticode.app.api.command.ActorDelegation(
                                "ADMIN", actorId, actorId, "cutover create"),
                        TraceMetadata.EMPTY,
                        createDTO.getSlug(),
                        createDTO.getTitle(),
                        actorId));
        if (!result.success()) {
            throw mapError(result);
        }
        return fetchVoBySlug(createDTO.getSlug());
    }

    @Transactional
    public ProblemVO updateProblem(Long id, UpdateProblemDTO updateDTO) {
        if (!dubboEnabled) {
            return problemService.updateProblem(id, updateDTO);
        }
        String idStr = String.valueOf(id);
        String actorId = "admin";
        RpcResult<ProblemAdminViewDTO> result = dubboProvider.updateProblem(
                new UpdateProblemCommand(
                        java.util.UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new com.ulticode.app.api.command.ActorDelegation(
                                "ADMIN", actorId, actorId, "cutover update"),
                        TraceMetadata.EMPTY,
                        idStr,
                        0L, // expectedVersion: no optimistic-lock check on problem (entity uses Integer version)
                        updateDTO.getTitle(),
                        "cutover update"));
        if (!result.success()) {
            throw mapError(result);
        }
        return problemService.getProblemById(id);
    }

    @Transactional
    public ProblemVO publishProblem(Long id) {
        if (!dubboEnabled) {
            return problemService.publishProblem(id);
        }
        return doPublish(id, true);
    }

    @Transactional
    public ProblemVO unpublishProblem(Long id) {
        if (!dubboEnabled) {
            return problemService.unpublishProblem(id);
        }
        return doPublish(id, false);
    }

    @Transactional
    public void deleteProblem(Long id) {
        if (!dubboEnabled) {
            problemService.deleteProblem(id);
            return;
        }
        // Delete is not on the current Dubbo contract (PublishProblemCommand
        // handles publish/unpublish only). Route through local service for
        // now; delete will be added to the contract in P4-CUTOVER-002.
        problemService.deleteProblem(id);
    }

    // ── helpers ────────────────────────────────────────────────

    private ProblemVO doPublish(Long id, boolean publish) {
        String idStr = String.valueOf(id);
        String actorId = "admin";
        RpcResult<Void> result = dubboProvider.publishProblem(
                new PublishProblemCommand(
                        java.util.UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new com.ulticode.app.api.command.ActorDelegation(
                                "ADMIN", actorId, actorId, publish ? "cutover publish" : "cutover unpublish"),
                        TraceMetadata.EMPTY,
                        idStr,
                        0L,
                        publish,
                        publish ? "cutover publish" : "cutover unpublish"));
        if (!result.success()) {
            throw mapError(result);
        }
        return problemService.getProblemById(id);
    }

    private ProblemVO fetchVoBySlug(String slug) {
        return problemService.getProblemBySlug(slug);
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
