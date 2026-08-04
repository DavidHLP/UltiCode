package com.ulticode.auth.dubbo.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import com.ulticode.auth.idempotency.mapper.AuthCommandReceiptMapper;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class AccountAdministrationProvider implements AccountAdministrationService {

    private static final Logger log = LoggerFactory.getLogger(AccountAdministrationProvider.class);

    private final AccountAdministrationEngine engine;
    private final AuthCommandReceiptMapper receiptMapper;
    private final ObjectMapper objectMapper;

    public AccountAdministrationProvider(AccountAdministrationEngine engine,
                                        AuthCommandReceiptMapper receiptMapper,
                                        ObjectMapper objectMapper) {
        this.engine = engine;
        this.receiptMapper = receiptMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public RpcResult<AccountStateDTO> changeState(ChangeAccountStateCommand command) {
        String serviceName = "AccountAdministrationService";
        String opName = "changeState";
        String idempotencyKey = command.idempotency() != null ? command.idempotency().idempotencyKey() : null;
        String traceId = command.trace() != null ? command.trace().traceId() : "t-system";

        // 1. Idempotency Check
        if (receiptMapper != null && idempotencyKey != null) {
            AuthCommandReceiptEntity existing = receiptMapper.findByReceiptKey(serviceName, opName, idempotencyKey);
            if (existing != null && "SUCCESS".equals(existing.getStatus())) {
                try {
                    AccountStateDTO dto = objectMapper.readValue(existing.getResultPayload(), AccountStateDTO.class);
                    return RpcResult.success(dto, traceId);
                } catch (Exception e) {
                    // Fallback to fresh execution if replay fails to parse
                }
            }
        }

        try {
            // 2. Perform transactional state mutation via Spring AOP proxy on AccountAdministrationEngine
            RpcResult<AccountStateDTO> result = engine.changeState(command, traceId);
            if (result != null && result.success() && result.data() != null) {
                // 3. Record command receipt AFTER successful transaction
                recordReceipt(serviceName, opName, command.commandId(), idempotencyKey, result.data(), command.actor().actorId(), traceId);
            }
            return result;
        } catch (Exception e) {
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
    }

    @Override
    public RpcResult<AuthorizationSnapshotDTO> changeAuthorization(ChangeAuthorizationCommand command) {
        String serviceName = "AccountAdministrationService";
        String opName = "changeAuthorization";
        String idempotencyKey = command.idempotency() != null ? command.idempotency().idempotencyKey() : null;
        String traceId = command.trace() != null ? command.trace().traceId() : "t-system";

        // 1. Idempotency Check
        if (receiptMapper != null && idempotencyKey != null) {
            AuthCommandReceiptEntity existing = receiptMapper.findByReceiptKey(serviceName, opName, idempotencyKey);
            if (existing != null && "SUCCESS".equals(existing.getStatus())) {
                try {
                    AuthorizationSnapshotDTO dto = objectMapper.readValue(existing.getResultPayload(), AuthorizationSnapshotDTO.class);
                    return RpcResult.success(dto, traceId);
                } catch (Exception e) {
                    // Fallback to fresh execution if replay fails to parse
                }
            }
        }

        try {
            // 2. Perform transactional authorization mutation via Spring AOP proxy on AccountAdministrationEngine
            RpcResult<AuthorizationSnapshotDTO> result = engine.changeAuthorization(command, traceId);
            if (result != null && result.success() && result.data() != null) {
                // 3. Record command receipt AFTER successful transaction
                recordReceipt(serviceName, opName, command.commandId(), idempotencyKey, result.data(), command.actor().actorId(), traceId);
            }
            return result;
        } catch (Exception e) {
            return RpcResult.failure(AuthErrorCode.UNEXPECTED_AUTH_STATE, traceId);
        }
    }

    /**
     * Record command receipt after primary transactional mutation completes.
     * Receipt insert failure (e.g. duplicate key collision on concurrent first-time retries or transient DB blip)
     * is intentionally caught and logged so it does not break the already-committed primary business transaction.
     * Future retries with the same idempotency key will receive an AUTHORIZATION_VERSION_CONFLICT error
     * which Admin callers treat as idempotent / already applied.
     */
    private void recordReceipt(String service, String operation, String commandId, String idempotencyKey, Object resultDto, String actorId, String traceId) {
        if (receiptMapper == null || idempotencyKey == null) {
            return;
        }
        try {
            AuthCommandReceiptEntity receipt = new AuthCommandReceiptEntity();
            receipt.setId(UUID.randomUUID().toString());
            receipt.setCommandId(commandId);
            receipt.setService(service);
            receipt.setOperation(operation);
            receipt.setIdempotencyKey(idempotencyKey);
            receipt.setStatus("SUCCESS");
            receipt.setResultPayload(objectMapper.writeValueAsString(resultDto));
            receipt.setActorType("ADMIN");
            receipt.setActorId(actorId);
            receipt.setTraceId(traceId);
            receipt.setCreatedAt(LocalDateTime.now());
            receiptMapper.insert(receipt);
        } catch (Exception e) {
            log.warn("Failed to insert idempotency receipt for commandId={}, idempotencyKey={}: {}", commandId, idempotencyKey, e.getMessage());
        }
    }
}
