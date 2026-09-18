package com.ulticode.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.ClaimCommandReceiptStore;
import com.ulticode.common.command.ReceiptErrorCatalog;
import com.ulticode.common.command.ReceiptExecutor;
import com.ulticode.common.command.ReceiptView;
import com.ulticode.common.command.ReceiptWrite;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Receipt executor factory claim profile")
class ReceiptExecutorFactoryTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-17T00:00:00Z"), ZoneOffset.UTC);

    private FakeStore store;
    private ReceiptExecutor<TestCommand> executor;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        executor = ReceiptExecutorFactory.claim(store, new ObjectMapper(), new TestErrors(), CLOCK);
    }

    @Test
    void claimRunsOnceAndReplaysJsonPayload() {
        TestCommand command = command("key-1", "item-a");
        AtomicInteger mutations = new AtomicInteger();

        RpcResult<Payload> first = executor.execute(
                "TestService", "create", command, Payload.class,
                traceId -> {
                    mutations.incrementAndGet();
                    return RpcResult.success(new Payload("result-a", 1), traceId);
                });
        RpcResult<Payload> replay = executor.execute(
                "TestService", "create", command, Payload.class,
                traceId -> {
                    mutations.incrementAndGet();
                    return RpcResult.success(new Payload("wrong", 2), traceId);
                });

        assertTrue(first.success());
        assertEquals(new Payload("result-a", 1), first.data());
        assertEquals(new Payload("result-a", 1), replay.data());
        assertEquals(1, mutations.get());
        assertEquals("SUCCESS", store.receipt("TestService", "create", "key-1").status());
    }

    @Test
    void rejectsCommandWithoutDelegator() {
        AtomicInteger mutations = new AtomicInteger();
        TestCommand undelegated = new TestCommand(
                "command-2",
                IdMetadata.of("key-2", null),
                new ActorDelegation("ADMIN", "actor-1", null, null),
                new TraceMetadata("trace-1", null, null, null),
                "item-a");

        RpcResult<Payload> result = executor.execute(
                "TestService", "create", undelegated, Payload.class,
                traceId -> {
                    mutations.incrementAndGet();
                    return RpcResult.success(new Payload("never", 0), traceId);
                });

        assertFalse(result.success());
        assertEquals(TestError.INVALID.code(), result.error().code());
        assertEquals(0, mutations.get());
        assertNull(store.receipt("TestService", "create", "key-2"));
    }

    private static TestCommand command(String key, String item) {
        return new TestCommand(
                "command-" + key,
                IdMetadata.of(key, null),
                new ActorDelegation("ADMIN", "actor-1", "delegator-1", null),
                new TraceMetadata("trace-1", null, null, null),
                item);
    }

    /**
     * Public so the shared fingerprint strategy can invoke its record
     * accessors reflectively from another package.
     */
    public record TestCommand(
            String commandId,
            IdMetadata idempotency,
            ActorDelegation actor,
            TraceMetadata trace,
            String item) implements WriteCommand {
    }

    record Payload(String id, int count) {
    }

    private static final class FakeStore implements ClaimCommandReceiptStore {
        private final Map<String, ReceiptView> receipts = new HashMap<>();

        @Override
        public int insert(ReceiptWrite receipt) {
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
                if (entry.getValue().id().equals(id)) {
                    foundKey = entry.getKey();
                    break;
                }
            }
            if (foundKey == null) {
                return 0;
            }
            receipts.remove(foundKey);
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
