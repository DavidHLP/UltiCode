package com.ulticode.submission.api.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.event.IntegrationEventEnvelopeContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionLifecycleEventContractTest {

    @Test
    void requiredJudgedLongPreservesSupportedNumericAndStringForms() {
        for (Object value : new Object[]{(byte) 7, (short) 7, 7, 7L,
                BigInteger.valueOf(7), new BigDecimal("7.00"), 7.0f, 7.0d, "7", "+7"}) {
            assertThat(SubmissionLifecycleEventContract.requiredJudgedLong(
                    Map.of("generation", value), "generation"))
                    .as("generation from %s (%s)", value, value.getClass().getSimpleName())
                    .isEqualTo(7L);
        }
        for (Object value : new Object[]{0, 0L, BigInteger.ZERO, BigDecimal.ZERO, 0.0f, 0.0d, "0", "-0"}) {
            assertThat(SubmissionLifecycleEventContract.requiredJudgedLong(
                    Map.of("generation", value), "generation")).isZero();
        }
        for (Object value : new Object[]{Long.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE),
                BigDecimal.valueOf(Long.MAX_VALUE), Long.toString(Long.MAX_VALUE)}) {
            assertThat(SubmissionLifecycleEventContract.requiredJudgedLong(
                    Map.of("generation", value), "generation")).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Test
    void requiredJudgedLongRejectsMissingAndNullWithoutACause() {
        assertThatThrownBy(() -> SubmissionLifecycleEventContract.requiredJudgedLong(Map.of(), "generation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing SubmissionJudged field: generation")
                .hasNoCause();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generation", null);
        assertThatThrownBy(() -> SubmissionLifecycleEventContract.requiredJudgedLong(payload, "generation"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing SubmissionJudged field: generation")
                .hasNoCause();
    }

    @Test
    void requiredJudgedLongRejectsNegativeIntegersWithoutACause() {
        for (Object value : new Object[]{-1, -1L, BigInteger.valueOf(-1), new BigDecimal("-1.0"),
                -1.0f, -1.0d, "-1", Long.MIN_VALUE, Long.toString(Long.MIN_VALUE)}) {
            assertThatThrownBy(() -> SubmissionLifecycleEventContract.requiredJudgedLong(
                    Map.of("generation", value), "generation"))
                    .as("negative generation from %s (%s)", value, value.getClass().getSimpleName())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Negative SubmissionJudged field: generation")
                    .hasNoCause();
        }
    }

    @Test
    void requiredJudgedLongRejectsFractionalAndOverflowNumbersWithArithmeticCause() {
        for (Object value : new Object[]{new BigDecimal("0.5"), 0.5f, 0.5d, -0.5d,
                new BigDecimal("-0.5"), BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                BigDecimal.valueOf(Long.MAX_VALUE).add(BigDecimal.ONE),
                BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)}) {
            assertThatThrownBy(() -> SubmissionLifecycleEventContract.requiredJudgedLong(
                    Map.of("generation", value), "generation"))
                    .as("invalid generation from %s (%s)", value, value.getClass().getSimpleName())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid SubmissionJudged field: generation")
                    .hasCauseInstanceOf(ArithmeticException.class);
        }
    }

    @Test
    void requiredJudgedLongRejectsInvalidStringsAndNonFiniteNumbersWithNumberFormatCause() {
        for (Object value : new Object[]{"1.0", "1e3", "", " ", " 1", "1 ", "invalid",
                "9223372036854775808", "-9223372036854775809", Float.NaN, Double.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> SubmissionLifecycleEventContract.requiredJudgedLong(
                    Map.of("generation", value), "generation"))
                    .as("invalid generation from %s (%s)", value, value.getClass().getSimpleName())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid SubmissionJudged field: generation")
                    .hasCauseInstanceOf(NumberFormatException.class);
        }
    }

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
