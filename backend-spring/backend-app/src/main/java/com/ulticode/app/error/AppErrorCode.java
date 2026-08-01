package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * App-domain error codes preserved from the legacy HTTP contract.
 *
 * <p>Mirrors {@code com.ulticode.admin.error.AdminErrorCode}: each relocated
 * module family carries its legacy integer codes forward so existing frontend
 * and API consumers see no numeric change. Generic protocol-level codes
 * (UNAUTHORIZED, FORBIDDEN, BAD_REQUEST) are reused via
 * {@link com.ulticode.common.error.BaseErrorCode}; only module-specific
 * failure codes live here.
 *
 * <p>Subscription module codes (18xxxx) migrated from the legacy
 * {@code com.ulticode.common.exception.ErrorCode} enum as part of
 * P7-APP-SUBSCRIPTION-001.
 */
@Getter
public enum AppErrorCode implements NamespacedErrorCode {

    // Subscription module (18xxxx)
    SUBSCRIPTION_NOT_FOUND(180001, "Subscription not found", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_ALREADY_ACTIVE(180002, "User already has an active subscription", HttpStatus.CONFLICT),
    SUBSCRIPTION_EXPIRED(180003, "Subscription has expired", HttpStatus.BAD_REQUEST),
    SUBSCRIPTION_CANCELLED(180004, "Subscription is cancelled", HttpStatus.BAD_REQUEST);

    public static final String NAMESPACE = "app";

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    AppErrorCode(Integer code, String message, HttpStatus httpStatus) {
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
