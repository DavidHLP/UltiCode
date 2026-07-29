package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContestAdministrationService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.service.ContestAdministrationDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Dubbo Provider implementation of {@link ContestAdministrationService} in {@code backend-app}.
 *
 * <p>Delegates to {@link ContestAdministrationDomainService} for canonical write-side domain logic.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ContestAdministrationProvider implements ContestAdministrationService {

    private final ContestAdministrationDomainService domainService;

    @Override
    public RpcResult<ContestAdminViewDTO> createContest(CreateContestCommand command) {
        log.info("ContestAdministrationProvider.createContest slug={} commandId={} actor={}",
                command.slug(), command.commandId(), command.actor() != null ? command.actor().actorId() : null);
        try {
            CreateContestDTO dto = new CreateContestDTO();
            dto.setSlug(command.slug());
            dto.setTitle(command.title());
            dto.setContestType(command.contestType());
            dto.setScoringRuleId(command.scoringRuleId());
            dto.setDescription(command.description());
            dto.setStartTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(command.startEpochMs()), ZoneOffset.UTC));
            dto.setDuration(command.durationMinutes());
            Contest entity = domainService.createContest(dto, command.creatorAccountId());
            return RpcResult.success(toAdminView(entity), command.trace() != null ? command.trace().traceId() : null);
        } catch (BusinessException e) {
            return toFailure(e, command.trace() != null ? command.trace().traceId() : null);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.createContest unexpected error", e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace() != null ? command.trace().traceId() : null);
        }
    }

    @Override
    public RpcResult<ContestAdminViewDTO> updateContest(UpdateContestCommand command) {
        log.info("ContestAdministrationProvider.updateContest id={} commandId={} actor={}",
                command.contestId(), command.commandId(), command.actor() != null ? command.actor().actorId() : null);
        try {
            UpdateContestDTO dto = new UpdateContestDTO();
            dto.setTitle(command.title());
            if (command.startEpochMs() != null) {
                dto.setStartTime(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(command.startEpochMs()), ZoneOffset.UTC));
            }
            dto.setDuration(command.durationMinutes());
            String actorId = command.actor() != null ? command.actor().actorId() : null;
            Contest entity = domainService.updateContest(command.contestId(), dto, actorId);
            return RpcResult.success(toAdminView(entity), command.trace() != null ? command.trace().traceId() : null);
        } catch (BusinessException e) {
            return toFailure(e, command.trace() != null ? command.trace().traceId() : null);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.updateContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace() != null ? command.trace().traceId() : null);
        }
    }

    @Override
    public RpcResult<Void> deleteContest(DeleteContestCommand command) {
        log.info("ContestAdministrationProvider.deleteContest id={} commandId={} actor={}",
                command.contestId(), command.commandId(), command.actor() != null ? command.actor().actorId() : null);
        try {
            String actorId = command.actor() != null ? command.actor().actorId() : null;
            domainService.deleteContest(command.contestId(), actorId);
            return RpcResult.success(command.trace() != null ? command.trace().traceId() : null);
        } catch (BusinessException e) {
            return toFailure(e, command.trace() != null ? command.trace().traceId() : null);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.deleteContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace() != null ? command.trace().traceId() : null);
        }
    }

    @Override
    public RpcResult<ContestAdminViewDTO> startContest(StartContestCommand command) {
        log.info("ContestAdministrationProvider.startContest id={} commandId={} actor={}",
                command.contestId(), command.commandId(), command.actor() != null ? command.actor().actorId() : null);
        try {
            String actorId = command.actor() != null ? command.actor().actorId() : null;
            Contest entity = domainService.startContest(command.contestId(), actorId);
            return RpcResult.success(toAdminView(entity), command.trace() != null ? command.trace().traceId() : null);
        } catch (BusinessException e) {
            return toFailure(e, command.trace() != null ? command.trace().traceId() : null);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.startContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace() != null ? command.trace().traceId() : null);
        }
    }

    @Override
    public RpcResult<ContestAdminViewDTO> endContest(EndContestCommand command) {
        log.info("ContestAdministrationProvider.endContest id={} commandId={} actor={}",
                command.contestId(), command.commandId(), command.actor() != null ? command.actor().actorId() : null);
        try {
            String actorId = command.actor() != null ? command.actor().actorId() : null;
            Contest entity = domainService.endContest(command.contestId(), actorId);
            return RpcResult.success(toAdminView(entity), command.trace() != null ? command.trace().traceId() : null);
        } catch (BusinessException e) {
            return toFailure(e, command.trace() != null ? command.trace().traceId() : null);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.endContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace() != null ? command.trace().traceId() : null);
        }
    }

    // ── helpers ────────────────────────────────────────────────

    private static ContestAdminViewDTO toAdminView(Contest entity) {
        return new ContestAdminViewDTO(entity.getId(), entity.getTitle(), entity.getStatus());
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        int code = e.getErrorCode() != null ? e.getErrorCode().code() : -1;
        AppErrorCode mapped;
        if (code == BaseErrorCode.NOT_FOUND.code() || code == 70001) {
            mapped = AppErrorCode.CONTENT_NOT_FOUND;
        } else if (code == BaseErrorCode.CONFLICT.code() || code == 40900) {
            mapped = AppErrorCode.CONTENT_STATE_CONFLICT;
        } else {
            mapped = AppErrorCode.UNEXPECTED_APP_STATE;
        }
        return RpcResult.failure(mapped, traceId);
    }
}
