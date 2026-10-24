package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps backend-app solution module failures to the shared HTTP result envelope.
 *
 * <p>Mirrors {@code AdminWebExceptionHandler}: bounded to solution controller
 * packages via {@link RestControllerAdvice#basePackages()}, explicit
 * {@link NamespacedErrorCode}→HTTP mapping via {@link SolutionErrorCode#httpStatus()},
 * and only handlers needed by solution controllers.
 *
 * <p>P7-RELOCATE-SOLUTION-001: required when the solution family relocated
 * from backend-legacy to backend-app. Legacy's {@code GlobalExceptionHandler}
 * covers the entire monolith; this handler covers only the solution surface.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ulticode.modules.solution")
public class SolutionWebExceptionHandler extends ContentWebExceptionHandler {

    public SolutionWebExceptionHandler() {
        super(log);
    }

    @Override
    protected HttpStatus httpStatus(NamespacedErrorCode errorCode) {
        if (errorCode instanceof SolutionErrorCode solutionErrorCode) {
            return solutionErrorCode.httpStatus();
        }
        return super.httpStatus(errorCode);
    }
}
