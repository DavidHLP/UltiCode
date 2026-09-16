package com.ulticode.common.command;

import com.ulticode.common.rpc.RpcResult;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Plain, owner-neutral receipt protocol. Persistence, payload serialization,
 * fingerprints, and errors are supplied by the owner adapter.
 *
 * @param <C> owner command type; it may be the shared command contract or an
 *            owner-local compatible command contract
 */
public final class ReceiptExecutor<C> {

    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCESS = "SUCCESS";
    private static final String DEFAULT_TRACE_ID = "t-system";

    private final ReceiptExecutionMode mode;
    private final CommandReceiptStore store;
    private final ReceiptPayloadCodec payloadCodec;
    private final ReceiptFingerprintStrategy<? super C> fingerprintStrategy;
    private final ReceiptErrorCatalog errors;
    private final Function<? super C, ReceiptCommandMetadata> metadataExtractor;
    private final Predicate<? super C> validCommand;
    private final Clock clock;

    public ReceiptExecutor(
            ReceiptExecutionMode mode,
            CommandReceiptStore store,
            ReceiptPayloadCodec payloadCodec,
            ReceiptFingerprintStrategy<? super C> fingerprintStrategy,
            ReceiptErrorCatalog errors,
            Function<? super C, ReceiptCommandMetadata> metadataExtractor,
            Predicate<? super C> validCommand,
            Clock clock) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.store = store;
        this.payloadCodec = Objects.requireNonNull(payloadCodec, "payloadCodec");
        this.fingerprintStrategy = Objects.requireNonNull(fingerprintStrategy, "fingerprintStrategy");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.metadataExtractor = Objects.requireNonNull(metadataExtractor, "metadataExtractor");
        this.validCommand = Objects.requireNonNull(validCommand, "validCommand");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Executes a mutation according to the configured protocol.
     *
     * <p>Mutation exceptions intentionally propagate so an owner transaction
     * boundary can roll back both the business write and its receipt state.</p>
     */
    public <T> RpcResult<T> execute(
            String service,
            String operation,
            C command,
            Class<T> resultType,
            Function<String, RpcResult<T>> mutation) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(mutation, "mutation");

        ReceiptCommandMetadata metadata = metadataExtractor.apply(command);
        String traceId = traceId(metadata == null ? null : metadata.traceId());
        String key = metadata == null ? null : metadata.idempotencyKey();
        if (!validCommand.test(command)) {
            return withKey(RpcResult.failure(errors.invalidCommand(), traceId), key);
        }

