package com.ulticode.common.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSON-round-trip and boundary tests for {@link RpcResult}.
 *
 * <p>Why this exists: the envelope is the contract every Dubbo / Dubbo-Triple
 * provider-consumer pair agrees on. A field rename, type change, or null
 * handling bug silently breaks the wire. Round-tripping the envelope through
 * Jackson and asserting on the deserialized shape is the cheapest way to
 * prove the wire shape survives serialisation.
 */
@DisplayName("RpcResult")
class RpcResultTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Test-only {@link NamespacedErrorCode} with a stable namespace. */
    private enum SubmissionErrorCode implements NamespacedErrorCode {
        SUBMISSION_RATE_LIMITED(40003, "Rate limited"),
        SUBMISSION_NOT_FOUND(40001, "Not found");

        private final int code;
        private final String message;

        SubmissionErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public String namespace() {
            return "submission";
        }

        @Override
        public int code() {
            return code;
        }

        @Override
        public String message() {
            return message;
        }
    }

    @Nested
    @DisplayName("factory contracts")
    class FactoryContracts {

        @Test
        @DisplayName("success() payload carries the supplied data and traceId")
        void successDataCarriesPayloadAndTrace() {
            RpcResult<String> r = RpcResult.success("payload", "t-1");
            assertThat(r.success()).isTrue();
            assertThat(r.data()).isEqualTo("payload");
            assertThat(r.page()).isNull();
            assertThat(r.error()).isNull();
            assertThat(r.traceId()).isEqualTo("t-1");
        }

        @Test
        @DisplayName("success() rejects null data")
        void successRejectsNullData() {
            assertThatThrownBy(() -> RpcResult.success(null, "t-1"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("data");
        }

        @Test
        @DisplayName("unit success() exposes null data and no page/error")
        void unitSuccess() {
            RpcResult<Void> r = RpcResult.success("t-2");
            assertThat(r.success()).isTrue();
            assertThat(r.data()).isNull();
            assertThat(r.page()).isNull();
            assertThat(r.error()).isNull();
        }

        @Test
        @DisplayName("page() precomputes totalPages from total / pageSize")
        void pagePrecomputesTotalPages() {
            RpcResult<Integer> r = RpcResult.page(List.of(1, 2, 3), 27L, 1, 10, "t-3");
            assertThat(r.success()).isTrue();
            assertThat(r.page()).isNotNull();
            assertThat((List<Object>) r.page().items()).containsExactly(1, 2, 3);
            assertThat(r.page().total()).isEqualTo(27L);
            assertThat(r.page().page()).isEqualTo(1);
            assertThat(r.page().pageSize()).isEqualTo(10);
            assertThat(r.page().totalPages()).isEqualTo(3); // ceil(27/10)
        }

        @Test
        @DisplayName("page() defends against pageSize<=0 by zeroing totalPages")
        void pageZeroesTotalPagesWhenPageSizeNonPositive() {
            RpcResult<Integer> r = RpcResult.page(List.of(), 5L, 1, 0, "t-3");
            assertThat(r.page().totalPages()).isZero();
        }

        @Test
        @DisplayName("failure(NamespacedErrorCode) maps to ErrorPayload via the static factory")
        void failureMapsNamespacedErrorCodeToPayload() {
            RpcResult<Void> r = RpcResult.failure(
                    SubmissionErrorCode.SUBMISSION_RATE_LIMITED, "t-4");
            assertThat(r.success()).isFalse();
            assertThat(r.error()).isNotNull();
            assertThat(r.error().namespace()).isEqualTo("submission");
            assertThat(r.error().code()).isEqualTo(40003);
            assertThat(r.error().message()).isEqualTo("Rate limited");
        }

        @Test
        @DisplayName("failure(BaseErrorCode) maps to ErrorPayload with empty namespace")
        void failureMapsBaseErrorCodeToPayload() {
            RpcResult<Void> r = RpcResult.failure(BaseErrorCode.NOT_FOUND, "t-5");
            assertThat(r.error()).isNotNull();
            assertThat(r.error().namespace()).isEmpty();
            assertThat(r.error().code()).isEqualTo(40400);
            assertThat(r.error().message()).isEqualTo("Not found");
        }

        @Test
        @DisplayName("failure() rejects null NamespacedErrorCode")
        void failureRejectsNullErrorCode() {
            assertThatThrownBy(() -> RpcResult.failure((NamespacedErrorCode) null, "t-6"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("error");
        }

        @Test
        @DisplayName("failure() rejects null ErrorPayload")
        void failureRejectsNullPayload() {
            assertThatThrownBy(() -> RpcResult.failure((RpcResult.ErrorPayload) null, "t-6"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("error");
        }
    }

    @Nested
    @DisplayName("JSON round-trip")
    class JsonRoundTrip {

        @Test
        @DisplayName("success payload round-trips through Jackson byte for byte on scalars")
        void successPayloadRoundTrip() throws Exception {
            RpcResult<String> original = RpcResult.success("hello", "t-rt-1");
            String json = MAPPER.writeValueAsString(original);
            assertThat(json)
                    .contains("\"success\":true")
                    .contains("\"data\":\"hello\"")
                    .contains("\"traceId\":\"t-rt-1\"")
                    .doesNotContain("\"page\":")
                    .doesNotContain("\"error\":");
            RpcResult<?> decoded = MAPPER.readValue(json, RpcResult.class);
            assertThat(decoded.success()).isTrue();
            assertThat(decoded.traceId()).isEqualTo("t-rt-1");
        }

        @Test
        @DisplayName("paginated result round-trips with the Page sub-record intact")
        void pageRoundTrip() throws Exception {
            RpcResult<Integer> original = RpcResult.page(List.of(10, 20, 30), 3L, 1, 3, "t-rt-2");
            String json = MAPPER.writeValueAsString(original);
            assertThat(json).contains("\"page\":");
            assertThat(json).contains("\"totalPages\":1");
            RpcResult<?> decoded = MAPPER.readValue(json, RpcResult.class);
            assertThat(decoded.page()).isNotNull();
            assertThat((List<Object>) decoded.page().items()).containsExactly(10, 20, 30);
            assertThat(decoded.page().totalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("failure envelope round-trips ErrorPayload as a concrete record")
        void failureEnvelopeRoundTrip() throws Exception {
            RpcResult<Void> original = RpcResult.failure(
                    SubmissionErrorCode.SUBMISSION_RATE_LIMITED, "t-rt-3");
            String json = MAPPER.writeValueAsString(original);
            assertThat(json)
                    .contains("\"success\":false")
                    .contains("\"error\":{")
                    .contains("\"namespace\":\"submission\"")
                    .contains("\"code\":40003")
                    .contains("\"message\":\"Rate limited\"");
            RpcResult<?> decoded = MAPPER.readValue(json, RpcResult.class);
            assertThat(decoded.error()).isNotNull();
            assertThat(decoded.error().namespace()).isEqualTo("submission");
            assertThat(decoded.error().code()).isEqualTo(40003);
            assertThat(decoded.error().message()).isEqualTo("Rate limited");
        }

        @Test
        @DisplayName("NON_NULL serialization omits deadlineMs + idempotencyKey when unset")
        void nonNullSerializationOmitsUnsetOptionalFields() throws Exception {
            RpcResult<String> original = RpcResult.success("p", "t");
            String json = MAPPER.writeValueAsString(original);
            assertThat(json)
                    .doesNotContain("deadlineMs")
                    .doesNotContain("idempotencyKey");
        }

        @Test
        @DisplayName("deadline + idempotencyKey round-trip when supplied")
        void deadlineAndIdempotencyRoundTrip() throws Exception {
            RpcResult<String> original = RpcResult.success("p", "t", 1718000005000L, "uuid-1");
            String json = MAPPER.writeValueAsString(original);
            RpcResult<?> decoded = MAPPER.readValue(json, RpcResult.class);
            assertThat(decoded.deadlineMs()).isEqualTo(1718000005000L);
            assertThat(decoded.idempotencyKey()).isEqualTo("uuid-1");
        }
    }

    @Nested
    @DisplayName("accessor shape")
    class AccessorShape {

        @Test
        @DisplayName("record exposes auto-generated component accessors only")
        void onlyRecordAccessors() {
            // Compile-time assertion: these calls compile and succeed.
            RpcResult<String> r = RpcResult.success("x", "t-acc");
            assertThat(r.success()).isTrue();
            assertThat(r.data()).isEqualTo("x");
            assertThat(r.page()).isNull();
            assertThat(r.error()).isNull();
            assertThat(r.traceId()).isEqualTo("t-acc");
            assertThat(r.deadlineMs()).isNull();
            assertThat(r.idempotencyKey()).isNull();
        }
    }
}