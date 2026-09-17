package com.ulticode.notification.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.command.ClaimCommandReceiptStore;
import com.ulticode.common.command.GenericFingerprintStrategy;
import com.ulticode.common.command.ReceiptErrorCatalog;
import com.ulticode.common.command.ReceiptExecutor;
import com.ulticode.common.command.ReceiptFingerprintStrategy;
import com.ulticode.common.command.ReceiptView;
import com.ulticode.common.command.ReceiptWrite;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.notification.error.NotificationErrorCode;
import com.ulticode.notification.idempotency.entity.NotificationCommandReceiptEntity;
import com.ulticode.notification.idempotency.mapper.NotificationCommandReceiptMapper;
import com.ulticode.receipt.ReceiptExecutorFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.function.Function;

/** Thin Notification adapter around the owner-neutral claim receipt protocol. */
@Component
public class CommandReceiptExecutor {

    private static final String DEFAULT_SERVICE = "NotificationAdministrationService";
    private static final ReceiptErrorCatalog ERRORS = new NotificationReceiptErrors();
    private static final ReceiptFingerprintStrategy<WriteCommand> FINGERPRINTS =
            new GenericFingerprintStrategy();

    private final ReceiptExecutor<WriteCommand> delegate;

    public CommandReceiptExecutor(
            NotificationCommandReceiptMapper receiptMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        ClaimCommandReceiptStore store = receiptMapper == null
                ? null : new NotificationReceiptStore(receiptMapper);
        delegate = ReceiptExecutorFactory.claim(store, objectMapper, ERRORS, clock);
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
        return delegate.execute(service, operation, command, resultType, mutation);
    }

    public static String traceId(WriteCommand command) {
        return ReceiptExecutor.traceId(command);
    }

    public static String fingerprint(WriteCommand command) {
        return FINGERPRINTS.fingerprint(command);
    }

    private static final class NotificationReceiptStore implements ClaimCommandReceiptStore {
        private final NotificationCommandReceiptMapper mapper;

        private NotificationReceiptStore(NotificationCommandReceiptMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public int insert(ReceiptWrite receipt) {
            return mapper.insertClaim(toEntity(receipt));
        }

        @Override
        public ReceiptView findByKey(String service, String operation, String idempotencyKey) {
            return toView(mapper.findByReceiptKey(service, operation, idempotencyKey));
        }

        @Override
        public int markSuccess(String id, String resultPayload) {
            return mapper.markSuccess(id, resultPayload);
        }

        @Override
        public int deleteClaim(String id) {
            return mapper.deleteClaim(id);
        }

        private static NotificationCommandReceiptEntity toEntity(ReceiptWrite receipt) {
            NotificationCommandReceiptEntity entity = new NotificationCommandReceiptEntity();
            entity.setId(receipt.id());
            entity.setCommandId(receipt.commandId());
            entity.setService(receipt.service());
            entity.setOperation(receipt.operation());
            entity.setIdempotencyKey(receipt.idempotencyKey());
            entity.setRequestFingerprint(receipt.requestFingerprint());
            entity.setStatus(receipt.status());
            entity.setResultPayload(receipt.resultPayload());
            entity.setActorType(receipt.actorType());
            entity.setActorId(receipt.actorId());
            entity.setTraceId(receipt.traceId());
            entity.setCreatedAt(receipt.createdAt());
            return entity;
        }

        private static ReceiptView toView(NotificationCommandReceiptEntity entity) {
            if (entity == null) {
                return null;
            }
            return new ReceiptView(
                    entity.getId(), entity.getCommandId(), entity.getService(), entity.getOperation(),
                    entity.getIdempotencyKey(), entity.getRequestFingerprint(), entity.getStatus(),
                    entity.getResultPayload(), entity.getActorType(), entity.getActorId(), entity.getTraceId());
        }
    }

    private static final class NotificationReceiptErrors implements ReceiptErrorCatalog {
        @Override
        public NotificationErrorCode invalidCommand() {
            return NotificationErrorCode.BAD_REQUEST;
        }

        @Override
        public NotificationErrorCode keyConflict() {
            return NotificationErrorCode.IDEMPOTENCY_KEY_CONFLICT;
        }

        @Override
        public NotificationErrorCode processingDuplicate() {
            return NotificationErrorCode.UNEXPECTED_NOTIFICATION_STATE;
        }

        @Override
        public NotificationErrorCode missingReceipt() {
            return NotificationErrorCode.UNEXPECTED_NOTIFICATION_STATE;
        }

        @Override
        public NotificationErrorCode replayFailure() {
            return NotificationErrorCode.UNEXPECTED_NOTIFICATION_STATE;
        }
    }
}
