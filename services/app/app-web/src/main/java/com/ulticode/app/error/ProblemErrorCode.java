package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Problem-domain error codes preserved from the legacy HTTP contract.
 *
 * <p>The integer codes and messages remain stable for existing frontend
 * consumers while the Problem family is hosted by backend-app.
 */
public enum ProblemErrorCode implements NamespacedErrorCode {

    PROBLEM_NOT_FOUND(30001, "Problem not found", HttpStatus.NOT_FOUND),
    PROBLEM_LOCKED(30002, "Problem is locked", HttpStatus.FORBIDDEN),
    PROBLEM_PREMIUM_REQUIRED(30003, "Premium subscription required", HttpStatus.FORBIDDEN),
    PROBLEM_VERSION_ALREADY_EXISTS(30004, "Problem version already exists", HttpStatus.CONFLICT),
    PROBLEM_TAG_NOT_FOUND(30010, "Problem tag not found", HttpStatus.NOT_FOUND),
    PROBLEM_TAG_NAME_EXISTS(30011, "Problem tag name already exists", HttpStatus.CONFLICT),
    PROBLEM_TAG_SLUG_EXISTS(30012, "Problem tag slug already exists", HttpStatus.CONFLICT),
    NOTE_NOT_FOUND(30013, "Note not found", HttpStatus.NOT_FOUND),
    NOTE_FORBIDDEN(30014, "Cannot access another user's note", HttpStatus.FORBIDDEN),
    TEST_CASE_NOT_FOUND(30020, "Test case not found", HttpStatus.NOT_FOUND),
    TEST_CASE_INVALID_SCOPE(30021, "Test case scope must be exactly one of SAMPLE or HIDDEN", HttpStatus.BAD_REQUEST),
    CODE_EXECUTION_UNAVAILABLE(30022, "Code execution is unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    CODE_EXECUTION_INVALID_REQUEST(30023, "Invalid code execution request", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ProblemErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String namespace() {
        return "problem";
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
