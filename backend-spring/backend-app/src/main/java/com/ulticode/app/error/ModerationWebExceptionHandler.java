package com.ulticode.app.error;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ModerationWebExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex.getErrorCode() instanceof ModerationErrorCode mec) {
            status = mec.httpStatus();
        } else {
            int code = ex.getErrorCode().code();
            if (code == 40100 || code == 40300) status = HttpStatus.FORBIDDEN;
            else if (code == 40400) status = HttpStatus.NOT_FOUND;
            else if (code == 40000) status = HttpStatus.BAD_REQUEST;
            else if (code == 40900) status = HttpStatus.CONFLICT;
        }
        return ResponseEntity.status(status)
                .body(Result.error(ex.getErrorCode().code(), ex.getErrorCode().message()));
    }
}
