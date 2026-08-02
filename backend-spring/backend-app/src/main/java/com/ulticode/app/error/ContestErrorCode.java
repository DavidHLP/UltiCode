package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Contest-domain error codes preserved from the legacy HTTP contract.
 *
 * <p>Mirrors {@link ForumErrorCode}/{@link SolutionErrorCode}: each relocated
 * module family carries its legacy integer codes forward so existing frontend
 * and API consumers see no numeric change. Generic protocol-level codes
 * (BAD_REQUEST, NOT_FOUND, FORBIDDEN, UNAUTHORIZED) are reused via
 * {@link com.ulticode.common.error.BaseErrorCode}.
 *
 * <p>P7-RELOCATE-CONTEST-001: extracted from legacy
 * {@code com.ulticode.common.exception.ErrorCode} when the contest family
 * relocated to backend-app.
 */
public enum ContestErrorCode implements NamespacedErrorCode {

    // Contest-owned codes (7xxxx range)
    CONTEST_NOT_FOUND(70001, "Contest not found", "contest", HttpStatus.NOT_FOUND),
    CONTEST_ONLY_REGISTER_UPCOMING(70002, "Can only register for upcoming contests", "contest", HttpStatus.BAD_REQUEST),
    CONTEST_ALREADY_REGISTERED(70003, "Already registered for this contest", "contest", HttpStatus.CONFLICT),
    CONTEST_NOT_REGISTERED(70004, "Not registered for this contest", "contest", HttpStatus.BAD_REQUEST),
    CONTEST_REGISTRATION_CLOSED(70005, "Contest registration is closed", "contest", HttpStatus.BAD_REQUEST),
    CONTEST_NOT_STARTED(70008, "Contest has not started", "contest", HttpStatus.BAD_REQUEST),
    CONTEST_ENDED(70009, "Contest has ended", "contest", HttpStatus.BAD_REQUEST),
    SCORING_RULE_NOT_FOUND(70010, "Scoring rule not found", "contest", HttpStatus.NOT_FOUND),
    CONTEST_FULL(70013, "Contest is full", "contest", HttpStatus.BAD_REQUEST),
    CONTEST_SLUG_EXISTS(70015, "Contest slug already exists", "contest", HttpStatus.CONFLICT),

    // Problem-domain code referenced by contest (co-located in backend-app)
    PROBLEM_NOT_FOUND(40004, "Problem not found", "problem", HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final String namespace;
    private final HttpStatus httpStatus;

    ContestErrorCode(int code, String message, String namespace, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.namespace = namespace;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() { return code; }

    @Override
    public String message() { return message; }

    @Override
    public String namespace() { return namespace; }

    public HttpStatus httpStatus() { return httpStatus; }
}
