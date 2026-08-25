package com.ulticode.modules.admin.service;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.AddContestProblemCommand;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.RemoveContestProblemCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.dto.ContestProblemAdminDTO;
import com.ulticode.app.api.dto.ContestProblemInputDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContestAdministrationService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.AddContestProblemDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.dto.CreateContestDTO;
import com.ulticode.modules.admin.dto.UpdateContestDTO;
import com.ulticode.modules.admin.projection.AdminContestProjection;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.ulticode.common.rpc.RpcPolicy;

/** Admin-side adapter for the App-owned contest write contract. */
@Service
@RequiredArgsConstructor
public class ContestCutoverService {

    private final AdminContestProjection adminContestProjection;
    private final CurrentUserProvider currentUserProvider;

    @Value("${app.features.contest-dubbo-cutover:false}")
    private boolean dubboEnabled;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ContestAdministrationService dubboProvider;

    public AdminContestVO createContest(CreateContestDTO dto, String userId) {
        return createContest(dto, userId, null);
    }

    public AdminContestVO createContest(CreateContestDTO dto, String userId, String idempotencyKey) {
        ensureDubboEnabled();
        String actorId = requireActor(userId);
        IdMetadata idempotency = idempotency(idempotencyKey);
        RpcResult<ContestAdminViewDTO> result = dubboProvider.createContest(
                new CreateContestCommand(
                        commandId("create", idempotency), idempotency,
                        new ActorDelegation("ADMIN", actorId, actorId, "contest create"),
                        trace(), dto.getSlug(), dto.getTitle(), actorId,
                        contestType(dto.getContestType()), "SCORE", dto.getScoringRuleId(), dto.getDescription(),
                        epochMs(dto.getStartTime()), dto.getDuration(), dto.getMaxParticipants(),
                        dto.getIsPremium(), dto.getIsPublished(), dto.getProblemIds(),
                        toProblemInputs(dto.getProblems())));
        if (!result.success()) {
            throw mapError(result);
        }
        return adminContestProjection.getContest(result.data().contestId());
    }

    public AdminContestVO updateContest(String id, UpdateContestDTO dto) {
        return updateContest(id, dto, null);
    }

    public AdminContestVO updateContest(String id, UpdateContestDTO dto, String idempotencyKey) {
        ensureDubboEnabled();
        String actorId = currentActor();
        IdMetadata idempotency = idempotency(idempotencyKey);
        RpcResult<ContestAdminViewDTO> result = dubboProvider.updateContest(
                new UpdateContestCommand(
                        commandId("update", idempotency), idempotency,
                        new ActorDelegation("ADMIN", actorId, actorId, "contest update"),
                        trace(), id, 0L, dto.getTitle(), epochMsOrNull(dto.getStartTime()),
                        dto.getDuration(), "contest update", dto.getDescription(),
                        dto.getMaxParticipants(), dto.getIsPremium(), dto.getIsPublished(),
                        dto.getSlug(), dto.getContestType(), dto.getScoringRuleId(),
                        dto.getProblemIds(), toProblemInputs(dto.getProblems())));
        if (!result.success()) {
            throw mapError(result);
        }
        return adminContestProjection.getContest(id);
    }

    public void deleteContest(String id) {
        deleteContest(id, null);
    }

    public void deleteContest(String id, String idempotencyKey) {
        ensureDubboEnabled();
        String actorId = currentActor();
        IdMetadata idempotency = idempotency(idempotencyKey);
        RpcResult<Void> result = dubboProvider.deleteContest(
                new DeleteContestCommand(
                        commandId("delete", idempotency), idempotency,
                        new ActorDelegation("ADMIN", actorId, actorId, "contest delete"),
                        trace(), id, 0L, "contest delete"));
        if (!result.success()) {
            throw mapError(result);
        }
    }

    public AdminContestVO startContest(String id) {
        return startContest(id, null);
    }

    public AdminContestVO startContest(String id, String idempotencyKey) {
        return transition(id, true, idempotencyKey);
    }

    public AdminContestVO endContest(String id) {
        return endContest(id, null);
    }

    public AdminContestVO endContest(String id, String idempotencyKey) {
        return transition(id, false, idempotencyKey);
    }

    public ContestProblemAdminDTO addProblem(String id, AddContestProblemDTO dto, String idempotencyKey) {
        ensureDubboEnabled();
        String actorId = currentActor();
        IdMetadata idempotency = idempotency(idempotencyKey);
        RpcResult<ContestProblemAdminDTO> result = dubboProvider.addProblem(
                new AddContestProblemCommand(
                        commandId("add-problem", idempotency), idempotency,
                        new ActorDelegation("ADMIN", actorId, actorId, "contest problem add"),
                        trace(), id, new ContestProblemInputDTO(dto.getProblemId(), dto.getScore())));
        if (!result.success()) {
            throw mapError(result);
        }
        return result.data();
    }

