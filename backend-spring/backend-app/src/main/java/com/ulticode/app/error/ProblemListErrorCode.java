package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Problem-list-domain error codes preserved from the legacy HTTP
 * contract (9xxxx range).
 *
 * <p>Integer codes and messages remain stable for existing frontend
 * consumers while the problem-list family is hosted by backend-app
 * (P7-RELOCATE-PROBLEMLIST-001). Mirrors the {@link ProblemErrorCode}
 * pattern.
 */
public enum ProblemListErrorCode implements NamespacedErrorCode {

    PROBLEM_LIST_NOT_FOUND(90001, "Problem list not found", HttpStatus.NOT_FOUND),
    PROBLEM_LIST_CANNOT_EDIT(90002, "Cannot edit this problem list", HttpStatus.FORBIDDEN),
    PROBLEM_LIST_PRIVATE(90003, "Problem list is private", HttpStatus.FORBIDDEN),
    PROBLEM_LIST_PROBLEM_DUPLICATE(90004, "This problem is already in the list", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ProblemListErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String namespace() {
        return "problem-list";
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
