package com.ulticode.auth.error;

import com.ulticode.common.error.NamespacedErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Authentication-domain error codes owned by the auth service.
 *
 * <p>This enum is the {@code backend-auth} private copy of the AUTH
 * sub-range (1xxxx) from backend-legacy's
 * {@code com.ulticode.common.exception.ErrorCode}. The auth service does
 * not depend on backend-legacy, so the AUTH codes it throws must live
 * here.
 *
 * <p>Numeric and message values are byte-identical to the legacy enum.
 * Cross-service compatibility is the responsibility of the HTTP envelope
 * (preserved verbatim) and the {@code RpcResult} wire projection (the
 * module's {@code AuthErrorCode} values map onto
 * {@code com.ulticode.common.error.NamespacedErrorCode}).
 *
 * <p>Implements {@link NamespacedErrorCode} so {@link AuthBusinessException}
 * can accept any {@code NamespacedErrorCode} (including
 * {@link com.ulticode.common.error.BaseErrorCode} and module-local enums
 * from other future services). The {@code namespace()} returns
 * {@code "auth"} so the {@code RpcResult} wire projection can route
 * cross-process errors back to this module.
 *
 * <p>The 11 generic protocol-level codes (UNAUTHORIZED, FORBIDDEN, etc.)
 * are <b>not</b> re-declared here; callers should reference
 * {@link com.ulticode.common.error.BaseErrorCode} for those, which
 * delegates to the shared source-of-truth.
 *
 * <p>Format: {@code AUTH_xxxxx} where {@code xxxxx} is the unique
 * sub-id within the AUTH module.
 */
@Getter
public enum AuthErrorCode implements NamespacedErrorCode {
    AUTH_INVALID_CREDENTIALS(10001, "Invalid credentials", HttpStatus.UNAUTHORIZED),
    AUTH_NO_PASSWORD(10002, "No password provided", HttpStatus.UNAUTHORIZED),
    AUTH_USERNAME_TAKEN(10003, "Username already taken", HttpStatus.CONFLICT),
    AUTH_EMAIL_TAKEN(10004, "Email already taken", HttpStatus.CONFLICT),
    AUTH_USER_NOT_FOUND(10005, "User not found", HttpStatus.NOT_FOUND),
    AUTH_TOKEN_EXPIRED(10006, "Token expired", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_RESET_TOKEN(10007, "Invalid reset token", HttpStatus.BAD_REQUEST),
    AUTH_RESET_TOKEN_ALREADY_USED(10008, "Reset token already used", HttpStatus.BAD_REQUEST),
    AUTH_RESET_TOKEN_EXPIRED(10009, "Reset token expired", HttpStatus.BAD_REQUEST),
    AUTH_INVALID_REQUEST(10010, "Invalid request", HttpStatus.BAD_REQUEST);

    public static final String NAMESPACE = "auth";

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    AuthErrorCode(Integer code, String message, HttpStatus httpStatus) {
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

    /**
     * Look up an AuthErrorCode by integer code.
     *
     * @return the matching AuthErrorCode, or {@code null} if not found.
     *         Callers are expected to handle the null case (typically
     *         by falling back to a generic 500 or logging the unknown code).
     */
    public static AuthErrorCode fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AuthErrorCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
