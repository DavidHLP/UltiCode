package com.ulticode.notification.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.command.CommandReceiptStore;
import com.ulticode.common.command.GenericFingerprintStrategy;
import com.ulticode.common.command.ReceiptCommandMetadata;
import com.ulticode.common.command.ReceiptErrorCatalog;
import com.ulticode.common.command.ReceiptExecutionMode;
import com.ulticode.common.command.ReceiptExecutor;
import com.ulticode.common.command.ReceiptFingerprintStrategy;
import com.ulticode.common.command.ReceiptPayloadCodec;
import com.ulticode.common.command.ReceiptView;
import com.ulticode.common.command.ReceiptWrite;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.notification.error.NotificationErrorCode;
import com.ulticode.notification.idempotency.entity.NotificationCommandReceiptEntity;
import com.ulticode.notification.idempotency.mapper.NotificationCommandReceiptMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

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
        CommandReceiptStore store = receiptMapper == null
                ? null : new NotificationReceiptStore(receiptMapper);
        ReceiptPayloadCodec codec = new JacksonPayloadCodec(objectMapper);
        Predicate<WriteCommand> validCommand = CommandReceiptExecutor::validCommand;
        delegate = new ReceiptExecutor<>(
                ReceiptExecutionMode.CLAIM_MUTATE_FINALIZE,
                store,
                codec,
                FINGERPRINTS,
                ERRORS,
                ReceiptCommandMetadata::from,
                validCommand,
                clock);
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

    private static final class JacksonPayloadCodec implements ReceiptPayloadCodec {
        private final ObjectMapper objectMapper;

        private JacksonPayloadCodec(ObjectMapper objectMapper) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        }

        @Override
        public String encode(Object value) throws Exception {
            return objectMapper.writeValueAsString(value);
        }

        @Override
        public <T> T decode(String payload, Class<T> resultType) throws Exception {
            return objectMapper.readValue(payload, resultType);
        }
    }

    private static final class NotificationReceiptStore implements CommandReceiptStore {
        private final NotificationCommandReceiptMapper mapper;

        private NotificationReceiptStore(NotificationCommandReceiptMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public int insertClaim(ReceiptWrite receipt) {
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
