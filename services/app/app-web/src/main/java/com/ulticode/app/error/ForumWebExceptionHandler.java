package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps backend-app forum module failures to the shared HTTP result envelope.
 *
 * <p>Mirrors {@link SolutionWebExceptionHandler}: bounded to forum controller
 * packages via {@link RestControllerAdvice#basePackages()}, explicit
 * {@link NamespacedErrorCode}→HTTP mapping via {@link ForumErrorCode#httpStatus()},
 * and only handlers needed by forum controllers.
 *
 * <p>P7-RELOCATE-FORUM-001: required when the forum family relocated
 * from backend-legacy to backend-app.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ulticode.modules.forum")
public class ForumWebExceptionHandler extends ContentWebExceptionHandler {

    public ForumWebExceptionHandler() {
        super(log);
    }

    @Override
    protected HttpStatus httpStatus(NamespacedErrorCode errorCode) {
        if (errorCode instanceof ForumErrorCode forumErrorCode) {
            return forumErrorCode.httpStatus();
        }
        return super.httpStatus(errorCode);
    }
}
