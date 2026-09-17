package com.ulticode.auth.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.command.ChangeRoleCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.PermissionMutationCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.command.WriteCommand;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.idempotency.entity.AuthCommandReceiptEntity;
import com.ulticode.auth.idempotency.mapper.AuthCommandReceiptMapper;
import com.ulticode.common.command.CommandReceiptStore;
import com.ulticode.common.command.ReceiptCommandMetadata;
import com.ulticode.common.command.ReceiptErrorCatalog;
import com.ulticode.common.command.ReceiptExecutor;
import com.ulticode.common.command.ReceiptFingerprintStrategy;
import com.ulticode.common.command.ReceiptView;
import com.ulticode.common.command.ReceiptWrite;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.receipt.JacksonReceiptPayloadCodec;
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

/** Thin Auth adapter retaining the explicit mutate-then-record protocol. */
@Component
public class CommandReceiptExecutor {

    private static final ReceiptErrorCatalog ERRORS = new AuthReceiptErrors();
    private static final ReceiptFingerprintStrategy<WriteCommand> FINGERPRINTS =
            new AuthFingerprintStrategy();

    private final ReceiptExecutor<WriteCommand> delegate;

    public CommandReceiptExecutor(
            AuthCommandReceiptMapper receiptMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        CommandReceiptStore store = receiptMapper == null
                ? null : new AuthReceiptStore(receiptMapper);
        delegate = ReceiptExecutor.mutateThenRecord(
                store,
                new JacksonReceiptPayloadCodec(objectMapper),
                FINGERPRINTS,
                ERRORS,
                CommandReceiptExecutor::metadata,
                CommandReceiptExecutor::validCommand,
                clock);
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
        if (command == null || command.trace() == null
                || command.trace().traceId() == null
                || command.trace().traceId().isBlank()) {
            return "t-system";
        }
        return command.trace().traceId();
    }

    public static String fingerprint(WriteCommand command) {
        return FINGERPRINTS.fingerprint(command);
    }

    /**
     * Auth commands implement {@code auth-api}'s own marker interface rather
     * than {@link com.ulticode.common.command.WriteCommand}, so Auth keeps its
     * own metadata projection instead of {@link ReceiptCommandMetadata#from}.
     */
    private static ReceiptCommandMetadata metadata(WriteCommand command) {
        if (command == null) {
            return null;
        }
        var actor = command.actor();
        return new ReceiptCommandMetadata(
                command.commandId(),
                command.idempotency() == null ? null : command.idempotency().idempotencyKey(),
                command.trace() == null ? null : command.trace().traceId(),
                actor == null ? null : actor.actorType(),
                actor == null ? null : actor.actorId());
    }

    private static boolean validCommand(WriteCommand command) {
        return command != null
                && command.idempotency() != null
                && command.idempotency().hasKey();
    }

    private static final class AuthReceiptStore implements CommandReceiptStore {
        private final AuthCommandReceiptMapper mapper;

        private AuthReceiptStore(AuthCommandReceiptMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public int insert(ReceiptWrite receipt) {
            return mapper.insert(toEntity(receipt));
        }

        @Override
        public ReceiptView findByKey(String service, String operation, String idempotencyKey) {
            return toView(mapper.findByReceiptKey(service, operation, idempotencyKey));
        }

        private static AuthCommandReceiptEntity toEntity(ReceiptWrite receipt) {
            AuthCommandReceiptEntity entity = new AuthCommandReceiptEntity();
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

        private static ReceiptView toView(AuthCommandReceiptEntity entity) {
            if (entity == null) {
                return null;
            }
            return new ReceiptView(
                    entity.getId(), entity.getCommandId(), entity.getService(), entity.getOperation(),
                    entity.getIdempotencyKey(), entity.getRequestFingerprint(), entity.getStatus(),
                    entity.getResultPayload(), entity.getActorType(), entity.getActorId(), entity.getTraceId());
        }
    }

    private static final class AuthReceiptErrors implements ReceiptErrorCatalog {
        @Override
        public AuthErrorCode invalidCommand() {
            return AuthErrorCode.INVALID_ACCOUNT_REQUEST;
        }

        @Override
        public AuthErrorCode keyConflict() {
            return AuthErrorCode.IDEMPOTENCY_KEY_CONFLICT;
        }

        @Override
        public AuthErrorCode processingDuplicate() {
            return AuthErrorCode.UNEXPECTED_AUTH_STATE;
        }

        @Override
        public AuthErrorCode missingReceipt() {
            return AuthErrorCode.UNEXPECTED_AUTH_STATE;
        }

        @Override
        public AuthErrorCode replayFailure() {
            return AuthErrorCode.UNEXPECTED_AUTH_STATE;
        }
    }

    private static final class AuthFingerprintStrategy implements ReceiptFingerprintStrategy<WriteCommand> {
        private static final char FIELD_SEPARATOR = '\u001f';

        @Override
        public String fingerprint(WriteCommand command) {
            return fingerprint(command, true);
        }

        @Override
        public boolean matches(String storedFingerprint, WriteCommand command) {
            return fingerprint(command).equals(storedFingerprint)
                    || fingerprint(command, false).equals(storedFingerprint);
        }

        private static String fingerprint(WriteCommand command, boolean includeExpectedVersion) {
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
                payload = includeExpectedVersion
                        ? join(value.accountId(), value.expectedVersion(), value.action(), value.rationale())
                        : join(value.accountId(), value.action(), value.rationale());
            } else if (command instanceof PermissionMutationCommand value) {
                payload = includeExpectedVersion
                        ? join(value.accountId(), value.operation(), value.action(),
                        value.resource(), value.expiresAt(), value.expectedVersion(),
                        value.actor().actorType(), value.actorId(), value.actor().delegatorId(), value.rationale())
                        : join(value.accountId(), value.operation(), value.action(), value.resource(),
                        value.expiresAt(), value.actor().actorType(), value.actorId(),
                        value.actor().delegatorId(), value.rationale());
            } else if (command instanceof ChangeRoleCommand value) {
                payload = includeExpectedVersion
                        ? join(value.accountId(), value.role(), value.expectedVersion(),
                        value.actor().actorType(), value.actor().actorId(),
                        value.actor().delegatorId(), value.rationale())
                        : join(value.accountId(), value.role(), value.actor().actorType(),
                        value.actor().actorId(), value.actor().delegatorId(), value.rationale());
            } else {
                throw new IllegalArgumentException(
                        "Unsupported write command for idempotency fingerprint: "
                                + command.getClass().getName());
            }
            return sha256(command.getClass().getName() + FIELD_SEPARATOR + payload);
        }

        private static String join(Object... values) {
            return Arrays.stream(values)
                    .map(AuthFingerprintStrategy::encode)
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
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }
}
