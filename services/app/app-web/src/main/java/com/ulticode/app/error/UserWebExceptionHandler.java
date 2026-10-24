package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps user-module business failures to the shared HTTP result envelope. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ulticode.modules.user")
public class UserWebExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception) {
        return BusinessExceptionResponse.create(exception, httpStatus(exception.getErrorCode()));
    }

    private HttpStatus httpStatus(NamespacedErrorCode errorCode) {
        if (errorCode instanceof UserErrorCode userErrorCode) {
            return userErrorCode.getHttpStatus();
        }
        return BusinessExceptionResponse.baseHttpStatus(errorCode);
    }
}
