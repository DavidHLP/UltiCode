package com.ulticode.common.exception;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-module regression: legacy {@link ErrorCode} must mirror the
 * {@link BaseErrorCode} literals and implement {@link NamespacedErrorCode} (P2-DISC-001).
 */
@DisplayName("ErrorCode ⇄ BaseErrorCode delegation")
class ErrorCodeDelegationTest {

    @Test
    @DisplayName("legacy ErrorCode mirrors BaseErrorCode for every generic code")
    void delegationMirrorsBaseErrorCode() {
        // SUCCESS
        assertThat(ErrorCode.SUCCESS.getCode()).isEqualTo(BaseErrorCode.SUCCESS.code());
        assertThat(ErrorCode.SUCCESS.getMessage()).isEqualTo(BaseErrorCode.SUCCESS.message());

        // 4xx ladder
        assertThat(ErrorCode.BAD_REQUEST.getCode()).isEqualTo(BaseErrorCode.BAD_REQUEST.code());
        assertThat(ErrorCode.VALIDATION_FAILED.getCode())
                .isEqualTo(BaseErrorCode.VALIDATION_FAILED.code());
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo(BaseErrorCode.UNAUTHORIZED.code());
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo(BaseErrorCode.FORBIDDEN.code());
        assertThat(ErrorCode.NOT_FOUND.getCode()).isEqualTo(BaseErrorCode.NOT_FOUND.code());
        assertThat(ErrorCode.METHOD_NOT_ALLOWED.getCode())
                .isEqualTo(BaseErrorCode.METHOD_NOT_ALLOWED.code());
        assertThat(ErrorCode.CONFLICT.getCode()).isEqualTo(BaseErrorCode.CONFLICT.code());
        assertThat(ErrorCode.TOO_MANY_REQUESTS.getCode())
                .isEqualTo(BaseErrorCode.TOO_MANY_REQUESTS.code());

        // 5xx ladder
        assertThat(ErrorCode.UNKNOWN_ERROR.getCode())
                .isEqualTo(BaseErrorCode.UNKNOWN_ERROR.code());
        assertThat(ErrorCode.DATABASE_ERROR.getCode())
                .isEqualTo(BaseErrorCode.DATABASE_ERROR.code());

        // Messages must also mirror, byte-for-byte.
        assertThat(ErrorCode.VALIDATION_FAILED.getMessage())
                .isEqualTo(BaseErrorCode.VALIDATION_FAILED.message());
        assertThat(ErrorCode.NOT_FOUND.getMessage())
                .isEqualTo(BaseErrorCode.NOT_FOUND.message());
        assertThat(ErrorCode.DATABASE_ERROR.getMessage())
                .isEqualTo(BaseErrorCode.DATABASE_ERROR.message());
    }

    @Test
    @DisplayName("P2-DISC-001: ErrorCode implements NamespacedErrorCode and maps namespaces correctly")
    void implementsNamespacedErrorCode() {
        assertThat(ErrorCode.SUCCESS).isInstanceOf(NamespacedErrorCode.class);
        assertThat(ErrorCode.SUCCESS.namespace()).isEqualTo("common");
        assertThat(ErrorCode.AUTH_INVALID_CREDENTIALS.namespace()).isEqualTo("auth");
        assertThat(ErrorCode.USER_NOT_FOUND.namespace()).isEqualTo("user");
        assertThat(ErrorCode.PROBLEM_NOT_FOUND.namespace()).isEqualTo("problem");

        assertThat(ErrorCode.AUTH_INVALID_CREDENTIALS.code()).isEqualTo(10001);
        assertThat(ErrorCode.AUTH_INVALID_CREDENTIALS.message()).isEqualTo("Invalid credentials");
    }

    @Test
    @DisplayName("P2-DISC-001: BusinessException in backend-legacy extends backend-common BusinessException")
    void legacyBusinessExceptionExtendsCommon() {
        BusinessException ex = new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "custom msg");
        assertThat(ex).isInstanceOf(com.ulticode.common.exception.BusinessException.class);
        assertThat(ex.getCode()).isEqualTo(10001);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
        assertThat(ex.getMessage()).isEqualTo("custom msg");
        assertThat(ex.getHttpStatus().value()).isEqualTo(401);
    }
}
