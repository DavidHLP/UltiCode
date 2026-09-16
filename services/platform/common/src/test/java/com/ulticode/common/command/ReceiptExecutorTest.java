package com.ulticode.common.command;

import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptExecutorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-16T00:00:00Z"), ZoneOffset.UTC);
    private static final ReceiptErrorCatalog ERRORS = new TestErrors();

    private FakeStore store;
    private ReceiptExecutor<TestCommand> executor;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        executor = new ReceiptExecutor<>(
                ReceiptExecutionMode.CLAIM_MUTATE_FINALIZE,
                store,
                new StringCodec(),
                new GenericFingerprintStrategy(),
                ERRORS,
                ReceiptCommandMetadata::from,
                ReceiptExecutorTest::valid,
                CLOCK);
    }

    @Test
    void claimMutationAndFinalizeHappenOnce() {
        TestCommand command = command("claim-key", "item-a");
        AtomicInteger mutations = new AtomicInteger();

        RpcResult<String> result = executor.execute(
                "TestService", "create", command, String.class,
                traceId -> {
                    mutations.incrementAndGet();
                    return RpcResult.success("created", traceId);
                });

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo("created");
        assertThat(mutations.get()).isEqualTo(1);
        assertThat(store.receipt("TestService", "create", "claim-key").status())
                .isEqualTo("SUCCESS");
        assertThat(store.receipt("TestService", "create", "claim-key").resultPayload())
                .isEqualTo("created");
    }

    @Test
    void successfulDuplicateReplaysWithoutMutation() {
        TestCommand command = command("replay-key", "item-a");
        AtomicInteger mutations = new AtomicInteger();
        executor.execute("TestService", "create", command, String.class, traceId -> {
            mutations.incrementAndGet();
            return RpcResult.success("stored-result", traceId);
        });

        RpcResult<String> replay = executor.execute(
                "TestService", "create", command, String.class,
                traceId -> {
                    mutations.incrementAndGet();
                    return RpcResult.success("wrong-result", traceId);
                });

        assertThat(replay.success()).isTrue();
        assertThat(replay.data()).isEqualTo("stored-result");
        assertThat(mutations.get()).isEqualTo(1);
    }
    @Test
    void reusedKeyWithDifferentCommandConflicts() {
        TestCommand original = command("shared-key", "item-a");
        executor.execute("TestService", "create", original, String.class,
                traceId -> RpcResult.success("stored-result", traceId));

        RpcResult<String> conflict = executor.execute(
                "TestService", "create", command("shared-key", "item-b"), String.class,
                traceId -> RpcResult.success("must-not-run", traceId));

        assertThat(conflict.success()).isFalse();
        assertThat(conflict.error().code()).isEqualTo(TestError.KEY_CONFLICT.code());
    }

    @Test
    void failedMutationDeletesProcessingClaim() {
        TestCommand command = command("failure-key", "item-a");

        RpcResult<String> failure = executor.execute(
                "TestService", "create", command, String.class,
                traceId -> RpcResult.failure(TestError.MUTATION_FAILURE, traceId));

        assertThat(failure.success()).isFalse();
        assertThat(store.receipt("TestService", "create", "failure-key")).isNull();
        assertThat(store.deletedIds).hasSize(1);
    }

    @Test
    void mutateThenRecordPersistsPayloadForAuthReplay() {
        ReceiptExecutor<TestCommand> authExecutor = new ReceiptExecutor<>(
                ReceiptExecutionMode.MUTATE_THEN_RECORD,
                store,
                new StringCodec(),
                new GenericFingerprintStrategy(),
                ERRORS,
                ReceiptCommandMetadata::from,
                ReceiptExecutorTest::valid,
                CLOCK);
        TestCommand command = command("auth-key", "account-a");
        AtomicInteger mutations = new AtomicInteger();

        RpcResult<String> first = authExecutor.execute(
                "AuthService", "changeState", command, String.class,
                traceId -> {
                    mutations.incrementAndGet();
                    return RpcResult.success("auth-result", traceId);
                });
        RpcResult<String> replay = authExecutor.execute(
                "AuthService", "changeState", command, String.class,
                traceId -> {
                    mutations.incrementAndGet();
                    return RpcResult.success("wrong-result", traceId);
                });

        assertThat(first.data()).isEqualTo("auth-result");
        assertThat(replay.data()).isEqualTo("auth-result");
        assertThat(mutations.get()).isEqualTo(1);
        assertThat(store.receipt("AuthService", "changeState", "auth-key").resultPayload())
                .isEqualTo("auth-result");
    }

    private static boolean valid(TestCommand command) {
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

    private static TestCommand command(String key, String item) {
        return new TestCommand(
                "command-" + key,
                IdMetadata.of(key, null),
                new ActorDelegation("ADMIN", "actor-a", "delegator-a", null),
                new TraceMetadata("trace-a", null, null, null),
                item);
    }

    private record TestCommand(
            String commandId,
            IdMetadata idempotency,
            ActorDelegation actor,
            TraceMetadata trace,
            String item) implements WriteCommand {
    }

    private static final class StringCodec implements ReceiptPayloadCodec {
        @Override
        public String encode(Object value) {
            return String.valueOf(value);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T decode(String payload, Class<T> resultType) {
            return (T) payload;
        }
    }

    private static final class FakeStore implements CommandReceiptStore {
        private final Map<String, ReceiptView> receipts = new HashMap<>();
        private final java.util.List<String> deletedIds = new java.util.ArrayList<>();

        @Override
        public int insertClaim(ReceiptWrite receipt) {
            String key = key(receipt.service(), receipt.operation(), receipt.idempotencyKey());
            if (receipts.containsKey(key)) {
                return 0;
            }
            receipts.put(key, new ReceiptView(
                    receipt.id(), receipt.commandId(), receipt.service(), receipt.operation(),
                    receipt.idempotencyKey(), receipt.requestFingerprint(), receipt.status(),
                    receipt.resultPayload(), receipt.actorType(), receipt.actorId(), receipt.traceId()));
            return 1;
        }

        @Override
        public ReceiptView findByKey(String service, String operation, String idempotencyKey) {
            return receipts.get(key(service, operation, idempotencyKey));
        }

        @Override
        public int markSuccess(String id, String resultPayload) {
            for (Map.Entry<String, ReceiptView> entry : receipts.entrySet()) {
                ReceiptView receipt = entry.getValue();
                if (receipt.id().equals(id) && "PROCESSING".equals(receipt.status())) {
                    entry.setValue(new ReceiptView(
                            receipt.id(), receipt.commandId(), receipt.service(), receipt.operation(),
                            receipt.idempotencyKey(), receipt.requestFingerprint(), "SUCCESS", resultPayload,
                            receipt.actorType(), receipt.actorId(), receipt.traceId()));
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int deleteClaim(String id) {
            String foundKey = null;
            for (Map.Entry<String, ReceiptView> entry : receipts.entrySet()) {
                if (entry.getValue().id().equals(id) && "PROCESSING".equals(entry.getValue().status())) {
                    foundKey = entry.getKey();
                    break;
                }
            }
            if (foundKey == null) {
                return 0;
            }
            receipts.remove(foundKey);
            deletedIds.add(id);
            return 1;
        }

        private ReceiptView receipt(String service, String operation, String idempotencyKey) {
            return findByKey(service, operation, idempotencyKey);
        }

        private static String key(String service, String operation, String idempotencyKey) {
            return service + "\u0000" + operation + "\u0000" + idempotencyKey;
        }
    }

    private enum TestError implements NamespacedErrorCode {
        INVALID(40000), KEY_CONFLICT(40900), PROCESSING(40901), MISSING(50000),
        REPLAY(50001), MUTATION_FAILURE(50002);

        private final int code;

        TestError(int code) {
            this.code = code;
        }

        @Override
        public String namespace() {
            return "test";
        }

        @Override
        public int code() {
            return code;
        }

        @Override
        public String message() {
            return name();
        }
    }

    private static final class TestErrors implements ReceiptErrorCatalog {
        @Override
        public NamespacedErrorCode invalidCommand() {
            return TestError.INVALID;
        }

        @Override
        public NamespacedErrorCode keyConflict() {
            return TestError.KEY_CONFLICT;
        }

        @Override
        public NamespacedErrorCode processingDuplicate() {
            return TestError.PROCESSING;
        }

        @Override
        public NamespacedErrorCode missingReceipt() {
            return TestError.MISSING;
        }

        @Override
        public NamespacedErrorCode replayFailure() {
            return TestError.REPLAY;
        }
    }
}
