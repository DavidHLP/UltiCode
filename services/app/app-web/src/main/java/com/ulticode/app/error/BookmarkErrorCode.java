package com.ulticode.app.error;

import com.ulticode.common.error.NamespacedErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Bookmark-domain error codes preserved from the legacy HTTP contract.
 *
 * <p>Each relocated module family owns its error-code enum so the namespace
 * and code range stay coherent. Generic protocol-level codes (NOT_FOUND,
 * FORBIDDEN, BAD_REQUEST) are reused via
 * {@link com.ulticode.common.error.BaseErrorCode}; only module-specific
 * failure codes live here.
 *
 * <p>Bookmark module codes (8xxxx) migrated from the legacy
 * {@code com.ulticode.common.exception.ErrorCode} enum as part of
 * P7-APP-BOOKMARK-001.
 */
@Getter
public enum BookmarkErrorCode implements NamespacedErrorCode {

    BOOKMARK_FOLDER_NOT_FOUND(80001, "Bookmark folder not found", HttpStatus.NOT_FOUND),
    BOOKMARK_CANNOT_DELETE_DEFAULT(80002, "Cannot delete default folder", HttpStatus.BAD_REQUEST),
    BOOKMARK_FOLDER_NAME_EXISTS(80003, "Folder name already exists", HttpStatus.CONFLICT);

    public static final String NAMESPACE = "bookmark";

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    BookmarkErrorCode(Integer code, String message, HttpStatus httpStatus) {
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
