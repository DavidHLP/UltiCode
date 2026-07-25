package com.ulticode.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hardcoded pin on the 11 {@link BaseErrorCode} literals.
 *
 * <p>The frontend (and the NestJS HTTP envelope legacy keeps aligned) parses
 * these integer codes on the wire. A drift here silently breaks
 * cross-service transport and frontend error mapping, so we lock the values
 * with a regression test that fires on any change.
 *
 * <p>The {@code VALIDATION_FAILED(49999, …)} outlier (which breaks the
 * HTTP-status×100 pattern used by the other 10 codes) is preserved verbatim
 * because legacy depends on it and a "fix" would silently corrupt the
 * NestJS contract. See {@code docs/MICROSERVICE_MIGRATION_GUIDE.md}.
 *
 * <p>The cross-module mirror (legacy {@code ErrorCode} ⇄
 * {@code BaseErrorCode}) is checked by
 * {@code ErrorCodeDelegationTest} in the legacy module.
 */
@DisplayName("BaseErrorCode pinned values")
class BaseErrorCodeValuePinningTest {

    @Test
    @DisplayName("Generic code/message literals match the legacy NestJS contract")
    void pinnedValuesMatchLegacyContract() {
        assertThat(BaseErrorCode.SUCCESS.code()).isEqualTo(0);
        assertThat(BaseErrorCode.SUCCESS.message()).isEqualTo("success");

        assertThat(BaseErrorCode.BAD_REQUEST.code()).isEqualTo(40000);
        assertThat(BaseErrorCode.BAD_REQUEST.message()).isEqualTo("Bad request");

        assertThat(BaseErrorCode.VALIDATION_FAILED.code()).isEqualTo(49999);
        assertThat(BaseErrorCode.VALIDATION_FAILED.message()).isEqualTo("Validation failed");

        assertThat(BaseErrorCode.UNAUTHORIZED.code()).isEqualTo(40100);
        assertThat(BaseErrorCode.UNAUTHORIZED.message()).isEqualTo("Unauthorized");

        assertThat(BaseErrorCode.FORBIDDEN.code()).isEqualTo(40300);
        assertThat(BaseErrorCode.FORBIDDEN.message()).isEqualTo("Forbidden");

        assertThat(BaseErrorCode.NOT_FOUND.code()).isEqualTo(40400);
        assertThat(BaseErrorCode.NOT_FOUND.message()).isEqualTo("Not found");

        assertThat(BaseErrorCode.METHOD_NOT_ALLOWED.code()).isEqualTo(40500);
        assertThat(BaseErrorCode.METHOD_NOT_ALLOWED.message()).isEqualTo("Method not allowed");

        assertThat(BaseErrorCode.CONFLICT.code()).isEqualTo(40900);
        assertThat(BaseErrorCode.CONFLICT.message()).isEqualTo("Conflict");

        assertThat(BaseErrorCode.TOO_MANY_REQUESTS.code()).isEqualTo(42900);
        assertThat(BaseErrorCode.TOO_MANY_REQUESTS.message()).isEqualTo("Too many requests");

        assertThat(BaseErrorCode.UNKNOWN_ERROR.code()).isEqualTo(50000);
        assertThat(BaseErrorCode.UNKNOWN_ERROR.message()).isEqualTo("Unknown error");

        assertThat(BaseErrorCode.DATABASE_ERROR.code()).isEqualTo(50001);
        assertThat(BaseErrorCode.DATABASE_ERROR.message()).isEqualTo("Database error");
    }
}