    public void removeProblem(String id, Long problemId, String idempotencyKey) {
        ensureDubboEnabled();
        String actorId = currentActor();
        IdMetadata idempotency = idempotency(idempotencyKey);
        RpcResult<Void> result = dubboProvider.removeProblem(
                new RemoveContestProblemCommand(
                        commandId("remove-problem", idempotency), idempotency,
                        new ActorDelegation("ADMIN", actorId, actorId, "contest problem remove"),
                        trace(), id, problemId));
        if (!result.success()) {
            throw mapError(result);
        }
    }

    private AdminContestVO transition(String id, boolean start, String idempotencyKey) {
        ensureDubboEnabled();
        String actorId = currentActor();
        IdMetadata idempotency = idempotency(idempotencyKey);
        String operation = start ? "start" : "end";
        RpcResult<ContestAdminViewDTO> result = start
                ? dubboProvider.startContest(new StartContestCommand(
                        commandId(operation, idempotency), idempotency,
                        new ActorDelegation("ADMIN", actorId, actorId, "contest start"),
                        trace(), id, 0L, "contest start"))
                : dubboProvider.endContest(new EndContestCommand(
                        commandId(operation, idempotency), idempotency,
                        new ActorDelegation("ADMIN", actorId, actorId, "contest end"),
                        trace(), id, 0L, "contest end"));
        if (!result.success()) {
            throw mapError(result);
        }
        return adminContestProjection.getContest(id);
    }

    private String currentActor() {
        return requireActor(currentUserProvider.getCurrentUserId());
    }

    private void ensureDubboEnabled() {
        if (!dubboEnabled) {
            throw new BusinessException(AdminErrorCode.CONFLICT,
                    "Contest Dubbo cutover is disabled");
        }
    }

    private static String requireActor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED,
                    "Authenticated admin actor is required");
        }
        return actorId;
    }

    private static String contestType(String value) {
        return value == null || value.isBlank() ? "ICPC" : value;
    }

    private static List<ContestProblemInputDTO> toProblemInputs(List<AddContestProblemDTO> problems) {
        return problems == null ? null : problems.stream()
                .map(problem -> new ContestProblemInputDTO(problem.getProblemId(), problem.getScore()))
                .toList();
    }

    private static long epochMs(java.time.LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static Long epochMsOrNull(java.time.LocalDateTime value) {
        return value == null ? null : epochMs(value);
    }

    private static IdMetadata idempotency(String requestedKey) {
        String key = requestedKey == null || requestedKey.isBlank()
                ? UUID.randomUUID().toString() : requestedKey.trim();
        if (key.length() > 120) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST,
                    "Idempotency-Key must not exceed 120 characters");
        }
        return IdMetadata.of(key, null);
    }

    private static String commandId(String operation, IdMetadata idempotency) {
        return UUID.nameUUIDFromBytes(
                (operation + ":" + idempotency.idempotencyKey()).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static TraceMetadata trace() {
        return new TraceMetadata(TraceIdUtil.current(), null, null, null);
    }

    private static BusinessException mapError(RpcResult<?> result) {
        if (result == null || result.error() == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "RPC failed without error payload");
        }
        int code = result.error().code();
        if (code == AppErrorCode.CONTENT_NOT_FOUND.code()) {
            return new BusinessException(AdminErrorCode.CONTEST_NOT_FOUND, result.error().message());
        }
        if (code == AppErrorCode.BAD_REQUEST.code()) {
            return new BusinessException(AdminErrorCode.BAD_REQUEST, result.error().message());
        }
        if (code == AppErrorCode.UNAUTHORIZED.code()) {
            return new BusinessException(AdminErrorCode.UNAUTHORIZED, result.error().message());
        }
        if (code == AppErrorCode.FORBIDDEN.code()) {
            return new BusinessException(AdminErrorCode.FORBIDDEN, result.error().message());
        }
        if (code == AppErrorCode.VERSION_CONFLICT.code()
                || code == AppErrorCode.CONTENT_STATE_CONFLICT.code()) {
            return new BusinessException(AdminErrorCode.CONFLICT, result.error().message());
        }
        return new BusinessException(AdminErrorCode.UNKNOWN_ERROR, result.error().message());
    }
}
