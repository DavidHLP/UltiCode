package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Follow-domain error codes preserved from the legacy HTTP contract.
 */
@Getter
public enum FollowErrorCode implements NamespacedErrorCode {

    CANNOT_FOLLOW_SELF(40001, "Cannot follow yourself", HttpStatus.BAD_REQUEST),
    ALREADY_FOLLOWING(40002, "Already following this user", HttpStatus.CONFLICT),
    NOT_FOLLOWING(40003, "Not following this user", HttpStatus.BAD_REQUEST);

    public static final String NAMESPACE = "follow";

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    FollowErrorCode(Integer code, String message, HttpStatus httpStatus) {
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
