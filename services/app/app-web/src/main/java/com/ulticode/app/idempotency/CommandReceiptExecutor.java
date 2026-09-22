package com.ulticode.app.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.command.ClaimCommandReceiptStore;
import com.ulticode.common.command.GenericFingerprintStrategy;
import com.ulticode.common.command.ReceiptErrorCatalog;
import com.ulticode.common.command.ReceiptExecutor;
import com.ulticode.common.command.ReceiptFingerprintStrategy;
import com.ulticode.common.command.ReceiptView;
import com.ulticode.common.command.ReceiptWrite;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.receipt.ClaimCommandReceiptStoreBridge;
import com.ulticode.receipt.ReceiptExecutorFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.function.Function;

/** Thin App adapter around the owner-neutral claim receipt protocol. */
@Component
public class CommandReceiptExecutor {

    private static final String DEFAULT_SERVICE = "SubmissionAdministrationService";
    private static final ReceiptErrorCatalog ERRORS = new AppReceiptErrors();
    private static final ReceiptFingerprintStrategy<WriteCommand> FINGERPRINTS =
            new AppFingerprintStrategy();

    private final ReceiptExecutor<WriteCommand> delegate;

    public CommandReceiptExecutor(
            AppCommandReceiptMapper receiptMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        ClaimCommandReceiptStore store = receiptMapper == null
                ? null : new AppReceiptStore(receiptMapper);
        delegate = ReceiptExecutorFactory.claim(store, objectMapper, FINGERPRINTS, ERRORS, clock);
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

    /** Returns the generic fingerprint used for every newly claimed receipt. */
    public static String fingerprint(WriteCommand command) {
        return FINGERPRINTS.fingerprint(command);
    }

    private static final class AppFingerprintStrategy implements ReceiptFingerprintStrategy<WriteCommand> {

        private static final GenericFingerprintStrategy GENERIC = new GenericFingerprintStrategy();

        @Override
        public String fingerprint(WriteCommand command) {
            return GENERIC.fingerprint(command);
        }

        @Override
        public boolean matches(String storedFingerprint, WriteCommand command) {
            if (GENERIC.matches(storedFingerprint, command)) {
                return true;
            }
            if (command instanceof UpdateProfileCommand profile) {
                return legacyProfileFingerprint(profile).equals(storedFingerprint);
            }
            if (command instanceof UploadAvatarCommand avatar) {
                return legacyAvatarFingerprint(avatar).equals(storedFingerprint);
            }
            return false;
        }

        private static String legacyProfileFingerprint(UpdateProfileCommand command) {
            return sha256(String.join("|",
                    nullSafe(command.accountId()),
                    nullSafe(command.name()),
                    nullSafe(command.avatar()),
                    nullSafe(command.bio()),
                    nullSafe(command.company()),
                    nullSafe(command.github()),
                    nullSafe(command.location()),
                    nullSafe(command.twitter()),
                    nullSafe(command.website()),
                    nullSafe(command.preferredLanguage())));
        }

        private static String legacyAvatarFingerprint(UploadAvatarCommand command) {
            return sha256(command.accountId() + "|" + command.avatarUrl());
        }

        private static String nullSafe(String value) {
            return value == null ? "" : value;
        }

        private static String sha256(String value) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }

    private static final class AppReceiptStore extends ClaimCommandReceiptStoreBridge<AppCommandReceiptEntity> {

        private AppReceiptStore(AppCommandReceiptMapper mapper) {
            super(
                    AppReceiptStore::toEntity,
                    AppReceiptStore::toView,
                    mapper::insertClaim,
                    mapper::findByReceiptKey,
                    mapper::markSuccess,
                    mapper::deleteClaim);
        }

        private static AppCommandReceiptEntity toEntity(ReceiptWrite receipt) {
            AppCommandReceiptEntity entity = new AppCommandReceiptEntity();
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

        private static ReceiptView toView(AppCommandReceiptEntity entity) {
            return new ReceiptView(
                    entity.getId(), entity.getCommandId(), entity.getService(), entity.getOperation(),
                    entity.getIdempotencyKey(), entity.getRequestFingerprint(), entity.getStatus(),
                    entity.getResultPayload(), entity.getActorType(), entity.getActorId(), entity.getTraceId());
        }
    }

    private static final class AppReceiptErrors implements ReceiptErrorCatalog {
        @Override
        public AppErrorCode invalidCommand() {
            return AppErrorCode.BAD_REQUEST;
        }

        @Override
        public AppErrorCode keyConflict() {
            return AppErrorCode.IDEMPOTENCY_KEY_CONFLICT;
        }

        @Override
        public AppErrorCode processingDuplicate() {
            return AppErrorCode.UNEXPECTED_APP_STATE;
        }

        @Override
        public AppErrorCode missingReceipt() {
            return AppErrorCode.UNEXPECTED_APP_STATE;
        }

        @Override
        public AppErrorCode replayFailure() {
            return AppErrorCode.UNEXPECTED_APP_STATE;
        }
    }
}
