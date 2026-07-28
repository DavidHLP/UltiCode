package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.CreateProblemCommand;
import com.ulticode.app.api.command.PublishProblemCommand;
import com.ulticode.app.api.command.UpdateProblemCommand;
import com.ulticode.app.api.dto.ProblemAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ProblemAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * P4-CUTOVER-001: Dubbo Provider implementation of
 * {@link ProblemAdministrationService}.
 *
 * <p>Lives in backend-legacy (where the Problem domain logic resides) but is
 * registered with the {@code backend-app} Dubbo group. When backend-app is
 * physically separated, this class migrates to the backend-app module and
 * the Consumer stays in backend-legacy; the Dubbo calls start going over
 * the network instead of in-JVM.
 *
 * <p>Delegates to {@link ProblemService} for all domain operations — the
 * state-machine service that owns create/update/publish/unpublish. The
 * Provider adapts the RPC contract (String UUID IDs, WriteCommand shape)
 * to the internal domain calls (Long IDs, DTOs).
 *
 * <p>Per &sect;6.2 the Provider opens its own local transaction (via
 * {@code ProblemService}'s {@code @Transactional} methods) and never chains
 * another RPC to complete the command (&sect;6.5 single-hop).
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ProblemAdministrationProvider implements ProblemAdministrationService {

    private final ProblemService problemService;

    @Override
    public RpcResult<ProblemAdminViewDTO> createProblem(CreateProblemCommand command) {
        log.info("ProblemAdministrationProvider.createProblem slug={} commandId={} actor={}",
                command.slug(), command.commandId(), command.actor().actorId());
        try {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug(command.slug());
            dto.setTitle(command.title());
            var vo = problemService.createProblem(dto);
            Problem entity = problemService.findById(vo.getId()).orElse(null);
            return RpcResult.success(toAdminView(vo.getId(), vo.getSlug(), vo.getTitle(),
                    entity, vo.getStatus()), command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ProblemAdministrationProvider.createProblem unexpected error", e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    public RpcResult<ProblemAdminViewDTO> updateProblem(UpdateProblemCommand command) {
        Long id = parseId(command.problemId(), command.trace().traceId());
        if (id == null) {
            return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, command.trace().traceId());
        }
        log.info("ProblemAdministrationProvider.updateProblem id={} commandId={} actor={}",
                id, command.commandId(), command.actor().actorId());
        try {
            UpdateProblemDTO dto = new UpdateProblemDTO();
            dto.setTitle(command.title());
            var vo = problemService.updateProblem(id, dto);
            Problem entity = problemService.findById(vo.getId()).orElse(null);
            return RpcResult.success(toAdminView(vo.getId(), vo.getSlug(), vo.getTitle(),
                    entity, vo.getStatus()), command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ProblemAdministrationProvider.updateProblem unexpected error id={}", id, e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    public RpcResult<Void> publishProblem(PublishProblemCommand command) {
        Long id = parseId(command.problemId(), command.trace().traceId());
        if (id == null) {
            return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, command.trace().traceId());
        }
        log.info("ProblemAdministrationProvider.publishProblem id={} publish={} commandId={} actor={}",
                id, command.publish(), command.commandId(), command.actor().actorId());
        try {
            if (command.publish()) {
                problemService.publishProblem(id);
            } else {
                problemService.unpublishProblem(id);
            }
            return RpcResult.success(command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ProblemAdministrationProvider.publishProblem unexpected error id={}", id, e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    // ── helpers ────────────────────────────────────────────────

    private Long parseId(String idStr, String traceId) {
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            log.warn("ProblemAdministrationProvider: invalid id '{}' (traceId={})", idStr, traceId);
            return null;
        }
    }

    private static ProblemAdminViewDTO toAdminView(Long id, String slug, String title,
                                                    Problem entity, String status) {
        long version = (entity != null && entity.getVersion() != null)
                ? entity.getVersion().longValue() : 0L;
        return new ProblemAdminViewDTO(
                id != null ? String.valueOf(id) : slug,
                slug,
                title,
                version,
                status != null ? status : "unknown");
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        int code = e.getErrorCode().code();
        AppErrorCode mapped;
        if (code == ErrorCode.PROBLEM_NOT_FOUND.code()) {
            mapped = AppErrorCode.CONTENT_NOT_FOUND;
        } else if (code == ErrorCode.CONFLICT.code()) {
            mapped = AppErrorCode.CONTENT_STATE_CONFLICT;
        } else {
            mapped = AppErrorCode.UNEXPECTED_APP_STATE;
        }
        return RpcResult.failure(mapped, traceId);
    }
}
