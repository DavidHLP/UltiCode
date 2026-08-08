package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Forum-domain error codes preserved from the legacy HTTP contract.
 *
 * <p>Mirrors {@link SolutionErrorCode}: each relocated module family
 * carries its legacy integer codes forward so existing frontend and API
 * consumers see no numeric change. Generic protocol-level codes
 * (UNAUTHORIZED, NOT_FOUND) are reused via {@link com.ulticode.common.error.BaseErrorCode};
 * only module-specific failure codes live here.
 *
 * <p>P7-RELOCATE-FORUM-001: extracted from legacy
 * {@code com.ulticode.common.exception.ErrorCode} when the forum family
 * relocated to backend-app.
 */
public enum ForumErrorCode implements NamespacedErrorCode {

    // Forum-owned codes (6xxxx range)
    FORUM_POST_NOT_FOUND(60001, "Post not found",
            "forum", HttpStatus.NOT_FOUND),
    FORUM_COMMUNITY_NOT_FOUND(60002, "Community not found",
            "forum", HttpStatus.NOT_FOUND),
    FORUM_COMMUNITY_RESTRICTED(60003, "Community is restricted",
            "forum", HttpStatus.FORBIDDEN),
    FORUM_CANNOT_EDIT_POST(60004, "Cannot edit this post",
            "forum", HttpStatus.FORBIDDEN),
    FORUM_CANNOT_DELETE_POST(60005, "Cannot delete this post",
            "forum", HttpStatus.FORBIDDEN),
    FORUM_COMMENT_NOT_FOUND(60006, "Comment not found",
            "forum", HttpStatus.NOT_FOUND),
    FORUM_POST_LOCKED(60007, "Post is locked",
            "forum", HttpStatus.FORBIDDEN),
    FORUM_INVALID_SORT(60013, "Unknown sortBy value",
            "forum", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final String namespace;
    private final HttpStatus httpStatus;

    ForumErrorCode(int code, String message, String namespace, HttpStatus httpStatus) {
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
