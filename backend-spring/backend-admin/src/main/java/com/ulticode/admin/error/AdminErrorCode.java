package com.ulticode.admin.error;

import com.ulticode.common.error.NamespacedErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Admin-domain error codes preserved from the legacy HTTP contract. */
@Getter
public enum AdminErrorCode implements NamespacedErrorCode {
    SETTING_INVALID_VALUE(200002, "Invalid setting value", HttpStatus.BAD_REQUEST),
    SETTING_PERSISTENCE_FAILED(200003, "Failed to persist setting", HttpStatus.INTERNAL_SERVER_ERROR),
    /** P7-ADMIN-BACKUP-IDENTITY-001: transport / result / payload / row-level failure from IdentityQueryService. */
    IDENTITY_QUERY_FAILED(200004, "Identity query failed", HttpStatus.INTERNAL_SERVER_ERROR);

    public static final String NAMESPACE = "admin";

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    AdminErrorCode(Integer code, String message, HttpStatus httpStatus) {
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
