package com.ulticode.receipt;

import com.ulticode.common.command.ReceiptView;
import com.ulticode.common.command.ReceiptWrite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Receipt store bridges")
class ReceiptStoreBridgesTest {

    @Test
    void baseBridgeConvertsInsertsAndGuardsMissingEntity() {
        Map<String, FakeEntity> stored = new HashMap<>();
        CommandReceiptStoreBridge<FakeEntity> bridge = new CommandReceiptStoreBridge<>(
                FakeEntity::from,
                FakeEntity::toView,
                entity -> {
                    stored.put(entity.idempotencyKey(), entity);
                    return 1;
                },
                (service, operation, key) -> stored.get(key));

        assertEquals(1, bridge.insert(write("id-1", "key-1")));
        assertEquals("id-1", stored.get("key-1").id());
        assertEquals("PROCESSING", bridge.findByKey("svc", "op", "key-1").status());
        assertNull(bridge.findByKey("svc", "op", "missing"));
    }

    @Test
    void claimBridgeForwardsFinalizeAndDelete() {
        ClaimCommandReceiptStoreBridge<FakeEntity> bridge = new ClaimCommandReceiptStoreBridge<>(
                FakeEntity::from,
                FakeEntity::toView,
                entity -> 1,
                (service, operation, key) -> null,
                (id, payload) -> "id-1".equals(id) && "payload-1".equals(payload) ? 1 : 0,
                id -> "id-1".equals(id) ? 1 : 0);

        assertEquals(1, bridge.markSuccess("id-1", "payload-1"));
        assertEquals(0, bridge.markSuccess("id-1", "other"));
        assertEquals(1, bridge.deleteClaim("id-1"));
        assertEquals(0, bridge.deleteClaim("missing"));
        assertNull(bridge.findByKey("svc", "op", "missing"));
    }

    private static ReceiptWrite write(String id, String idempotencyKey) {
        return new ReceiptWrite(
                id, "command-1", "svc", "op", idempotencyKey, "fp", "PROCESSING", null,
                "ADMIN", "actor-1", "trace-1", LocalDateTime.of(2026, 9, 17, 0, 0));
    }

    private record FakeEntity(String id, String idempotencyKey, String status) {

        static FakeEntity from(ReceiptWrite receipt) {
            return new FakeEntity(receipt.id(), receipt.idempotencyKey(), receipt.status());
        }

        static ReceiptView toView(FakeEntity entity) {
            return new ReceiptView(
                    entity.id(), null, null, null, entity.idempotencyKey(), null,
                    entity.status(), null, null, null, null);
        }
    }
}
