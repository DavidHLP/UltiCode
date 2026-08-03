package com.ulticode.modules.admin.service;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.service.ContestAdministrationService;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.projection.AdminContestProjection;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * P4-CUTOVER-002: feature-flagged routing adapter for contest lifecycle.
 *
 * <p>When {@code app.features.contest-dubbo-cutover=false} (default),
 * delegates directly to {@link AdminContestMutationService}. When the flag
 * is {@code true}, writes go through the Dubbo
 * {@link ContestAdministrationService} Provider; read-back (returning
 * {@link AdminContestVO}) still uses the local service.
 *
 * <p>Mirrors {@link ProblemCutoverService} in pattern. Contest IDs are
 * String UUID, matching the Dubbo contract directly (no Long↔String
 * conversion needed unlike Problem).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestCutoverService {

    private final AdminContestMutationService mutationService;
    private final AdminContestProjection adminContestProjection;
    private final CurrentUserProvider currentUserProvider;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 3000, retries = 0, check = false)
    private ContestAdministrationService dubboProvider;

    @Value("${app.features.contest-dubbo-cutover:false}")
    private boolean dubboEnabled;

    @Transactional
    public AdminContestVO createContest(CreateContestDTO dto, String userId) {
        if (!dubboEnabled) {
            return mutationService.createContest(dto, userId);
        }
        String actorId = userId != null ? userId : "admin";
        long startEpochMs = dto.getStartTime() != null
                ? dto.getStartTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                : System.currentTimeMillis();
        int duration = dto.getDuration() != null ? dto.getDuration() : 0;
        RpcResult<ContestAdminViewDTO> result = dubboProvider.createContest(
                new CreateContestCommand(
                        UUID.randomUUID().toString(), IdMetadata.mint(),
                        new ActorDelegation("ADMIN", actorId, actorId, "cutover create"),
                        TraceMetadata.EMPTY,
                        dto.getSlug(), dto.getTitle(), actorId,
                        dto.getContestType(),
                        null, // scoringMode: provider applies DB default
                        dto.getScoringRuleId(), dto.getDescription(),
                        startEpochMs, duration));
        if (!result.success()) {
            throw mapError(result);
        }
        // Read-back: fetch full VO by the returned contestId
        return adminContestProjection.getContest(result.data().contestId());
    }

    @Transactional
    public AdminContestVO updateContest(String id, UpdateContestDTO dto) {
        if (!dubboEnabled) {
            return mutationService.updateContest(id, dto);
        }
        String actorId = safeActorId();
        Long startEpochMs = dto.getStartTime() != null
                ? dto.getStartTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                : null;
        RpcResult<ContestAdminViewDTO> result = dubboProvider.updateContest(
                new UpdateContestCommand(
                        UUID.randomUUID().toString(), IdMetadata.mint(),
                        new ActorDelegation("ADMIN", actorId, actorId, "cutover update"),
                        TraceMetadata.EMPTY,
                        id, 0L, dto.getTitle(), startEpochMs, dto.getDuration(),
                        "cutover update"));
        if (!result.success()) {
            throw mapError(result);
        }
        return adminContestProjection.getContest(id);
    }

    @Transactional
    public void deleteContest(String id) {
        if (!dubboEnabled) {
            mutationService.deleteContest(id);
            return;
        }
        String actorId = safeActorId();
        RpcResult<Void> result = dubboProvider.deleteContest(
                new DeleteContestCommand(
                        UUID.randomUUID().toString(), IdMetadata.mint(),
                        new ActorDelegation("ADMIN", actorId, actorId, "cutover delete"),
                        TraceMetadata.EMPTY,
                        id, 0L, "cutover delete"));
        if (!result.success()) {
            throw mapError(result);
        }
    }

    @Transactional
    public AdminContestVO startContest(String id) {
        if (!dubboEnabled) {
            return mutationService.startContest(id);
        }
        return doTransition(id, true);
    }

    @Transactional
    public AdminContestVO endContest(String id) {
        if (!dubboEnabled) {
            return mutationService.endContest(id);
        }
        return doTransition(id, false);
    }

    // ── helpers ────────────────────────────────────────────────

    private AdminContestVO doTransition(String id, boolean start) {
        String actorId = safeActorId();
        RpcResult<ContestAdminViewDTO> result;
        if (start) {
            result = dubboProvider.startContest(
                    new StartContestCommand(
                            UUID.randomUUID().toString(), IdMetadata.mint(),
                            new ActorDelegation("ADMIN", actorId, actorId, "cutover start"),
                            TraceMetadata.EMPTY, id, 0L, "cutover start"));
        } else {
            result = dubboProvider.endContest(
                    new EndContestCommand(
                            UUID.randomUUID().toString(), IdMetadata.mint(),
                            new ActorDelegation("ADMIN", actorId, actorId, "cutover end"),
                            TraceMetadata.EMPTY, id, 0L, "cutover end"));
        }
        if (!result.success()) {
            throw mapError(result);
        }
        return adminContestProjection.getContest(id);
    }

    private String safeActorId() {
        try {
            return currentUserProvider.getCurrentUserId();
        } catch (Exception e) {
            return "admin";
        }
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
