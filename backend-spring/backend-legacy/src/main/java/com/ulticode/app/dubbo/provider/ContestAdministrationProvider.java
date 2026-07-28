package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContestAdministrationService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.service.AdminContestMutationService;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * P4-CUTOVER-002: Dubbo Provider implementation of
 * {@link ContestAdministrationService}.
 *
 * <p>Delegates to {@link AdminContestMutationService} for the full contest
 * lifecycle flow (including owner-port writes, WebSocket push side-effects,
 * etc.). The Provider adapts the RPC contract to the internal domain calls.
 *
 * <p>Per &sect;6.2 the Provider opens its own local transaction (via the
 * mutation service's {@code @Transactional} methods) and never chains
 * another RPC (&sect;6.5 single-hop).
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ContestAdministrationProvider implements ContestAdministrationService {

    private final AdminContestMutationService mutationService;

    @Override
    public RpcResult<ContestAdminViewDTO> createContest(CreateContestCommand command) {
        log.info("ContestAdministrationProvider.createContest slug={} commandId={} actor={}",
                command.slug(), command.commandId(), command.actor().actorId());
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
            AdminContestVO vo = mutationService.createContest(dto, command.creatorAccountId());
            return RpcResult.success(toAdminView(vo), command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.createContest unexpected error", e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    public RpcResult<ContestAdminViewDTO> updateContest(UpdateContestCommand command) {
        log.info("ContestAdministrationProvider.updateContest id={} commandId={} actor={}",
                command.contestId(), command.commandId(), command.actor().actorId());
        try {
            UpdateContestDTO dto = new UpdateContestDTO();
            dto.setTitle(command.title());
            if (command.startEpochMs() != null) {
                dto.setStartTime(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(command.startEpochMs()), ZoneOffset.UTC));
            }
            dto.setDuration(command.durationMinutes());
            AdminContestVO vo = mutationService.updateContest(command.contestId(), dto);
            return RpcResult.success(toAdminView(vo), command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.updateContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    public RpcResult<Void> deleteContest(DeleteContestCommand command) {
        log.info("ContestAdministrationProvider.deleteContest id={} commandId={} actor={}",
                command.contestId(), command.commandId(), command.actor().actorId());
        try {
            mutationService.deleteContest(command.contestId());
            return RpcResult.success(command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.deleteContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    public RpcResult<ContestAdminViewDTO> startContest(StartContestCommand command) {
        log.info("ContestAdministrationProvider.startContest id={} commandId={} actor={}",
                command.contestId(), command.commandId(), command.actor().actorId());
        try {
            AdminContestVO vo = mutationService.startContest(command.contestId());
            return RpcResult.success(toAdminView(vo), command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.startContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    @Override
    public RpcResult<ContestAdminViewDTO> endContest(EndContestCommand command) {
        log.info("ContestAdministrationProvider.endContest id={} commandId={} actor={}",
                command.contestId(), command.commandId(), command.actor().actorId());
        try {
            AdminContestVO vo = mutationService.endContest(command.contestId());
            return RpcResult.success(toAdminView(vo), command.trace().traceId());
        } catch (BusinessException e) {
            return toFailure(e, command.trace().traceId());
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.endContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, command.trace().traceId());
        }
    }

    // ── helpers ────────────────────────────────────────────────

    private static ContestAdminViewDTO toAdminView(AdminContestVO vo) {
        return new ContestAdminViewDTO(vo.getId(), vo.getTitle(), vo.getStatus());
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        int code = e.getErrorCode().code();
        AppErrorCode mapped;
        if (code == ErrorCode.CONTEST_NOT_FOUND.code()
                || code == ErrorCode.PROBLEM_NOT_FOUND.code()) {
            mapped = AppErrorCode.CONTENT_NOT_FOUND;
        } else if (code == ErrorCode.CONFLICT.code()) {
            mapped = AppErrorCode.CONTENT_STATE_CONFLICT;
        } else {
            mapped = AppErrorCode.UNEXPECTED_APP_STATE;
        }
        return RpcResult.failure(mapped, traceId);
    }
}
