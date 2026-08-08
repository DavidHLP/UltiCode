package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import org.springframework.http.HttpStatus;

public enum EmailErrorCode implements NamespacedErrorCode {
    EMAIL_TEMPLATE_NOT_FOUND(190001, "Email template not found", "email", HttpStatus.NOT_FOUND),
    EMAIL_SEND_FAILED(190002, "Failed to send email", "email", HttpStatus.INTERNAL_SERVER_ERROR),
    EMAIL_INVALID_RECIPIENT(190003, "Invalid email recipient", "email", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final String namespace;
    private final HttpStatus httpStatus;

    EmailErrorCode(int code, String message, String namespace, HttpStatus httpStatus) {
        this.code = code; this.message = message; this.namespace = namespace; this.httpStatus = httpStatus;
    }
    @Override public int code() { return code; }
    @Override public String message() { return message; }
    @Override public String namespace() { return namespace; }
    public HttpStatus httpStatus() { return httpStatus; }
}
