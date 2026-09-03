package com.ulticode.submission.api.error;

import com.ulticode.common.error.NamespacedErrorCode;

/** Stable error catalog for Submission-owned RPC failures. */
public enum SubmissionErrorCode implements NamespacedErrorCode {
    INVALID_USER_ID(40001, "Invalid user id"),
    UNEXPECTED_SUBMISSION_STATE(50001, "Unexpected submission state");

    public static final String NAMESPACE = "submission";

    private final int code;
    private final String message;

    SubmissionErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
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