        String fingerprint = fingerprintStrategy.fingerprint(command);
        if (mode == ReceiptExecutionMode.MUTATE_THEN_RECORD) {
            return executeMutateThenRecord(
                    service, operation, command, resultType, mutation, metadata, fingerprint, traceId);
        }
        return executeClaimMutateFinalize(
                service, operation, command, resultType, mutation, metadata, fingerprint, traceId);
    }

    private <T> RpcResult<T> executeMutateThenRecord(
            String service,
            String operation,
            C command,
            Class<T> resultType,
            Function<String, RpcResult<T>> mutation,
            ReceiptCommandMetadata metadata,
            String fingerprint,
            String traceId) {
        String key = metadata.idempotencyKey();
        if (store != null) {
            ReceiptView existing = store.findByKey(service, operation, key);
            if (existing != null) {
                if (existing.requestFingerprint() != null
                        && !fingerprintStrategy.matches(existing.requestFingerprint(), command)) {
                    return RpcResult.failure(errors.keyConflict(), traceId);
                }
                if (!SUCCESS.equals(existing.status())) {
                    return RpcResult.failure(errors.processingDuplicate(), traceId);
                }
                try {
                    T result = payloadCodec.decode(existing.resultPayload(), resultType);
                    return RpcResult.success(result, traceId);
                } catch (Exception exception) {
                    return RpcResult.failure(errors.replayFailure(), traceId);
                }
            }
        }

        RpcResult<T> result = Objects.requireNonNull(
                mutation.apply(traceId), "mutation result must not be null");
        if (store != null && result.success() && result.data() != null) {
            String payload;
            try {
                payload = payloadCodec.encode(result.data());
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to serialize command receipt", exception);
            }
            int inserted = store.insertClaim(newWrite(
                    service, operation, command, metadata, fingerprint, SUCCESS, payload, traceId));
            if (inserted == 0 && key != null && !key.isBlank()) {
                // A concurrent command already recorded this key. Its receipt is
                // authoritative, so mirror the claim protocol instead of reporting
                // a success whose receipt was never persisted.
                ReceiptView recorded = store.findByKey(service, operation, key);
                if (recorded == null) {
                    return withKey(RpcResult.failure(errors.missingReceipt(), traceId), key);
                }
                if (recorded.requestFingerprint() != null
                        && !fingerprintStrategy.matches(recorded.requestFingerprint(), command)) {
                    return withKey(RpcResult.failure(errors.keyConflict(), traceId), key);
                }
                if (!SUCCESS.equals(recorded.status())) {
                    return withKey(RpcResult.failure(errors.processingDuplicate(), traceId), key);
                }
                try {
                    T recordedResult = payloadCodec.decode(recorded.resultPayload(), resultType);
                    return withKey(RpcResult.success(recordedResult, traceId), key);
                } catch (Exception exception) {
                    return withKey(RpcResult.failure(errors.replayFailure(), traceId), key);
                }
            }
        }
        return result;
    }

    private <T> RpcResult<T> executeClaimMutateFinalize(
            String service,
            String operation,
            C command,
            Class<T> resultType,
            Function<String, RpcResult<T>> mutation,
            ReceiptCommandMetadata metadata,
            String fingerprint,
            String traceId) {
        if (store == null) {
            throw new IllegalStateException("Receipt store is required for claim mode");
        }
        String key = metadata.idempotencyKey();
        ReceiptWrite claim = newWrite(
                service, operation, command, metadata, fingerprint, PROCESSING, null, traceId);
        if (store.insertClaim(claim) == 0) {
            ReceiptView existing = store.findByKey(service, operation, key);
            if (existing == null) {
                return withKey(RpcResult.failure(errors.missingReceipt(), traceId), key);
            }
            return replay(existing, fingerprint, resultType, traceId, key, command);
        }

        RpcResult<T> result = withKey(
                Objects.requireNonNull(mutation.apply(traceId), "mutation result must not be null"), key);
        if (!result.success()) {
            // Checked, but a zero-row delete only means the claim is already gone
            // or was reclaimed by another worker; the business failure result
            // remains the authoritative outcome.
            store.deleteClaim(claim.id());
            return result;
        }

        try {
            String payload = payloadCodec.encode(result.data());
            if (store.markSuccess(claim.id(), payload) != 1) {
                throw new IllegalStateException("Unable to finalize command receipt");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize command receipt", exception);
        }
        return result;
    }

    private <T> RpcResult<T> replay(
            ReceiptView existing,
            String fingerprint,
            Class<T> resultType,
            String traceId,
            String key,
            C command) {
        if (existing.requestFingerprint() != null
                && !fingerprintStrategy.matches(existing.requestFingerprint(), command)) {
            return withKey(RpcResult.failure(errors.keyConflict(), traceId), key);
        }
        if (!SUCCESS.equals(existing.status())) {
            return withKey(RpcResult.failure(errors.processingDuplicate(), traceId), key);
        }
        if (resultType == Void.class) {
            return withKey(new RpcResult<>(true, null, null, null, traceId, 0L, key), key);
        }
        try {
            T result = payloadCodec.decode(existing.resultPayload(), resultType);
            return RpcResult.success(result, traceId, 0L, key);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to replay command receipt", exception);
        }
    }

    private ReceiptWrite newWrite(
            String service,
            String operation,
            C command,
            ReceiptCommandMetadata metadata,
            String fingerprint,
            String status,
            String resultPayload,
            String traceId) {
        return new ReceiptWrite(
                UUID.randomUUID().toString(),
                metadata.commandId(),
                service,
                operation,
                metadata.idempotencyKey(),
                fingerprint,
                status,
                resultPayload,
                metadata.actorType(),
                metadata.actorId(),
                traceId,
                LocalDateTime.now(clock));
    }

    /** Returns the project default trace id when a command has none. */
    public static String traceId(WriteCommand command) {
        return command == null || command.trace() == null
                ? DEFAULT_TRACE_ID : traceId(command.trace().traceId());
    }

    private static String traceId(String traceId) {
        return traceId == null || traceId.isBlank() ? DEFAULT_TRACE_ID : traceId;
    }

    private static <T> RpcResult<T> withKey(RpcResult<T> result, String key) {
        if (key == null || key.isBlank() || key.equals(result.idempotencyKey())) {
            return result;
        }
        return new RpcResult<>(
                result.success(), result.data(), result.page(), result.error(),
                result.traceId(), result.deadlineMs(), key);
    }
}
