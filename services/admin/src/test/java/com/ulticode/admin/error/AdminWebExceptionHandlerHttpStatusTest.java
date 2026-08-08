package com.ulticode.admin.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link AdminWebExceptionHandler#handleBusinessException} path,
 * which delegates to the private {@code httpStatus()} method.
 * Covers the AdminErrorCode, BaseErrorCode, and reflective legacy paths.
 */
class AdminWebExceptionHandlerHttpStatusTest {

    private final AdminWebExceptionHandler handler = new AdminWebExceptionHandler();

    @Test
    void adminErrorCode_mapsToConfiguredHttpStatus() {
        BusinessException ex = new BusinessException(
                AdminErrorCode.SETTING_INVALID_VALUE, "bad value");

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void baseErrorCode_badRequest_mapsTo400() {
        BusinessException ex = new BusinessException(BaseErrorCode.BAD_REQUEST);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void baseErrorCode_notFound_mapsTo404() {
        BusinessException ex = new BusinessException(BaseErrorCode.NOT_FOUND);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void baseErrorCode_forbidden_mapsTo403() {
        BusinessException ex = new BusinessException(BaseErrorCode.FORBIDDEN);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void baseErrorCode_unknownError_mapsTo500() {
        BusinessException ex = new BusinessException(BaseErrorCode.UNKNOWN_ERROR);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Tests the reflective code path: a NamespacedErrorCode that is neither
     * AdminErrorCode nor BaseErrorCode, but exposes a getHttpStatus() method
     * (mimicking legacy ErrorCode). The handler should discover it via reflection.
     */
    @Test
    void legacyLikeErrorCode_reflectivePath_mapsToReturnedStatus() {
        // Create an anonymous NamespacedErrorCode with a getHttpStatus() method
        NamespacedErrorCode legacyLike = new LegacyLikeErrorCode();
        BusinessException ex = new BusinessException(legacyLike, "legacy failure");

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unknownNamespacedErrorCode_fallsBackTo500() {
        // A plain NamespacedErrorCode without getHttpStatus() and not BaseErrorCode
        NamespacedErrorCode plain = new NamespacedErrorCode() {
            @Override public String namespace() { return "plain"; }
            @Override public int code() { return 99999; }
            @Override public String message() { return "plain error"; }
        };
        BusinessException ex = new BusinessException(plain);

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void nullErrorCode_fallsBackTo500() {
        BusinessException ex = new BusinessException(null, "missing error code");

        ResponseEntity<Result<Void>> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * A fake "legacy-like" error code that implements NamespacedErrorCode and
     * exposes a getHttpStatus() method, simulating the legacy ErrorCode contract
     * that the reflective path in httpStatus() is designed to handle.
     */
    @SuppressWarnings("unused") // getHttpStatus() is discovered via reflection
    private static class LegacyLikeErrorCode implements NamespacedErrorCode {
        @Override public String namespace() { return "legacy"; }
        @Override public int code() { return 10001; }
        @Override public String message() { return "legacy-like error"; }

        public HttpStatus getHttpStatus() {
            return HttpStatus.CONFLICT;
        }
    }
}
