package com.ulticode.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationEventEnvelopeContractTest {

    @Test
    void buildsTheCanonicalAuditPayloadAndEnvelope() throws Exception {
        IntegrationEventEnvelopeContract.AuditRecordedPayload payload =
                IntegrationEventEnvelopeContract.auditPayload(
                        "audit-1", "performer-1", "user-1", "UPDATE", "USER", "user-1",
                        Map.of("name", "old"), Map.of("name", "new"), "127.0.0.1", "agent",
                        "2026-09-12T15:00:00");
        Map<String, String> envelope = IntegrationEventEnvelopeContract.auditEnvelope(
                "audit-1", IntegrationEventEnvelopeContract.APP_OWNER,
                new ObjectMapper().writeValueAsString(payload));

        assertThat(envelope).containsEntry("eventId", "audit-1")
                .containsEntry("owner", "App")
                .containsEntry("eventType", "AuditRecorded")
                .containsEntry("schemaVersion", "1")
                .containsEntry("aggregateId", "audit-1")
                .containsEntry("aggregateVersion", "0");
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonPayload = new ObjectMapper().readValue(
                envelope.get("payload"), Map.class);
        assertThat(jsonPayload).containsEntry("auditId", "audit-1")
                .containsEntry("action", "UPDATE")
                .containsEntry("entityType", "USER");
        assertThat(envelope).doesNotContainKey("causationId").doesNotContainKey("traceId");
    }

    @Test
    void rejectsIncompleteAuditEnvelope() {
        assertThatThrownBy(() -> IntegrationEventEnvelopeContract.auditEnvelope(
                " ", "App", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }
}
