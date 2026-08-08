package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * User-domain error codes preserved from the legacy HTTP contract (2xxxx range).
 *
 * <p>Integer codes and messages remain stable for existing frontend
 * consumers while the user surface is hosted by backend-app
 * (P7-RELOCATE-USER-REMAINDER-001). Mirrors the {@link ProblemListErrorCode}
 * pattern.
 */
@Getter
public enum UserErrorCode implements NamespacedErrorCode {

    USER_NOT_FOUND(20001, "User not found", HttpStatus.NOT_FOUND);

    public static final String NAMESPACE = "user";

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    UserErrorCode(Integer code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
