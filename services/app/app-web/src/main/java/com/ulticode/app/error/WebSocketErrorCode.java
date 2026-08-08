package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import org.springframework.http.HttpStatus;

public enum WebSocketErrorCode implements NamespacedErrorCode {
    UNAUTHORIZED(150001, "WebSocket unauthorized", "websocket", HttpStatus.UNAUTHORIZED),
    INVALID_CONTEST_ID(150002, "Invalid contest ID", "websocket", HttpStatus.BAD_REQUEST),
    INVALID_TOKEN(150003, "Invalid WebSocket token", "websocket", HttpStatus.UNAUTHORIZED),
    TOKEN_BLACKLISTED(150004, "Token has been blacklisted", "websocket", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(150005, "User not found", "websocket", HttpStatus.NOT_FOUND),
    USER_BANNED(150006, "Account is banned or inactive", "websocket", HttpStatus.FORBIDDEN),
    SESSION_MISSING(150007, "WebSocket session is not authenticated", "websocket", HttpStatus.UNAUTHORIZED);

    private final int code;
    private final String message;
    private final String namespace;
    private final HttpStatus httpStatus;

    WebSocketErrorCode(int code, String message, String namespace, HttpStatus httpStatus) {
        this.code = code; this.message = message; this.namespace = namespace; this.httpStatus = httpStatus;
    }
    @Override public int code() { return code; }
    @Override public String message() { return message; }
    @Override public String namespace() { return namespace; }
    public HttpStatus httpStatus() { return httpStatus; }
}
