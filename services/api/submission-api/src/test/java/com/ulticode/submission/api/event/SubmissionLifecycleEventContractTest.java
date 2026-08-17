package com.ulticode.submission.api.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.event.IntegrationEventEnvelopeContract;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionLifecycleEventContractTest {

    @Test
    void freezesSubmissionOwnerAndEventShapes() {
        assertThat(SubmissionLifecycleEventContract.SCHEMA_VERSION).isEqualTo(1);
        assertThat(SubmissionLifecycleEventContract.OWNER).isEqualTo("Submission");
        assertThat(SubmissionLifecycleEventContract.ENVELOPE_FIELDS)
                .isEqualTo(IntegrationEventEnvelopeContract.FIELDS);
        assertThat(SubmissionLifecycleEventContract.CREATED_EVENT_TYPE)
                .isEqualTo("SubmissionCreated");
        assertThat(SubmissionLifecycleEventContract.JUDGED_EVENT_TYPE)
                .isEqualTo("SubmissionJudged");
        assertThat(SubmissionLifecycleEventContract.JUDGED_FIELDS)
                .containsExactlyInAnyOrder(
                        "submissionId", "userId", "problemId", "contestId",
                        "generation", "verdict", "runtimeMs", "memoryMb");
    }

    @Test
    void freezesJudgedPayloadWireShapeAsJson() throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put(IntegrationEventEnvelopeContract.EVENT_ID, "outbox-1");
        envelope.put(IntegrationEventEnvelopeContract.OWNER, "App");
        envelope.put(IntegrationEventEnvelopeContract.EVENT_TYPE,
                SubmissionLifecycleEventContract.JUDGED_EVENT_TYPE);
        envelope.put(IntegrationEventEnvelopeContract.SCHEMA_VERSION, 1);
        envelope.put(IntegrationEventEnvelopeContract.AGGREGATE_ID, "sub-1");
        envelope.put(IntegrationEventEnvelopeContract.AGGREGATE_VERSION, 3L);
        envelope.put(IntegrationEventEnvelopeContract.CAUSATION_ID, null);
        envelope.put(IntegrationEventEnvelopeContract.TRACE_ID, "t-1");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("submissionId", "sub-1");
        payload.put("generation", 3L);
        payload.put("userId", "user-1");
        payload.put("problemId", 42L);
        payload.put("verdict", "Accepted");
        payload.put("runtimeMs", 12);
        payload.put("memoryMb", 1.5);
        payload.put("contestId", "contest-1");
        envelope.put(IntegrationEventEnvelopeContract.PAYLOAD, payload);

        ObjectMapper mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> node = mapper.readValue(
                mapper.writeValueAsBytes(envelope), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadNode =
                (Map<String, Object>) node.get(IntegrationEventEnvelopeContract.PAYLOAD);

        assertThat(node.keySet())
                .containsExactlyInAnyOrderElementsOf(
                        IntegrationEventEnvelopeContract.FIELDS);
        assertThat(payloadNode)
                .containsOnlyKeys(SubmissionLifecycleEventContract.JUDGED_FIELDS);
        assertThat(payloadNode.get("verdict")).isEqualTo("Accepted");
    }
}
