package com.ulticode.app.error;

import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.error.NamespacedErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Solution-domain error codes preserved from the legacy HTTP contract.
 *
 * <p>Mirrors {@link AppErrorCode}: each relocated module family carries
 * its legacy integer codes forward so existing frontend and API consumers
 * see no numeric change. Generic protocol-level codes (UNAUTHORIZED) are
 * reused via {@link BaseErrorCode}; only module-specific failure codes
 * live here.
 *
 * <p>P7-RELOCATE-SOLUTION-001: extracted from legacy
 * {@code com.ulticode.common.exception.ErrorCode} when the solution
 * family relocated to backend-app. ErrorCode could not be promoted to
 * backend-common because it imports Spring {@code HttpStatus}.
 */
public enum SolutionErrorCode implements NamespacedErrorCode {


    // Cross-module references (preserved from legacy codes)
    USER_CANNOT_EDIT_OTHERS(20002, "Cannot edit other users",
            "user", HttpStatus.FORBIDDEN),
    USER_BANNED(20003, "You are banned from posting content",
            "user", HttpStatus.FORBIDDEN),
    PROBLEM_NOT_FOUND(30001, "Problem not found",
            "problem", HttpStatus.NOT_FOUND),

    // Solution-owned codes (5xxxx range)
    SOLUTION_NOT_FOUND(50401, "Solution not found",
            "solution", HttpStatus.NOT_FOUND),
    SOLUTION_CANNOT_DELETE_OTHERS(50002, "Cannot delete others' solution",
            "solution", HttpStatus.FORBIDDEN),
    SOLUTION_CANNOT_UPDATE_OTHERS(50003, "Cannot update others' solution",
            "solution", HttpStatus.FORBIDDEN),
    SOLUTION_COMMENT_NOT_FOUND(50004, "Solution comment not found",
            "solution", HttpStatus.NOT_FOUND),
    SOLUTION_ALREADY_EXISTS(50008, "Solution already exists",
            "solution", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final String namespace;
    private final HttpStatus httpStatus;

    SolutionErrorCode(int code, String message, String namespace, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.namespace = namespace;
        this.httpStatus = httpStatus;
    }

    @Override
    public String namespace() {
        return namespace;
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
