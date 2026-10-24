package com.ulticode.app.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class BusinessExceptionResponse {

    private BusinessExceptionResponse() {
    }

    static ResponseEntity<Result<Void>> create(BusinessException exception, HttpStatus status) {
        NamespacedErrorCode errorCode = exception.getErrorCode();
        Integer code = errorCode == null ? BaseErrorCode.UNKNOWN_ERROR.code() : errorCode.code();
        String message = exception.getMessage() != null
                ? exception.getMessage()
                : errorCode != null ? errorCode.message() : BaseErrorCode.UNKNOWN_ERROR.message();
        return ResponseEntity.status(status).body(Result.error(code, message, exception.getTraceId()));
    }

    static HttpStatus baseHttpStatus(NamespacedErrorCode errorCode) {
        if (errorCode instanceof BaseErrorCode baseErrorCode) {
            return switch (baseErrorCode) {
                case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CONFLICT -> HttpStatus.CONFLICT;
                case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
                case BAD_REQUEST, VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
                case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
