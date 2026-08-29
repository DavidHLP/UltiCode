package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.AddContestProblemCommand;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.RemoveContestProblemCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.dto.ContestProblemAdminDTO;
import com.ulticode.app.api.dto.ContestProblemInputDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.app.api.service.ContestAdministrationService;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.app.error.ContestErrorCode;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.contest.port.ContestOwnerPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dubbo provider for the App-owned contest write seam. The provider validates
 * delegated admin identity, translates the RPC contract to the local owner
 * port, and maps local failures back to app-api error codes.
 *
 * <p>Write commands carry {@link WriteCommand} idempotency metadata; each
 * mutating method claims the {@code (service, operation, idempotency_key)}
 * receipt atomically in the same transaction as the owner mutation (per
 * &sect;6.2 replay-dedup). Retried commands replay the stored success payload
 * or fail with {@link AppErrorCode#IDEMPOTENCY_KEY_CONFLICT} when the
 * business fingerprint differs.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ContestAdministrationProvider implements ContestAdministrationService {

    private static final String SERVICE_NAME = "ContestAdministrationService";
    private static final String OP_CREATE = "createContest";
    private static final String OP_UPDATE = "updateContest";
    private static final String OP_DELETE = "deleteContest";
    private static final String OP_START = "startContest";
    private static final String OP_END = "endContest";
    private static final String OP_ADD_PROBLEM = "addProblem";
    private static final String OP_REMOVE_PROBLEM = "removeProblem";

    private final ContestOwnerPort ownerPort;
    private final ContestAdminReadPort readPort;
    private final AppCommandReceiptMapper receiptMapper;
    private final ObjectMapper objectMapper;
    private final AdminActorAuthorizer actorAuthorizer;

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_CONTEST,
            entityType = AuditVocabulary.ENTITY_CONTEST, captureOldState = false)
    public RpcResult<ContestAdminViewDTO> createContest(CreateContestCommand command) {
        String traceId = traceId(command);
        String idempotencyKey = idempotencyKey(command);
        String fingerprint = fingerprintCreate(command);
        try {
            requireAdminActor(command.actor());
            AppCommandReceiptEntity replay = findReplayableReceipt(OP_CREATE, idempotencyKey, fingerprint);
            if (replay != null) {
                ContestAdminViewDTO replayed = replayPayload(replay, ContestAdminViewDTO.class);
                if (replayed != null) {
                    return RpcResult.success(replayed, traceId);
                }
            }
            String contestId = ownerPort.createContest(command);
            audit(command.actor(), contestId, null);
            ContestAdminViewDTO view = readView(contestId);
            recordReceipt(command, OP_CREATE, idempotencyKey, fingerprint, view, traceId);
            return RpcResult.success(view, traceId);
        } catch (BusinessException e) {
            return toFailure(e, traceId);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.createContest unexpected error", e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_CONTEST,
            entityType = AuditVocabulary.ENTITY_CONTEST, captureOldState = false)
    public RpcResult<ContestAdminViewDTO> updateContest(UpdateContestCommand command) {
        String traceId = traceId(command);
        String idempotencyKey = idempotencyKey(command);
        String fingerprint = fingerprintUpdate(command);
        try {
            requireAdminActor(command.actor());
            AppCommandReceiptEntity replay = findReplayableReceipt(OP_UPDATE, idempotencyKey, fingerprint);
            if (replay != null) {
                ContestAdminViewDTO replayed = replayPayload(replay, ContestAdminViewDTO.class);
                if (replayed != null) {
                    return RpcResult.success(replayed, traceId);
                }
            }
            ownerPort.updateContest(command);
            audit(command.actor(), command.contestId(), null);
            ContestAdminViewDTO view = readView(command.contestId());
            recordReceipt(command, OP_UPDATE, idempotencyKey, fingerprint, view, traceId);
            return RpcResult.success(view, traceId);
        } catch (BusinessException e) {
            return toFailure(e, traceId);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.updateContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.DELETE_CONTEST,
            entityType = AuditVocabulary.ENTITY_CONTEST, captureOldState = false)
    public RpcResult<Void> deleteContest(DeleteContestCommand command) {
        String traceId = traceId(command);
        String idempotencyKey = idempotencyKey(command);
        String fingerprint = fingerprintTransition(command.actor(), command.contestId(),
                command.expectedVersion(), command.rationale());
        try {
            requireAdminActor(command.actor());
            AppCommandReceiptEntity replay = findReplayableReceipt(OP_DELETE, idempotencyKey, fingerprint);
            if (replay != null) {
                return RpcResult.success(traceId);
            }
            ownerPort.deleteContest(command.contestId(), command.actor().actorId());
            audit(command.actor(), command.contestId(), null);
            recordReceipt(command, OP_DELETE, idempotencyKey, fingerprint, null, traceId);
            return RpcResult.success(traceId);
        } catch (BusinessException e) {
            return toFailure(e, traceId);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.deleteContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_CONTEST,
            entityType = AuditVocabulary.ENTITY_CONTEST, captureOldState = false)
    public RpcResult<ContestAdminViewDTO> startContest(StartContestCommand command) {
        String traceId = traceId(command);
        String idempotencyKey = idempotencyKey(command);
        String fingerprint = fingerprintTransition(command.actor(), command.contestId(),
                command.expectedVersion(), command.rationale());
        try {
            requireAdminActor(command.actor());
            AppCommandReceiptEntity replay = findReplayableReceipt(OP_START, idempotencyKey, fingerprint);
            if (replay != null) {
                ContestAdminViewDTO replayed = replayPayload(replay, ContestAdminViewDTO.class);
                if (replayed != null) {
                    return RpcResult.success(replayed, traceId);
                }
            }
            ownerPort.startContest(command.contestId());
            audit(command.actor(), command.contestId(), null);
            ContestAdminViewDTO view = readView(command.contestId());
            recordReceipt(command, OP_START, idempotencyKey, fingerprint, view, traceId);
            return RpcResult.success(view, traceId);
        } catch (BusinessException e) {
            return toFailure(e, traceId);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.startContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_CONTEST,
            entityType = AuditVocabulary.ENTITY_CONTEST, captureOldState = false)
    public RpcResult<ContestAdminViewDTO> endContest(EndContestCommand command) {
        String traceId = traceId(command);
        String idempotencyKey = idempotencyKey(command);
        String fingerprint = fingerprintTransition(command.actor(), command.contestId(),
                command.expectedVersion(), command.rationale());
        try {
            requireAdminActor(command.actor());
            AppCommandReceiptEntity replay = findReplayableReceipt(OP_END, idempotencyKey, fingerprint);
            if (replay != null) {
                ContestAdminViewDTO replayed = replayPayload(replay, ContestAdminViewDTO.class);
                if (replayed != null) {
                    return RpcResult.success(replayed, traceId);
                }
            }
            ownerPort.endContest(command.contestId());
            audit(command.actor(), command.contestId(), null);
            ContestAdminViewDTO view = readView(command.contestId());
            recordReceipt(command, OP_END, idempotencyKey, fingerprint, view, traceId);
            return RpcResult.success(view, traceId);
        } catch (BusinessException e) {
            return toFailure(e, traceId);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.endContest unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_CONTEST,
            entityType = AuditVocabulary.ENTITY_CONTEST, captureOldState = false)
    public RpcResult<ContestProblemAdminDTO> addProblem(AddContestProblemCommand command) {
        String traceId = traceId(command);
        String idempotencyKey = idempotencyKey(command);
        String fingerprint = fingerprintAddProblem(command);
        try {
            requireAdminActor(command.actor());
            AppCommandReceiptEntity replay = findReplayableReceipt(OP_ADD_PROBLEM, idempotencyKey, fingerprint);
            if (replay != null) {
                ContestProblemAdminDTO replayed = replayPayload(replay, ContestProblemAdminDTO.class);
                if (replayed != null) {
                    return RpcResult.success(replayed, traceId);
                }
            }
            ContestProblemAdminDTO result = ownerPort.addProblem(command);
            audit(command.actor(), command.contestId(), null);
            recordReceipt(command, OP_ADD_PROBLEM, idempotencyKey, fingerprint, result, traceId);
            return RpcResult.success(result, traceId);
        } catch (BusinessException e) {
            return toFailure(e, traceId);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.addProblem unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_CONTEST,
            entityType = AuditVocabulary.ENTITY_CONTEST, captureOldState = false)
    public RpcResult<Void> removeProblem(RemoveContestProblemCommand command) {
        String traceId = traceId(command);
        String idempotencyKey = idempotencyKey(command);
        String fingerprint = sha256Hex(String.join("|",
                actorFields(command.actor()),
                nullSafe(command.contestId()),
                nullSafe(command.problemId())));
        try {
            requireAdminActor(command.actor());
            AppCommandReceiptEntity replay = findReplayableReceipt(OP_REMOVE_PROBLEM, idempotencyKey, fingerprint);
            if (replay != null) {
                return RpcResult.success(traceId);
            }
            ownerPort.removeProblem(command);
            audit(command.actor(), command.contestId(), null);
            recordReceipt(command, OP_REMOVE_PROBLEM, idempotencyKey, fingerprint, null, traceId);
            return RpcResult.success(traceId);
        } catch (BusinessException e) {
            return toFailure(e, traceId);
        } catch (Exception e) {
            log.error("ContestAdministrationProvider.removeProblem unexpected error id={}",
                    command.contestId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    /**
     * Claim the replay-dedup receipt for {@code (service, operation, idempotency key)}.
     *
     * @return the stored SUCCESS receipt when this exact request already ran,
     *         {@code null} when the key is absent or nothing is stored yet
     *         (caller should execute); throws
     *         {@link AppErrorCode#IDEMPOTENCY_KEY_CONFLICT} when the same key
     *         was used with a different business payload.
     */
    private AppCommandReceiptEntity findReplayableReceipt(String operation, String idempotencyKey,
                                                          String fingerprint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        AppCommandReceiptEntity existing = receiptMapper.findByReceiptKey(
                SERVICE_NAME, operation, idempotencyKey);
        if (existing == null || !"SUCCESS".equals(existing.getStatus())) {
            return null;
        }
        String storedFingerprint = existing.getRequestFingerprint();
        if (storedFingerprint != null && !storedFingerprint.equals(fingerprint)) {
            throw new BusinessException(AppErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        return existing;
    }

    private <T> T replayPayload(AppCommandReceiptEntity receipt, Class<T> payloadType) {
        try {
            return objectMapper.readValue(receipt.getResultPayload(), payloadType);
        } catch (Exception e) {
            return null;
        }
    }

    private void recordReceipt(WriteCommand command, String operation, String idempotencyKey,
                               String fingerprint, Object result, String traceId) {
        try {
            AppCommandReceiptEntity receipt = new AppCommandReceiptEntity();
            receipt.setId(UUID.randomUUID().toString());
            receipt.setCommandId(command.commandId());
            receipt.setService(SERVICE_NAME);
            receipt.setOperation(operation);
            receipt.setIdempotencyKey(idempotencyKey);
            receipt.setRequestFingerprint(fingerprint);
            receipt.setStatus("SUCCESS");
            receipt.setResultPayload(result != null ? objectMapper.writeValueAsString(result) : null);
            receipt.setActorType(command.actor() != null ? command.actor().actorType() : null);
            receipt.setActorId(command.actor() != null ? command.actor().actorId() : null);
            receipt.setTraceId(traceId);
            receiptMapper.insert(receipt);
        } catch (Exception e) {
            // The receipt is part of the authoritative transaction: a persistence
            // failure must roll the mutation back, never report success.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new RuntimeException("Idempotency receipt insert failed", e);
        }
    }

    private ContestAdminViewDTO readView(String contestId) {
        ContestAdminDTO contest = readPort.selectById(contestId);
        if (contest == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Contest not found after mutation");
        }
        return new ContestAdminViewDTO(contest.getId(), contest.getTitle(), contest.getStatus());
    }

    private void requireAdminActor(ActorDelegation actor) {
        com.ulticode.app.security.TrustedAdminActor.requireTrusted(
                actorAuthorizer, actor, "Contest mutation");
    }

    private static void audit(ActorDelegation actor, String entityId,
                              java.util.Map<String, Object> newValues) {
        AuditContext.setUserId(actor.actorId());
        AuditContext.setEntityId(entityId);
        if (newValues != null) {
            AuditContext.setNewValues(newValues);
        }
    }

    private static String traceId(com.ulticode.common.command.WriteCommand command) {
        return command != null && command.trace() != null && command.trace().traceId() != null
                ? command.trace().traceId() : null;
    }

    private static String idempotencyKey(WriteCommand command) {
        return command != null && command.idempotency() != null
                ? command.idempotency().idempotencyKey() : null;
    }

    private static String fingerprintCreate(CreateContestCommand command) {
        return sha256Hex(String.join("|",
                actorFields(command.actor()),
                nullSafe(command.slug()),
                nullSafe(command.title()),
                nullSafe(command.creatorAccountId()),
                nullSafe(command.contestType()),
                nullSafe(command.scoringMode()),
                nullSafe(command.scoringRuleId()),
                nullSafe(command.description()),
                String.valueOf(command.startEpochMs()),
                String.valueOf(command.durationMinutes()),
                nullSafe(command.maxParticipants()),
                nullSafe(command.isPremium()),
                nullSafe(command.isPublished()),
                joinLongs(command.problemIds()),
                joinProblems(command.problems())));
    }

    private static String fingerprintUpdate(UpdateContestCommand command) {
        return sha256Hex(String.join("|",
                actorFields(command.actor()),
                nullSafe(command.contestId()),
                nullSafe(command.expectedVersion()),
                nullSafe(command.title()),
                nullSafe(command.startEpochMs()),
                nullSafe(command.durationMinutes()),
                nullSafe(command.rationale()),
                nullSafe(command.description()),
                nullSafe(command.maxParticipants()),
                nullSafe(command.isPremium()),
                nullSafe(command.isPublished()),
                nullSafe(command.slug()),
                nullSafe(command.contestType()),
                nullSafe(command.scoringRuleId()),
                joinLongs(command.problemIds()),
                joinProblems(command.problems())));
    }

    private static String fingerprintTransition(ActorDelegation actor, String contestId,
                                                Long expectedVersion, String rationale) {
        return sha256Hex(String.join("|",
                actorFields(actor),
                nullSafe(contestId),
                nullSafe(expectedVersion),
                nullSafe(rationale)));
    }

    private static String fingerprintAddProblem(AddContestProblemCommand command) {
        return sha256Hex(String.join("|",
                actorFields(command.actor()),
                nullSafe(command.contestId()),
                nullSafe(command.problem() != null ? command.problem().problemId() : null),
                nullSafe(command.problem() != null ? command.problem().score() : null)));
    }

    private static String actorFields(ActorDelegation actor) {
        if (actor == null) {
            return "||";
        }
        return String.join("|",
                nullSafe(actor.actorType()),
                nullSafe(actor.actorId()),
                nullSafe(actor.delegatorId()));
    }

    private static String joinLongs(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String joinProblems(List<ContestProblemInputDTO> problems) {
        if (problems == null || problems.isEmpty()) {
            return "";
        }
        return problems.stream()
                .map(p -> p.problemId() + ":" + nullSafe(p.score()))
                .collect(Collectors.joining(","));
    }

    private static String nullSafe(Object value) {
        return value != null ? value.toString() : "";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static <T> RpcResult<T> toFailure(BusinessException e, String traceId) {
        if (e.getErrorCode() == null) {
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
        int code = e.getErrorCode().code();
        AppErrorCode mapped;
        if (code == BaseErrorCode.UNAUTHORIZED.code()) {
            mapped = AppErrorCode.UNAUTHORIZED;
        } else if (code == BaseErrorCode.FORBIDDEN.code()) {
            mapped = AppErrorCode.FORBIDDEN;
        } else if (code == BaseErrorCode.BAD_REQUEST.code()
                || code == BaseErrorCode.VALIDATION_FAILED.code()
                || code == ContestErrorCode.CONTEST_ONLY_REGISTER_UPCOMING.code()
                || code == ContestErrorCode.CONTEST_NOT_STARTED.code()
                || code == ContestErrorCode.CONTEST_ENDED.code()
                || code == ContestErrorCode.CONTEST_FULL.code()) {
            mapped = AppErrorCode.BAD_REQUEST;
        } else if (code == BaseErrorCode.NOT_FOUND.code()
                || code == ContestErrorCode.CONTEST_NOT_FOUND.code()
                || code == ContestErrorCode.SCORING_RULE_NOT_FOUND.code()
                || code == ContestErrorCode.PROBLEM_NOT_FOUND.code()) {
            mapped = AppErrorCode.CONTENT_NOT_FOUND;
        } else if (code == ContestErrorCode.CONTEST_SLUG_EXISTS.code()) {
            mapped = AppErrorCode.CONTENT_STATE_CONFLICT;
        } else if (code == AppErrorCode.IDEMPOTENCY_KEY_CONFLICT.code()) {
            mapped = AppErrorCode.IDEMPOTENCY_KEY_CONFLICT;
        } else {
            mapped = AppErrorCode.UNEXPECTED_APP_STATE;
        }
        return RpcResult.failure(mapped, traceId);
    }
}
