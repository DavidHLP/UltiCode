package com.ulticode.auth.dubbo.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.command.WriteCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import com.ulticode.auth.idempotency.mapper.AuthCommandReceiptMapper;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Function;

/**
 * Dubbo provider for auth-owned account-management mutations.
 *
 * <p>Replay deduplication is intentionally finalized in the same transaction
 * as the business mutation. A receipt insert failure therefore rolls back the
 * mutation instead of creating an unrecorded side effect.
 */
@Component
@DubboService(version = "1.0.0")
public class AccountManagementProvider implements AccountManagementService {

    private static final Logger log = LoggerFactory.getLogger(AccountManagementProvider.class);
    private static final String SERVICE_NAME = "AccountManagementService";
    private static final String DEFAULT_TRACE_ID = "t-system";

    private final AccountManagementEngine engine;
    private final AuthCommandReceiptMapper receiptMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AccountManagementProvider(AccountManagementEngine engine,
                                     AuthCommandReceiptMapper receiptMapper,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.engine = engine;
        this.receiptMapper = receiptMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RpcResult<AccountMutationDTO> createAccount(CreateAccountCommand command) {
        return execute("createAccount", command,
                traceId -> engine.create(command, traceId));
    }

    @Override
    @Transactional
    public RpcResult<AccountMutationDTO> updateCredentials(
            UpdateAccountCredentialsCommand command) {
        return execute("updateCredentials", command,
                traceId -> engine.updateCredentials(command, traceId));
    }

    @Override
    @Transactional
    public RpcResult<AccountMutationDTO> changePassword(ChangePasswordCommand command) {
        return execute("changePassword", command,
                traceId -> engine.changePassword(command, traceId));
    }

    @Override
    @Transactional
    public RpcResult<AccountMutationDTO> resetPassword(ResetPasswordCommand command) {
        return execute("resetPassword", command,
                traceId -> engine.resetPassword(command, traceId));
    }

    @Override
    @Transactional
    public RpcResult<AccountMutationDTO> deleteAccount(DeleteAccountCommand command) {
        return execute("deleteAccount", command,
                traceId -> engine.deleteAccount(command, traceId));
    }

    private RpcResult<AccountMutationDTO> execute(
            String operation,
            WriteCommand command,
            Function<String, RpcResult<AccountMutationDTO>> mutation) {
        String traceId = traceId(command);
        if (command == null || command.idempotency() == null
                || !command.idempotency().hasKey()) {
            return RpcResult.failure(AuthErrorCode.INVALID_ACCOUNT_REQUEST, traceId);
        }

        String idempotencyKey = command.idempotency().idempotencyKey();
        String requestFingerprint = fingerprint(command);
        if (receiptMapper != null) {
            AuthCommandReceiptEntity existing = receiptMapper.findByReceiptKey(
                    SERVICE_NAME, operation, idempotencyKey);
            if (existing != null) {
                if (!requestFingerprint.equals(existing.getRequestFingerprint())) {
                    return RpcResult.failure(AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT, traceId);
                }
                if (!"SUCCESS".equals(existing.getStatus())) {
                    return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
                }
                try {
                    AccountMutationDTO dto = objectMapper.readValue(
                            existing.getResultPayload(), AccountMutationDTO.class);
                    return RpcResult.success(dto, traceId);
                } catch (Exception e) {
                    log.error("Unable to replay account-management receipt for operation={}", operation, e);
                    return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
                }
            }
        }

        RpcResult<AccountMutationDTO> result;
        try {
            result = mutation.apply(traceId);
        } catch (Exception e) {
            log.error("Account-management operation failed: {}", operation, e);
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
        if (result == null) {
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
        if (result.success() && result.data() != null && receiptMapper != null) {
            recordReceipt(operation, command, requestFingerprint, result.data(), traceId);
        }
        return result;
    }

    /**
     * Insert the final receipt without catching exceptions. This method runs
     * inside the caller's transaction; a failed insert must mark that
     * transaction unsuccessful and roll back the primary mutation.
     */
    private void recordReceipt(String operation,
                               WriteCommand command,
                               String requestFingerprint,
                               AccountMutationDTO result,
                               String traceId) {
        AuthCommandReceiptEntity receipt = new AuthCommandReceiptEntity();
        receipt.setId(UUID.randomUUID().toString());
        receipt.setCommandId(command.commandId());
        receipt.setService(SERVICE_NAME);
        receipt.setOperation(operation);
        receipt.setIdempotencyKey(command.idempotency().idempotencyKey());
        receipt.setRequestFingerprint(requestFingerprint);
        receipt.setStatus("SUCCESS");
        try {
            receipt.setResultPayload(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize account-management receipt", e);
        }
        receipt.setActorType(command.actor().actorType());
        receipt.setActorId(command.actor().actorId());
        receipt.setTraceId(traceId);
        receipt.setCreatedAt(LocalDateTime.now(clock));
        receiptMapper.insert(receipt);
    }

    private static String traceId(WriteCommand command) {
        if (command == null || command.trace() == null
                || command.trace().traceId() == null
                || command.trace().traceId().isBlank()) {
            return DEFAULT_TRACE_ID;
        }
        return command.trace().traceId();
    }

    /** Fingerprint business fields, never metadata that changes between retries. */
    private static String fingerprint(WriteCommand command) {
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
        } else {
            payload = command.getClass().getName();
        }
        return sha256(command.getClass().getName() + "\u001f" + payload);
    }

    private static String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append('\u001f');
            }
            builder.append(value == null ? "<null>" : value);
        }
        return builder.toString();
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
