package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import org.springframework.http.HttpStatus;

public enum ModerationErrorCode implements NamespacedErrorCode {
    QUEUE_NOT_FOUND(100001, "Moderation queue item not found", "moderation", HttpStatus.NOT_FOUND),
    ALREADY_ASSIGNED(100002, "Item is already assigned to another moderator", "moderation", HttpStatus.CONFLICT),
    ALREADY_REPORTED(100003, "You have already reported this content", "moderation", HttpStatus.CONFLICT),
    APPEAL_NOT_FOUND(100004, "Appeal not found", "moderation", HttpStatus.NOT_FOUND),
    APPEAL_ALREADY_REVIEWED(100005, "This appeal has already been reviewed", "moderation", HttpStatus.BAD_REQUEST),
    CANNOT_APPEAL(100006, "This item cannot be appealed", "moderation", HttpStatus.BAD_REQUEST),
    NOT_AUTHOR(100007, "Only the content author can appeal", "moderation", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final String namespace;
    private final HttpStatus httpStatus;

    ModerationErrorCode(int code, String message, String namespace, HttpStatus httpStatus) {
        this.code = code; this.message = message; this.namespace = namespace; this.httpStatus = httpStatus;
    }
    @Override public int code() { return code; }
    @Override public String message() { return message; }
    @Override public String namespace() { return namespace; }
    public HttpStatus httpStatus() { return httpStatus; }
}
