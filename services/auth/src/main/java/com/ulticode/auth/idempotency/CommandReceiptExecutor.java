package com.ulticode.auth.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.command.WriteCommand;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import com.ulticode.auth.idempotency.mapper.AuthCommandReceiptMapper;
import com.ulticode.common.rpc.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Shared provider-side command receipt boundary.
 *
 * <p>Receipt lookup, primary mutation, and receipt finalization all execute
 * in one transaction. A receipt insert or serialization failure therefore
 * rolls back the primary mutation instead of leaving an un-deduplicated side
 * effect.</p>
 */
@Component
public class CommandReceiptExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandReceiptExecutor.class);
    private static final String SUCCESS = "SUCCESS";
    private static final String DEFAULT_TRACE_ID = "t-system";
    private static final char FIELD_SEPARATOR = '\u001f';

    private final AuthCommandReceiptMapper receiptMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CommandReceiptExecutor(
            AuthCommandReceiptMapper receiptMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        this.receiptMapper = receiptMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Execute one idempotent mutation at a provider transaction boundary.
     *
     * <p>The mutation is deliberately allowed to throw. The transaction
     * interceptor must observe that exception before the provider translates
     * it into a transport failure, otherwise a caught business exception could
     * accidentally commit a partial mutation.</p>
     */
    @Transactional
    public <T> RpcResult<T> execute(
            String service,
            String operation,
            WriteCommand command,
            Class<T> resultType,
            Function<String, RpcResult<T>> mutation) {
        String traceId = traceId(command);
        if (command == null || command.idempotency() == null
                || !command.idempotency().hasKey()) {
            return RpcResult.failure(AuthErrorCode.INVALID_ACCOUNT_REQUEST, traceId);
        }

        String idempotencyKey = command.idempotency().idempotencyKey();
        String requestFingerprint = fingerprint(command);
        if (receiptMapper == null) {
            RpcResult<T> result = Objects.requireNonNull(mutation.apply(traceId),
                    "mutation result must not be null");
            return result;
        }

        AuthCommandReceiptEntity existing = receiptMapper.findByReceiptKey(
                service, operation, idempotencyKey);
        if (existing != null) {
            if (existing.getRequestFingerprint() != null
                    && !requestFingerprint.equals(existing.getRequestFingerprint())) {
                return RpcResult.failure(AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT, traceId);
            }
            if (!SUCCESS.equals(existing.getStatus())) {
                return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
            }
            try {
                T result = objectMapper.readValue(existing.getResultPayload(), resultType);
                return RpcResult.success(result, traceId);
            } catch (Exception e) {
                log.error("Unable to replay command receipt for service={}, operation={}",
                        service, operation, e);
                return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
            }
        }

        RpcResult<T> result = Objects.requireNonNull(mutation.apply(traceId),
                "mutation result must not be null");
        if (result.success() && result.data() != null) {
            insertReceipt(service, operation, command, requestFingerprint, result.data(), traceId);
        }
        return result;
    }

    private <T> void insertReceipt(
            String service,
            String operation,
            WriteCommand command,
            String requestFingerprint,
            T result,
            String traceId) {
        AuthCommandReceiptEntity receipt = new AuthCommandReceiptEntity();
        receipt.setId(java.util.UUID.randomUUID().toString());
        receipt.setCommandId(command.commandId());
        receipt.setService(service);
        receipt.setOperation(operation);
        receipt.setIdempotencyKey(command.idempotency().idempotencyKey());
        receipt.setRequestFingerprint(requestFingerprint);
        receipt.setStatus(SUCCESS);
        try {
            receipt.setResultPayload(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize command receipt", e);
        }
        receipt.setActorType(command.actor() == null ? null : command.actor().actorType());
        receipt.setActorId(command.actor() == null ? null : command.actor().actorId());
        receipt.setTraceId(traceId);
        receipt.setCreatedAt(LocalDateTime.now(clock));
        // Do not catch insert failures: the enclosing transaction must roll back.
        receiptMapper.insert(receipt);
    }

    public static String traceId(WriteCommand command) {
        if (command == null || command.trace() == null
                || command.trace().traceId() == null
                || command.trace().traceId().isBlank()) {
            return DEFAULT_TRACE_ID;
        }
        return command.trace().traceId();
    }

    /** Fingerprint business fields only; command metadata changes between retries. */
    public static String fingerprint(WriteCommand command) {
        Objects.requireNonNull(command, "command");
        String payload;
        if (command instanceof CreateAccountCommand value) {
            payload = join(value.username(), value.email(), value.password(), value.role());
        } else if (command instanceof UpdateAccountCredentialsCommand value) {
            payload = join(value.accountId(), value.username(), value.email());
        } else if (command instanceof ChangePasswordCommand value) {
            payload = join(value.accountId(), value.currentPassword(), value.newPassword());
        } else if (command instanceof ResetPasswordCommand value) {
            payload = join(value.accountId(), value.newPassword(), value.rationale());
        } else if (command instanceof DeleteAccountCommand value) {
            payload = join(value.accountId(), value.rationale());
        } else if (command instanceof ChangeAccountStateCommand value) {
            payload = join(value.accountId(), value.action(), value.rationale());
        } else if (command instanceof ChangeAuthorizationCommand value) {
            String permissions = value.permissions().stream()
                    .sorted()
                    .map(CommandReceiptExecutor::encode)
                    .collect(Collectors.joining("|"));
            payload = join(value.accountId(), value.role(), permissions, value.rationale());
        } else {
            payload = command.getClass().getName();
        }
        return sha256(command.getClass().getName() + FIELD_SEPARATOR + payload);
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
