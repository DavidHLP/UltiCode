package com.ulticode.modules.admin.service;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.AddContestProblemCommand;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.RemoveContestProblemCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.dto.ContestProblemAdminDTO;
import com.ulticode.app.api.dto.ContestProblemInputDTO;
import com.ulticode.app.api.service.ContestAdministrationService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AddContestProblemDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.dto.CreateContestDTO;
import com.ulticode.modules.admin.dto.UpdateContestDTO;
import com.ulticode.modules.admin.projection.AdminContestProjection;
import com.ulticode.modules.admin.write.AdminOwnerErrorMapper;
import com.ulticode.modules.admin.write.AdminWriteEnvelope;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
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
        String actorId = userId;
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "create", idempotencyKey, actorId, currentUserProvider, "contest create");
        RpcResult<ContestAdminViewDTO> result = dubboProvider.createContest(
                new CreateContestCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(),
                        envelope.trace(), dto.getSlug(), dto.getTitle(), actorId,
                        contestType(dto.getContestType()), "SCORE", dto.getScoringRuleId(), dto.getDescription(),
                        epochMs(dto.getStartTime()), dto.getDuration(), dto.getMaxParticipants(),
                        dto.getIsPremium(), dto.getIsPublished(), dto.getProblemIds(),
                        toProblemInputs(dto.getProblems())));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.CONTEST, result);
        }
        return adminContestProjection.getContest(result.data().contestId());
    }

    public AdminContestVO updateContest(String id, UpdateContestDTO dto) {
        return updateContest(id, dto, null);
    }

    public AdminContestVO updateContest(String id, UpdateContestDTO dto, String idempotencyKey) {
        ensureDubboEnabled();
        String actorId = currentUserProvider.getCurrentUserId();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "update", idempotencyKey, actorId, currentUserProvider, "contest update");
        RpcResult<ContestAdminViewDTO> result = dubboProvider.updateContest(
                new UpdateContestCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(),
                        envelope.trace(), id, 0L, dto.getTitle(), epochMsOrNull(dto.getStartTime()),
                        dto.getDuration(), "contest update", dto.getDescription(),
                        dto.getMaxParticipants(), dto.getIsPremium(), dto.getIsPublished(),
                        dto.getSlug(), dto.getContestType(), dto.getScoringRuleId(),
                        dto.getProblemIds(), toProblemInputs(dto.getProblems())));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.CONTEST, result);
        }
        return adminContestProjection.getContest(id);
    }

    public void deleteContest(String id) {
        deleteContest(id, null);
    }

    public void deleteContest(String id, String idempotencyKey) {
        ensureDubboEnabled();
        String actorId = currentUserProvider.getCurrentUserId();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "delete", idempotencyKey, actorId, currentUserProvider, "contest delete");
        RpcResult<Void> result = dubboProvider.deleteContest(
                new DeleteContestCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(),
                        envelope.trace(), id, 0L, "contest delete"));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.CONTEST, result);
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
        String actorId = currentUserProvider.getCurrentUserId();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "add-problem", idempotencyKey, actorId, currentUserProvider, "contest problem add");
        RpcResult<ContestProblemAdminDTO> result = dubboProvider.addProblem(
                new AddContestProblemCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(),
                        envelope.trace(), id, new ContestProblemInputDTO(dto.getProblemId(), dto.getScore())));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.CONTEST, result);
        }
        return result.data();
    }

    public void removeProblem(String id, Long problemId, String idempotencyKey) {
        ensureDubboEnabled();
        String actorId = currentUserProvider.getCurrentUserId();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "remove-problem", idempotencyKey, actorId, currentUserProvider, "contest problem remove");
        RpcResult<Void> result = dubboProvider.removeProblem(
                new RemoveContestProblemCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(),
                        envelope.trace(), id, problemId));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.CONTEST, result);
        }
    }

    private AdminContestVO transition(String id, boolean start, String idempotencyKey) {
        ensureDubboEnabled();
        String operation = start ? "start" : "end";
        String actorId = currentUserProvider.getCurrentUserId();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                operation, idempotencyKey, actorId, currentUserProvider, "contest " + operation);
        RpcResult<ContestAdminViewDTO> result = start
                ? dubboProvider.startContest(new StartContestCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(),
                        envelope.trace(), id, 0L, "contest start"))
                : dubboProvider.endContest(new EndContestCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(),
                        envelope.trace(), id, 0L, "contest end"));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(AdminOwnerErrorMapper.Owner.CONTEST, result);
        }
        return adminContestProjection.getContest(id);
    }

    private void ensureDubboEnabled() {
        if (!dubboEnabled) {
            throw new BusinessException(AdminErrorCode.CONFLICT,
                    "Contest Dubbo cutover is disabled");
        }
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
}
