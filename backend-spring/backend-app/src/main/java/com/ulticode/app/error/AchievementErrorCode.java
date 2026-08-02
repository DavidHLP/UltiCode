package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import org.springframework.http.HttpStatus;

public enum AchievementErrorCode implements NamespacedErrorCode {
    ACHIEVEMENT_NOT_FOUND(170001, "Achievement not found", "achievement", HttpStatus.NOT_FOUND),
    ACHIEVEMENT_ALREADY_EARNED(170002, "Achievement already earned", "achievement", HttpStatus.CONFLICT),
    ACHIEVEMENT_INVALID_TYPE(170003, "Invalid achievement type", "achievement", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final String namespace;
    private final HttpStatus httpStatus;

    AchievementErrorCode(int code, String message, String namespace, HttpStatus httpStatus) {
        this.code = code; this.message = message; this.namespace = namespace; this.httpStatus = httpStatus;
    }
    @Override public int code() { return code; }
    @Override public String message() { return message; }
    @Override public String namespace() { return namespace; }
    public HttpStatus httpStatus() { return httpStatus; }
}
