package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.CreateProblemCommand;
import com.ulticode.app.api.command.DeleteProblemCommand;
import com.ulticode.app.api.command.PublishProblemCommand;
import com.ulticode.app.api.command.UpdateProblemCommand;
import com.ulticode.app.api.dto.ProblemAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ProblemAdministrationService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.service.ProblemAdministrationDomainService;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dubbo Provider implementation of {@link ProblemAdministrationService} in {@code backend-app}.
 *
 * <p>Delegates to {@link ProblemAdministrationDomainService} for canonical write-side domain logic.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ProblemAdministrationProvider implements ProblemAdministrationService {

    private final ProblemAdministrationDomainService domainService;
    private final SearchDocumentChangedPublisher searchPublisher;
    private final AdminActorAuthorizer actorAuthorizer;

    @Override
    @Transactional
    public RpcResult<ProblemAdminViewDTO> createProblem(CreateProblemCommand command) {
        String traceId = traceId(command);
        RpcResult<ProblemAdminViewDTO> rejected = rejectIfUntrusted(
                command == null ? null : command.actor(), traceId);
        if (rejected != null) {
            return rejected;
        }
        log.info("ProblemAdministrationProvider.createProblem slug={} commandId={} actor={}",
                command.slug(), command.commandId(), command.actor() != null ? command.actor().actorId() : null);
        try {
            CreateProblemDTO dto = new CreateProblemDTO();
            dto.setSlug(command.slug());
            dto.setTitle(command.title());
            String actorId = command.actor() != null ? command.actor().actorId() : null;
            Problem entity = domainService.createProblem(dto, actorId);
            searchPublisher.publishProblem(entity, true);
            return RpcResult.success(toAdminView(entity.getId(), entity.getSlug(), entity.getTitle(),
                    entity, entity.getStatus()), command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ProblemAdministrationProvider.createProblem unexpected error", e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    @Transactional
    public RpcResult<ProblemAdminViewDTO> updateProblem(UpdateProblemCommand command) {
        String traceId = traceId(command);
        RpcResult<ProblemAdminViewDTO> rejected = rejectIfUntrusted(
                command == null ? null : command.actor(), traceId);
        if (rejected != null) {
            return rejected;
        }
        Long id = parseId(command.problemId(), command.trace().traceId());
        if (id == null) {
            return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, command.trace().traceId());
        }
        log.info("ProblemAdministrationProvider.updateProblem id={} commandId={} actor={}",
                id, command.commandId(), command.actor() != null ? command.actor().actorId() : null);
        try {
            UpdateProblemDTO dto = new UpdateProblemDTO();
            dto.setTitle(command.title());
            String actorId = command.actor() != null ? command.actor().actorId() : null;
            Problem entity = domainService.updateProblem(id, dto, actorId, command.expectedVersion());
            searchPublisher.publishProblem(entity, true);
            return RpcResult.success(toAdminView(entity.getId(), entity.getSlug(), entity.getTitle(),
                    entity, entity.getStatus()), command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ProblemAdministrationProvider.updateProblem unexpected error id={}", id, e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    @Transactional
    public RpcResult<Void> publishProblem(PublishProblemCommand command) {
        String traceId = traceId(command);
        RpcResult<Void> rejected = rejectIfUntrusted(
                command == null ? null : command.actor(), traceId);
        if (rejected != null) {
            return rejected;
        }
        Long id = parseId(command.problemId(), command.trace().traceId());
        if (id == null) {
            return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, command.trace().traceId());
        }
        log.info("ProblemAdministrationProvider.publishProblem id={} publish={} commandId={} actor={}",
                id, command.publish(), command.commandId(), command.actor() != null ? command.actor().actorId() : null);
        try {
            String actorId = command.actor() != null ? command.actor().actorId() : null;
            Problem entity;
            if (command.publish()) {
                entity = domainService.publishProblem(id, actorId, command.expectedVersion());
            } else {
                entity = domainService.unpublishProblem(id, actorId, command.expectedVersion());
            }
            // DefaultProblemSearchReadPort filters is_published=true; keep the
            // index coherent (UPSERT on publish, tombstone on unpublish).
            searchPublisher.publishProblem(entity, command.publish());
            return RpcResult.success(command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ProblemAdministrationProvider.publishProblem unexpected error id={}", id, e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    @Transactional
    public RpcResult<Void> deleteProblem(DeleteProblemCommand command) {
        String traceId = traceId(command);
        RpcResult<Void> rejected = rejectIfUntrusted(
                command == null ? null : command.actor(), traceId);
        if (rejected != null) {
            return rejected;
        }
        Long id = parseId(command.problemId(), command.trace().traceId());
        if (id == null) {
            return RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, command.trace().traceId());
        }
        log.info("ProblemAdministrationProvider.deleteProblem id={} commandId={} actor={}",
                id, command.commandId(), command.actor() != null ? command.actor().actorId() : null);
        try {
            String actorId = command.actor() != null ? command.actor().actorId() : null;
            Problem before = java.util.Optional
                    .ofNullable(domainService.findById(id))
                    .flatMap(o -> o)
                    .orElse(null);
            domainService.deleteProblem(id, actorId, command.expectedVersion());
            if (before != null) {
                searchPublisher.publishProblem(before, false);
            }
            return RpcResult.success(command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ProblemAdministrationProvider.deleteProblem unexpected error id={}", id, e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }
    private <T> RpcResult<T> rejectIfUntrusted(
            com.ulticode.common.command.ActorDelegation actor, String traceId) {
        return com.ulticode.app.security.TrustedAdminActor.isTrusted(
                        actorAuthorizer, actor, "problem administration")
                ? null
                : RpcResult.failure(AppErrorCode.FORBIDDEN, traceId);
    }

    private static String traceId(com.ulticode.common.command.WriteCommand command) {
        return command == null || command.trace() == null ? null : command.trace().traceId();
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
        int code = e.getErrorCode() != null ? e.getErrorCode().code() : -1;
        AppErrorCode mapped;
        if (code == BaseErrorCode.NOT_FOUND.code() || code == 30001) {
            mapped = AppErrorCode.CONTENT_NOT_FOUND;
        } else if (code == AppErrorCode.VERSION_CONFLICT.code()) {
            mapped = AppErrorCode.VERSION_CONFLICT;
        } else if (code == BaseErrorCode.CONFLICT.code() || code == 40900) {
            mapped = AppErrorCode.CONTENT_STATE_CONFLICT;
        } else {
            mapped = AppErrorCode.UNEXPECTED_APP_STATE;
        }
        return RpcResult.failure(mapped, traceId);
    }
}
