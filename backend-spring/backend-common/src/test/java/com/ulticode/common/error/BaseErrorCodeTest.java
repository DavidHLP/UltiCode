package com.ulticode.common.error;

import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary tests for the namespaced error-code contract. {@link RpcResult}
 * is exercised here too because {@link RpcResult.ErrorPayload} is the wire
 * projection of {@link NamespacedErrorCode} &mdash; the error base type is
 * only useful if it survives the round-trip losslessly.
 */
@DisplayName("NamespacedErrorCode / BaseErrorCode")
class BaseErrorCodeTest {

    @Nested
    @DisplayName("BaseErrorCode base contracts")
    class BaseContracts {

        @Test
        @DisplayName("Generic transport-neutral codes share a numeric ladder that survives deprecation")
        void codesCarryValues() {
            assertThat(BaseErrorCode.SUCCESS.code()).isEqualTo(0);
            assertThat(BaseErrorCode.BAD_REQUEST.code()).isEqualTo(40000);
            assertThat(BaseErrorCode.UNAUTHORIZED.code()).isEqualTo(40100);
            assertThat(BaseErrorCode.FORBIDDEN.code()).isEqualTo(40300);
            assertThat(BaseErrorCode.NOT_FOUND.code()).isEqualTo(40400);
            assertThat(BaseErrorCode.CONFLICT.code()).isEqualTo(40900);
            assertThat(BaseErrorCode.TOO_MANY_REQUESTS.code()).isEqualTo(42900);
            assertThat(BaseErrorCode.UNKNOWN_ERROR.code()).isEqualTo(50000);
            assertThat(BaseErrorCode.DATABASE_ERROR.code()).isEqualTo(50001);
        }

        @Test
        @DisplayName("BaseErrorCode's namespace is empty (sub-enums override)")
        void namespaceIsEmptyForBase() {
            for (BaseErrorCode c : BaseErrorCode.values()) {
                assertThat(c.namespace())
                        .as("BaseErrorCode.%s namespace should be empty", c.name())
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("Every BaseErrorCode carries a non-null, non-blank message")
        void messagesAreNonBlank() {
            for (BaseErrorCode c : BaseErrorCode.values()) {
                assertThat(c.message())
                        .as("BaseErrorCode.%s must have a human-readable message", c.name())
                        .isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("RpcResult projection")
    class RpcResultProjection {

        @Test
        @DisplayName("BaseErrorCode.NOT_FOUND projects to ErrorPayload with empty namespace")
        void notFoundProjectsToEmptyNamespace() {
            RpcResult.ErrorPayload p = RpcResult.ErrorPayload.of(BaseErrorCode.NOT_FOUND);
            assertThat(p.namespace()).isEmpty();
            assertThat(p.code()).isEqualTo(40400);
            assertThat(p.message()).isEqualTo("Not found");
        }

        @Test
        @DisplayName("NamespacedErrorCode from a module enum keeps its namespace")
        void namespacedCodeKeepsNamespace() {
            RpcResult.ErrorPayload p = RpcResult.ErrorPayload.of(SubmissionError.RATE_LIMITED);
            assertThat(p.namespace()).isEqualTo("submission");
            assertThat(p.code()).isEqualTo(40003);
            assertThat(p.message()).isEqualTo("Rate limited");
        }
    }

    /** Test-only enum mirroring how a module would declare its own errors. */
    private enum SubmissionError implements NamespacedErrorCode {
        RATE_LIMITED(40003, "Rate limited");

        private final int code;
        private final String message;

        SubmissionError(int code, String message) {
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
}