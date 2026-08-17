package com.ulticode.app.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.rpc.RpcResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provider-side durable command receipt boundary for App-owned mutations.
 * The claim, owner mutation, and receipt finalization share one transaction.
 */
@Component
public class CommandReceiptExecutor {

    private static final String DEFAULT_SERVICE = "SubmissionAdministrationService";
    private static final String PROBLEM_LIST_SERVICE = "ProblemListAdministrationService";
    private static final String SUCCESS = "SUCCESS";
    private static final String PROCESSING = "PROCESSING";
    private static final String DEFAULT_TRACE_ID = "t-system";
    private static final char FIELD_SEPARATOR = '\u001f';

    private final AppCommandReceiptMapper receiptMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CommandReceiptExecutor(
            AppCommandReceiptMapper receiptMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        this.receiptMapper = receiptMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public <T> RpcResult<T> execute(
            String operation,
            WriteCommand command,
            Class<T> resultType,
            Function<String, RpcResult<T>> mutation) {
        return execute(DEFAULT_SERVICE, operation, command, resultType, mutation);
    }

    @Transactional
    public <T> RpcResult<T> execute(
            String service,
            String operation,
            WriteCommand command,
            Class<T> resultType,
            Function<String, RpcResult<T>> mutation) {
        String traceId = traceId(command);
        if (!validCommand(command)) {
            return withKey(RpcResult.failure(AppErrorCode.BAD_REQUEST, traceId),
                    command == null || command.idempotency() == null
                            ? null : command.idempotency().idempotencyKey());
        }

        String key = command.idempotency().idempotencyKey();
        String fingerprint = fingerprint(command);
        AppCommandReceiptEntity claim = newClaim(service, operation, command, fingerprint, traceId);
        if (receiptMapper.insertClaim(claim) == 0) {
            AppCommandReceiptEntity existing = receiptMapper.findByReceiptKey(service, operation, key);
            if (existing == null) {
                return withKey(RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId), key);
            }
            return replay(existing, fingerprint, resultType, traceId, key);
        }

        RpcResult<T> keyedResult = withKey(
                Objects.requireNonNull(mutation.apply(traceId), "mutation result must not be null"),
                key);
        if (keyedResult.success()) {
            try {
                String payload = objectMapper.writeValueAsString(keyedResult.data());
                if (receiptMapper.markSuccess(claim.getId(), payload) != 1) {
                    throw new IllegalStateException("Unable to finalize App command receipt");
                }
            } catch (Exception e) {
                throw new IllegalStateException("Unable to serialize App command receipt", e);
            }
        } else {
            receiptMapper.deleteClaim(claim.getId());
        }
        return keyedResult;
    }

    public static String problemListService() {
        return PROBLEM_LIST_SERVICE;
    }

    private AppCommandReceiptEntity newClaim(
            String service,
            String operation,
            WriteCommand command,
            String requestFingerprint,
            String traceId) {
        AppCommandReceiptEntity receipt = new AppCommandReceiptEntity();
        receipt.setId(java.util.UUID.randomUUID().toString());
        receipt.setCommandId(command.commandId());
        receipt.setService(service);
        receipt.setOperation(operation);
        receipt.setIdempotencyKey(command.idempotency().idempotencyKey());
        receipt.setRequestFingerprint(requestFingerprint);
        receipt.setStatus(PROCESSING);
        receipt.setActorType(command.actor().actorType());
        receipt.setActorId(command.actor().actorId());
        receipt.setTraceId(traceId);
        receipt.setCreatedAt(LocalDateTime.now(clock));
        return receipt;
    }


    private <T> RpcResult<T> replay(
            AppCommandReceiptEntity existing,
            String fingerprint,
            Class<T> resultType,
            String traceId,
            String key) {
        if (existing.getRequestFingerprint() != null
                && !fingerprint.equals(existing.getRequestFingerprint())) {
            return withKey(RpcResult.failure(AppErrorCode.IDEMPOTENCY_KEY_CONFLICT, traceId), key);
        }
        if (!SUCCESS.equals(existing.getStatus())) {
            return withKey(RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId), key);
        }
        if (resultType == Void.class) {
            return new RpcResult<>(true, null, null, null, traceId, 0L, key);
        }
        try {
            T result = objectMapper.readValue(existing.getResultPayload(), resultType);
            return RpcResult.success(result, traceId, 0L, key);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to replay App command receipt", e);
        }
    }

    private static <T> RpcResult<T> withKey(RpcResult<T> result, String key) {
        if (key == null || key.isBlank() || key.equals(result.idempotencyKey())) {
            return result;
        }
        return new RpcResult<>(
                result.success(),
                result.data(),
                result.page(),
                result.error(),
                result.traceId(),
                result.deadlineMs(),
                key);
    }

    private static boolean validCommand(WriteCommand command) {
        return command != null
                && command.idempotency() != null
                && command.idempotency().hasKey()
                && command.trace() != null
                && command.actor() != null
                && command.actor().actorId() != null
                && !command.actor().actorId().isBlank()
                && command.actor().delegatorId() != null
                && !command.actor().delegatorId().isBlank();
    }

    public static String traceId(WriteCommand command) {
        if (command == null || command.trace() == null
                || command.trace().traceId() == null
                || command.trace().traceId().isBlank()) {
            return DEFAULT_TRACE_ID;
        }
        return command.trace().traceId();
    }

    /** Fingerprint business fields and actor identity; retry-only metadata is excluded. */
    public static String fingerprint(WriteCommand command) {
        Objects.requireNonNull(command, "command");
        var components = command.getClass().getRecordComponents();
        String payload = components == null
                ? command.getClass().getName()
                : Arrays.stream(components)
                .filter(component -> !switch (component.getName()) {
                    case "commandId", "idempotency", "trace" -> true;
                    default -> false;
                })
                .map(component -> {
                    try {
                        return component.getName() + "="
                                + encode(component.getAccessor().invoke(command));
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(
                                "Unable to fingerprint " + command.getClass().getName(), e);
                    }
                })
                .collect(Collectors.joining("|"));
        return sha256(command.getClass().getName() + FIELD_SEPARATOR
                + join(command.actor().actorType(), command.actor().actorId(),
                command.actor().delegatorId(), payload));
    }

    private static String join(Object... values) {
        return Arrays.stream(values)
                .map(CommandReceiptExecutor::encode)
                .collect(Collectors.joining("|"));
    }

    private static String encode(Object value) {
        if (value == null) {
            return "-1:";
        }
        String text = String.valueOf(value);
        return text.length() + ":" + text;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
