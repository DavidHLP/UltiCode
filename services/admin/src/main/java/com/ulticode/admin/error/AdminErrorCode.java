package com.ulticode.admin.error;

import com.ulticode.common.error.NamespacedErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Admin-domain error codes preserved from the legacy HTTP contract. */
@Getter
public enum AdminErrorCode implements NamespacedErrorCode {
    // Generic HTTP error codes — mirrored from legacy ErrorCode contract
    BAD_REQUEST(40000, "Bad request", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "Unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "Forbidden", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "Not found", HttpStatus.NOT_FOUND),
    CONFLICT(40900, "Conflict", HttpStatus.CONFLICT),
    VALIDATION_FAILED(49999, "Validation failed", HttpStatus.BAD_REQUEST),
    UNKNOWN_ERROR(50000, "Unknown error", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR(50001, "Database error", HttpStatus.INTERNAL_SERVER_ERROR),

    // Auth module (1xxxx)
    AUTH_INVALID_CREDENTIALS(10001, "Invalid credentials", HttpStatus.UNAUTHORIZED),
    AUTH_USER_NOT_FOUND(10005, "User not found", HttpStatus.NOT_FOUND),
    AUTH_TOKEN_EXPIRED(10006, "Token expired", HttpStatus.UNAUTHORIZED),

    // User module (2xxxx)
    USER_NOT_FOUND(20001, "User not found", HttpStatus.NOT_FOUND),
    USER_CANNOT_EDIT_OTHERS(20002, "Cannot edit other users", HttpStatus.FORBIDDEN),
    USER_BANNED(20003, "You are banned from posting content", HttpStatus.FORBIDDEN),
    USER_PASSWORD_INCORRECT(20004, "Current password is incorrect", HttpStatus.BAD_REQUEST),

    // Problem module (3xxxx)
    PROBLEM_NOT_FOUND(30001, "Problem not found", HttpStatus.NOT_FOUND),
    PROBLEM_LOCKED(30002, "Problem is locked", HttpStatus.FORBIDDEN),
    PROBLEM_TAG_NOT_FOUND(30010, "Problem tag not found", HttpStatus.NOT_FOUND),
    PROBLEM_TAG_NAME_EXISTS(30011, "Problem tag name already exists", HttpStatus.CONFLICT),
    PROBLEM_TAG_SLUG_EXISTS(30012, "Problem tag slug already exists", HttpStatus.CONFLICT),
    TEST_CASE_NOT_FOUND(30020, "Test case not found", HttpStatus.NOT_FOUND),
    TEST_CASE_INVALID_SCOPE(30021, "Test case scope must be exactly one of SAMPLE or HIDDEN", HttpStatus.BAD_REQUEST),

    // Submission module (4xxxx)
    SUBMISSION_NOT_FOUND(40001, "Submission not found", HttpStatus.NOT_FOUND),

    // Solution module (5xxxx)
    SOLUTION_NOT_FOUND(50401, "Solution not found", HttpStatus.NOT_FOUND),

    // Forum module (6xxxx)
    FORUM_TAG_NOT_FOUND(60010, "Forum tag not found", HttpStatus.NOT_FOUND),
    FORUM_TAG_NAME_EXISTS(60011, "Forum tag name already exists", HttpStatus.CONFLICT),
    FORUM_TAG_SLUG_EXISTS(60012, "Forum tag slug already exists", HttpStatus.CONFLICT),

    // Contest module (7xxxx)
    CONTEST_NOT_FOUND(70001, "Contest not found", HttpStatus.NOT_FOUND),
    CONTEST_ONLY_REGISTER_UPCOMING(70002, "Can only register for upcoming contests", HttpStatus.BAD_REQUEST),
    CONTEST_ONLY_UPDATE_UPCOMING(70006, "Contest can only be updated when in UPCOMING status", HttpStatus.BAD_REQUEST),
    CONTEST_SLUG_EXISTS(70015, "Contest slug already exists", HttpStatus.CONFLICT),

    // Problem list module (9xxxx)
    PROBLEM_LIST_NOT_FOUND(90001, "Problem list not found", HttpStatus.NOT_FOUND),
    PROBLEM_LIST_PROBLEM_DUPLICATE(90004, "This problem is already in the list", HttpStatus.CONFLICT),
    // Settings module (20xxxx)
    SETTING_INVALID_VALUE(200002, "Invalid setting value", HttpStatus.BAD_REQUEST),
    SETTING_PERSISTENCE_FAILED(200003, "Failed to persist setting", HttpStatus.INTERNAL_SERVER_ERROR),
    /** P7-ADMIN-BACKUP-IDENTITY-001: transport / result / payload / row-level failure from IdentityQueryService. */
    IDENTITY_QUERY_FAILED(200004, "Identity query failed", HttpStatus.INTERNAL_SERVER_ERROR),
    /**
     * Cross-owner read seam: the owning service's query RPC failed or was
     * unreachable. Surfaced as 503 so infrastructure failure is never
     * disguised as business-empty data or NOT_FOUND.
     */
    OWNER_QUERY_UNAVAILABLE(200005, "Owner service query unavailable", HttpStatus.SERVICE_UNAVAILABLE);

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
